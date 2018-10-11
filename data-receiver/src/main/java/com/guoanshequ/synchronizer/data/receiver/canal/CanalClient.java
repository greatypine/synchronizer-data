package com.guoanshequ.synchronizer.data.receiver.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.google.protobuf.InvalidProtocolBufferException;
import com.guoanshequ.synchronizer.data.model.CanalBean;
import com.guoanshequ.synchronizer.data.receiver.common.MessageQueue;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.guoanshequ.synchronizer.data.utils.MessageConverter.createColumnMap;

@Component
public class CanalClient implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(CanalClient.class);

    @Value("${canal.destination}")
    private String destination;

    @Value("${canal.server.zkServers}")
    private String zkServers;

    @Value("${canal.server.hosts}")
    private String hosts;

    @Value("${canal.server.username}")
    private String username;

    @Value("${canal.server.password}")
    private String password;

    private final MessageQueue<CanalBean> queue;

    private volatile boolean isRunning = true;

    @Autowired
    public CanalClient(MessageQueue<CanalBean> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        CanalConnector connector;
        List<SocketAddress> socketAddressList = new ArrayList<>();
        String[] hostArray = StringUtils.split(hosts, ",");
        for (String host : hostArray) {
            String[] hostAndPort = StringUtils.split(host, ":");
            socketAddressList.add(new InetSocketAddress(hostAndPort[0], Integer.parseInt(hostAndPort[1])));
        }

        connector = CanalConnectors.newClusterConnector(socketAddressList, destination, username, password);
//        connector = CanalConnectors.newClusterConnector(zkServers, destination, username, password);
        int batchSize = 5 * 1024;
        connector.connect();
        connector.subscribe();

        try {
            while (isRunning) {
                Message message = connector.getWithoutAck(batchSize); // 获取指定数量的数据
                long batchId = message.getId();
                int size = message.getEntries().size();
                if (batchId != -1 && size != 0) {
                    logger.debug("the data size of taken is {}. ", size);
                    putEntriesToQueue(message.getEntries());
                }
                connector.ack(batchId);
            }
        } catch (InterruptedException e) {
            logger.error("the canal client failed. cause: {}, message: {}", e.getCause(), e.getMessage());
            isRunning = false;
        } finally {
            connector.disconnect();
            logger.info("canal client thread quit.");
        }

    }

    private void putEntriesToQueue(List<CanalEntry.Entry> entryList) throws InterruptedException {

        for (CanalEntry.Entry entry : entryList) {
            if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN) {
                    CanalEntry.TransactionBegin begin;
                    try {
                        begin = CanalEntry.TransactionBegin.parseFrom(entry.getStoreValue());
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException("parse event has an error , data:" + entry.toString(), e);
                    }
                    // 打印事务头信息，执行的线程id
                    logger.debug("BEGIN ----> Thread id: {}", begin.getThreadId());
                } else {
                    CanalEntry.TransactionEnd end;
                    try {
                        end = CanalEntry.TransactionEnd.parseFrom(entry.getStoreValue());
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException("parse event has an error , data:" + entry.toString(), e);
                    }
                    // 打印事务提交信息，事务id
                    logger.debug("END ----> transaction id: {}", end.getTransactionId());
                }
            } else if (entry.getEntryType() == CanalEntry.EntryType.ROWDATA) {
                CanalEntry.RowChange rowChange;
                try {
                    rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                } catch (Exception e) {
                    throw new RuntimeException("parse event has an error , data:" + entry.toString(), e);
                }
                CanalEntry.EventType eventType = rowChange.getEventType();
                if (rowChange.getIsDdl()) {
                    logger.info("ddlSql ----> " + rowChange.getSql());

                    CanalBean canalBean = new CanalBean(entry.getHeader().getSchemaName(), entry.getHeader().getTableName(),
                            entry.getHeader().getExecuteTime(), eventType.getNumber(), rowChange.getSql());
                    logger.info("put ddl operation into queue. {}/{}", queue.size(), queue.getCapacity());
                    queue.put(canalBean);
                } else {
                    for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                        CanalBean canalBean = new CanalBean(entry.getHeader().getSchemaName(), entry.getHeader().getTableName(),
                                entry.getHeader().getExecuteTime(), eventType.getNumber(), null);

                        //由于此方法内部不能获取到mysql字段的一系列信息，所以用下面的方法进行了替换
                        // Map<String, CanalBean.RowData.ColumnEntry> beforeColumns = createColumnMap(rowData.getBeforeColumnsList());
                        // Map<String, CanalBean.RowData.ColumnEntry> afterColumns = createColumnMap(rowData.getAfterColumnsList());

                        //打印 & 封装
                        Map<String, CanalBean.RowData.ColumnEntry> beforeColumns = createColumnMap(rowData.getBeforeColumnsOrBuilderList());
                        Map<String, CanalBean.RowData.ColumnEntry> afterColumns = createColumnMap(rowData.getAfterColumnsOrBuilderList());

                        canalBean.setRowData(new CanalBean.RowData(beforeColumns, afterColumns));
                        logger.debug("put dml operation into queue. {}/{}", queue.size(), queue.getCapacity());
                        queue.put(canalBean);
                    }
                }
            }
        }
    }

    public void stop() {
        isRunning = false;
    }

}

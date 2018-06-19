package com.guoanshequ.synchronizer.data.sender.kafka;

import com.guoanshequ.synchronizer.data.model.CanalBean;
import com.guoanshequ.synchronizer.data.sender.kudu.KuduTableOperation;
import org.apache.commons.lang.StringUtils;
import org.apache.kudu.ColumnSchema;
import org.apache.kudu.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class WorkerThread implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(WorkerThread.class);

    private final KuduClient kuduClient;

    private final String tableName;

    private final LinkedBlockingQueue<CanalBean> queue;

    private boolean isRunning = true;

    private Date lastExecuteTime;

    private Long[] waitMillis;

    public WorkerThread(KuduClient kuduClient, String tableName, LinkedBlockingQueue<CanalBean> queue, int idleTimeout, long maxWaitTime) {
        this.kuduClient = kuduClient;
        this.tableName = tableName;
        this.queue = queue;
        List<Long> longList = new ArrayList<>();
        long sum = 0;
        long item = 1;
        long max = idleTimeout * 1000;

        while (true) {
            item *= 2;
            item = item > maxWaitTime ? maxWaitTime : item;
            sum += item;
            if (sum < max) {
                longList.add(item);
            } else {
                longList.add(max - sum + item);
                break;
            }
        }
        waitMillis = new Long[longList.size()];
        waitMillis = longList.toArray(waitMillis);
        logger.info("synchronization thread for table [{}] was started.", tableName);
    }

    @Override
    public void run() {
        KuduSession kuduSession = null;
        try {

            String impalaTable = StringUtils.join(new String[]{"impala::", tableName});
            String infoFormat = StringUtils.join(new String[]{impalaTable, " {} keys:[{}]", " queue size:{}"});

            KuduTable kuduTable = kuduClient.openTable(impalaTable);
            List<ColumnSchema> columnSchemas = kuduTable.getSchema().getColumns();
            List<String> keyColumns = new ArrayList<>();
            for (ColumnSchema columnSchema:columnSchemas) {
                if(columnSchema.isKey()) {
                    keyColumns.add(columnSchema.getName());
                }
            }

            kuduSession = kuduClient.newSession();
            int n = 0;
            int len = waitMillis.length;
            while (isRunning) {
                lastExecuteTime = new Date();
                CanalBean canalBean = queue.poll();
                if (canalBean == null) {
                    if (n == len) break;
                    Thread.sleep(waitMillis[n++]);
                } else {
                    n = 0;
                    Operation operation = KuduTableOperation.createOperation(kuduTable, canalBean);
                    if (operation != null) {
                        kuduSession.apply(operation);
                        Object[] infoValues = new String[3];
                        if (operation instanceof Insert) {
                            infoValues[0] = "INSERT";
                        } else if (operation instanceof Delete) {
                            infoValues[0] = "DELETE";
                        } else if (operation instanceof Update) {
                            infoValues[0] = "UPDATE";
                        }
                        List<String> keyValues = new ArrayList<>();
                        for (String key : keyColumns) {
                            keyValues.add(StringUtils.join(new String[]{key, "=", operation.getRow().getString(key)}));
                        }
                        infoValues[1] = StringUtils.join(keyValues.toArray(), ",");
                        infoValues[2] = StringUtils.join(new Object[]{queue.size(), queue.remainingCapacity()}, "/");
                        logger.info(infoFormat, infoValues);
                    }
                }
            }
            if (isRunning) {
                logger.info("synchronization thread for table [{}] idle time out.", tableName);
            } else {
                logger.info("synchronization thread for table [{}] was stopped.", tableName);
            }
        } catch (InterruptedException e) {
            isRunning = false;
            logger.info(lastExecuteTime.toString());
            logger.error("thread InterruptedException exception. cause: {}, message: {}", e.getCause(), e.getMessage());
        } catch (KuduException e) {
            isRunning = false;
            logger.error("kudu operation exception. cause: {}, message: {}", e.getCause(), e.getMessage());
        } finally {
            try {
                if (kuduSession != null) {
                    kuduSession.close();
                }
            } catch (KuduException e) {
                logger.error("failed to close kudu session. cause: {}, message: {}", e.getCause(), e.getMessage());
            }
        }
    }

    public void stop() {
        isRunning = false;
    }

    public Date getLatestRunTime() {
        return lastExecuteTime;
    }
}

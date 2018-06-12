package com.guoanshequ.synchronizer.data.sender.kafka;

import com.guoanshequ.synchronizer.data.model.CanalBean;
import com.guoanshequ.synchronizer.data.sender.kudu.KuduTableOperation;
import org.apache.commons.lang.StringUtils;
import org.apache.kudu.ColumnSchema;
import org.apache.kudu.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class WorkerThread implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(WorkerThread.class);

    private final KuduClient kuduClient;

    private final String tableName;

    private final LinkedBlockingQueue<CanalBean> queue;

    private boolean isRunning = true;

    public WorkerThread(KuduClient kuduClient, String tableName, LinkedBlockingQueue<CanalBean> queue) {
        this.kuduClient = kuduClient;
        this.tableName = tableName;
        this.queue = queue;
    }

    @Override
    public void run() {
        KuduSession kuduSession = null;
        try {

            String impalaTable = StringUtils.join(new String[]{"impala::", tableName});
            String infoFormat = StringUtils.join(new String[]{impalaTable, " {} keys:[{}]"});

            KuduTable kuduTable = kuduClient.openTable(impalaTable);
            List<ColumnSchema> columnSchemas = kuduTable.getSchema().getColumns();
            List<String> keyColumns = new ArrayList<>();
            for (ColumnSchema columnSchema:columnSchemas) {
                if(columnSchema.isKey()) {
                    keyColumns.add(columnSchema.getName());
                }
            }

            kuduSession = kuduClient.newSession();

            while (isRunning) {
                CanalBean canalBean = queue.take();
                Operation operation = KuduTableOperation.createOperation(kuduTable, canalBean);
                if (operation != null) {
                    kuduSession.apply(operation);
                    Object[] infoValues = new String[2];
                    if (operation instanceof Insert) {
                        infoValues[0] = "INSERT";
                    } else if (operation instanceof Delete) {
                        infoValues[0] = "DELETE";
                    } else if (operation instanceof Update) {
                        infoValues[0] = "UPDATE";
                    }
                    List<String> keyValues = new ArrayList<>();
                    for(String key: keyColumns) {
                        keyValues.add(StringUtils.join(new String[]{key, "=", operation.getRow().getString(key)}));
                    }
                    infoValues[1] = StringUtils.join(keyValues.toArray(), ",");
                    logger.info(infoFormat, infoValues);
                }
            }
        } catch (InterruptedException e) {
            isRunning = false;
            logger.error("queue take exception. cause: {}, message: {}", e.getCause(), e.getMessage());
        } catch (KuduException e) {
            isRunning = false;
            logger.error("kudu operation exception. cause: {}, message: {}", e.getCause(), e.getMessage());
        } finally {
            try {
                kuduSession.close();
            } catch (KuduException e) {
                logger.error("failed to close kudu session. cause: {}, message: {}", e.getCause(), e.getMessage());
            }
            try {
                kuduClient.close();
            } catch (KuduException e1) {
                logger.error("failed to close kudu client. cause: {}, message: {}", e1.getCause(), e1.getMessage());
            }
        }
    }

    public void stop() {
        isRunning = false;
    }
}

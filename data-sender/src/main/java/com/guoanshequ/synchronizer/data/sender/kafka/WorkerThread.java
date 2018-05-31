package com.guoanshequ.synchronizer.data.sender.kafka;

import com.guoanshequ.synchronizer.data.model.CanalBean;
import com.guoanshequ.synchronizer.data.sender.kudu.KuduTableOperation;
import org.apache.kudu.client.KuduClient;
import org.apache.kudu.client.KuduException;
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
        try {
            while (isRunning) {
                int countPer = queue.size();
                List<CanalBean> canalBeanList = null;
                if (countPer > 0) {
                    canalBeanList = new ArrayList<>(countPer);
                    while (countPer-- != 0) {
                        canalBeanList.add(queue.take());
                    }
                }
                if(canalBeanList != null) {
                    KuduTableOperation.syncTable(tableName, kuduClient, canalBeanList);
                }
            }
        } catch (InterruptedException e) {
            isRunning = false;
            logger.error("worker thread failed. cause: {}, message: {}", e.getCause(), e.getMessage());
            if (kuduClient != null) {
                try {
                    kuduClient.close();
                } catch (KuduException e1) {
                    logger.error("failed to close kudu client. cause: {}, message: {}", e.getCause(), e.getMessage());
                }
            }
        }
    }

    public void stop() {
        isRunning = false;
    }
}

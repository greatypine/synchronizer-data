package com.guoanshequ.synchronizer.data.sender;


import com.guoanshequ.synchronizer.data.model.CanalBean;
import com.guoanshequ.synchronizer.data.sender.config.KuduConfig;
import com.guoanshequ.synchronizer.data.sender.kafka.WorkerThread;
import org.apache.kudu.client.KuduClient;
import org.apache.kudu.client.KuduException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@SpringBootApplication
public class DataSenderApplication {

    private static final Logger logger = LoggerFactory.getLogger(DataSenderApplication.class);

    private static ConcurrentHashMap<String, LinkedBlockingQueue<CanalBean>> concurrentHashMap;

    @Bean
    public static ConcurrentHashMap<String, LinkedBlockingQueue<CanalBean>> createConcurrentHashMap() {
        if (concurrentHashMap == null) {
            concurrentHashMap = new ConcurrentHashMap<>();
        }
        return concurrentHashMap;
    }

    public static void main(String[] args) {

        ConfigurableApplicationContext applicationContext = SpringApplication.run(DataSenderApplication.class, args);

        KuduConfig kuduConfig = applicationContext.getBean(KuduConfig.class);
        ConcurrentHashMap<String, LinkedBlockingQueue<CanalBean>> blockingQueueConcurrentHashMap = createConcurrentHashMap();

        ExecutorService executorService = Executors.newCachedThreadPool();
        List<WorkerThread> workerThreadList = new ArrayList<>();
        List<KuduClient> kuduClientList = new ArrayList<>();


        for (Map.Entry<String, String> entry : kuduConfig.getTableMappings().entrySet()) {
            String workerThreadName = entry.getValue();
            LinkedBlockingQueue<CanalBean> blockingQueue;
            if (blockingQueueConcurrentHashMap.containsKey(workerThreadName)) {
                logger.debug("the queue involved with impala table {} already exists.", workerThreadName);
                blockingQueue = blockingQueueConcurrentHashMap.get(workerThreadName);
            } else {
                logger.debug("the queue involved with impala table {} is not exists.", workerThreadName);
                blockingQueue = new LinkedBlockingQueue<>(kuduConfig.getQueueSize());
                blockingQueueConcurrentHashMap.put(workerThreadName, blockingQueue);
            }
            KuduClient client = new KuduClient.KuduClientBuilder(kuduConfig.getMaster()).build();
            kuduClientList.add(client);
            WorkerThread workerThread = new WorkerThread(client, workerThreadName, blockingQueue);
            workerThreadList.add(workerThread);
            executorService.execute(workerThread);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("close all opened kudu client.");
            for (KuduClient kuduClient:kuduClientList){
                try {
                    kuduClient.close();
                } catch (KuduException e) {
                    logger.error("close Kudu client failed. cause: {}, message: {}.", e.getCause(), e.getMessage());
                }
            }

            logger.info("clean the thread pool.");
            for (WorkerThread thread : workerThreadList) {
                thread.stop();
            }
            logger.info("shutdown thread pool.");
            executorService.shutdown();
        }));

    }
}

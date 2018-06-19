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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

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

        ExecutorService executorService = Executors.newCachedThreadPool();
        Map<String, WorkerThread> workerMap = new HashMap<>();
        Map<String, Future<?>> workerFutureMap = new HashMap<>();
        KuduClient kuduClient = new KuduClient.KuduClientBuilder(kuduConfig.getMaster()).build();

        AtomicReference<Boolean> isMonitoring = new AtomicReference<>(true);

        executorService.execute(() -> {
            while (isMonitoring.get()) {
                try {
                    Thread.sleep(kuduConfig.getScanInterval());
                    logger.debug("the size of thread pool: {}", ((ThreadPoolExecutor) executorService).getPoolSize());
                    for (Map.Entry<String, LinkedBlockingQueue<CanalBean>> entry : concurrentHashMap.entrySet()) {
                        String key = entry.getKey();
                        LinkedBlockingQueue<CanalBean> queue = entry.getValue();
                        if ((!workerMap.containsKey(key) && queue.size() > 0) || (workerMap.containsKey(key) && workerFutureMap.get(key).isDone() && queue.size() > 0)) {
                            WorkerThread workerThread = new WorkerThread(kuduClient, key, queue, kuduConfig.getIdleTimeout(), kuduConfig.getMaxWaitTime());
                            workerMap.put(key, workerThread);
                            Future<?> future = executorService.submit(workerThread);
                            workerFutureMap.put(key, future);
                        }
                    }
                } catch (InterruptedException e) {
                    logger.error("monitor thread exception. cause: {}, message: {}.", e.getCause(), e.getMessage());
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("stop monitor thread.");
            isMonitoring.set(false);
            logger.info("stop worker thread.");
            for (Map.Entry<String, WorkerThread> entry : workerMap.entrySet()) {
                entry.getValue().stop();
            }
            logger.info("close kudu client.");
            try {
                kuduClient.close();
            } catch (KuduException e) {
                logger.error("close Kudu client failed. cause: {}, message: {}.", e.getCause(), e.getMessage());
            }
            logger.info("shutdown thread pool.");
            executorService.shutdown();
        }));
    }
}

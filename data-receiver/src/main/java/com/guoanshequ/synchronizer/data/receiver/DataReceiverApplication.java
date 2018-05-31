package com.guoanshequ.synchronizer.data.receiver;

import com.guoanshequ.synchronizer.data.receiver.canal.CanalClient;
import com.guoanshequ.synchronizer.data.receiver.kafka.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class DataReceiverApplication {

    private static final Logger logger = LoggerFactory.getLogger(DataReceiverApplication.class);

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(DataReceiverApplication.class, args);

        CanalClient canalClient = context.getBean(CanalClient.class);
        KafkaProducer kafkaProducer = context.getBean(KafkaProducer.class);

        ExecutorService executorService = Executors.newCachedThreadPool();
        // start canal client thread
        executorService.execute(canalClient);
        executorService.execute(kafkaProducer);


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            canalClient.stop();
            logger.info("stop  the canal client.");
            kafkaProducer.stop();
            logger.info("stop  the kafka producer.");
            executorService.shutdown();
        }));

    }
}

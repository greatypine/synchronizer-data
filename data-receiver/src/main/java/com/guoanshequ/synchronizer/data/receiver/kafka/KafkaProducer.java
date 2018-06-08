package com.guoanshequ.synchronizer.data.receiver.kafka;

import com.alibaba.fastjson.JSON;
import com.guoanshequ.synchronizer.data.model.CanalBean;
import com.guoanshequ.synchronizer.data.receiver.common.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaProducer implements Runnable {

    private final static Logger logger = LoggerFactory.getLogger(KafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final MessageQueue<CanalBean> queue;

    private volatile boolean isRunning = true;

    @Autowired
    public KafkaProducer(MessageQueue<CanalBean> queue, KafkaTemplate<String, String> kafkaTemplate) {
        this.queue = queue;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void run() {

        try {
            while (isRunning) {
                CanalBean canalBean = queue.take();
                String canalBeanJsonStr = JSON.toJSONString(canalBean);
                logger.debug("message context: {}", canalBeanJsonStr);
                kafkaTemplate.send(canalBean.getDatabase(), canalBeanJsonStr);
            }
        } catch (InterruptedException e) {
            logger.error("the kafka producer client failed. cause: {}, message: {}", e.getCause(), e.getMessage());
            isRunning = false;
        }
    }

    public void stop() {
        isRunning = false;
    }
}

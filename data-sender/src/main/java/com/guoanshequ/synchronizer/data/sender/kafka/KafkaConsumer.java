package com.guoanshequ.synchronizer.data.sender.kafka;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.guoanshequ.synchronizer.data.model.CanalBean;
import com.guoanshequ.synchronizer.data.sender.config.KuduConfig;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class KafkaConsumer {

    private final static Logger logger = LoggerFactory.getLogger(KafkaConsumer.class);

    private final KuduConfig kuduConfig;

    private final ConcurrentHashMap<String, LinkedBlockingQueue<CanalBean>> queueConcurrentHashMap;

    @Autowired
    public KafkaConsumer(KuduConfig kuduConfig, ConcurrentHashMap<String, LinkedBlockingQueue<CanalBean>> queueConcurrentHashMap) {
        this.kuduConfig = kuduConfig;
        this.queueConcurrentHashMap = queueConcurrentHashMap;
    }

    @KafkaListener(topics = "#{kafkaConfig.getTopics()}")
    public void consumer(String message) {
        CanalBean canalBean = JSON.parseObject(message, new TypeReference<CanalBean>(){});
        logger.debug("{} topic message : {}", canalBean.getDatabase(), message);

        String kuduTableName = kuduConfig.getTableMappings().get(StringUtils.join(new String[]{canalBean.getDatabase(), ".", canalBean.getTable()}));

        LinkedBlockingQueue<CanalBean> canalBeanLinkedBlockingQueue = queueConcurrentHashMap.get(kuduTableName);
        if (canalBeanLinkedBlockingQueue == null) {
            logger.debug("the queue for [{}] is not exist at consumer listener.", kuduTableName);
            canalBeanLinkedBlockingQueue = new LinkedBlockingQueue<>(kuduConfig.getQueueSize());
            queueConcurrentHashMap.put(kuduTableName, canalBeanLinkedBlockingQueue);
        } else {
            logger.debug("the queue for [{}] has been created.", kuduTableName);
        }

        try {
            canalBeanLinkedBlockingQueue.put(canalBean);
        } catch (InterruptedException e) {
            logger.error("getting kafka message failed. cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }
}

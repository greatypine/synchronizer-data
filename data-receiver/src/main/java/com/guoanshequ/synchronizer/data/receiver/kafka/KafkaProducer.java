package com.guoanshequ.synchronizer.data.receiver.kafka;

import com.alibaba.fastjson.JSON;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.guoanshequ.synchronizer.data.model.CanalBean;
import com.guoanshequ.synchronizer.data.receiver.common.MessageQueue;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.alibaba.otter.canal.protocol.CanalEntry.EventType.DELETE;
import static com.alibaba.otter.canal.protocol.CanalEntry.EventType.INSERT;
import static com.alibaba.otter.canal.protocol.CanalEntry.EventType.UPDATE;

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

        String infoFormat = "SEND MSG: {}, DB: {}, TABLE: {}, KEY: [{}], OPE: {}";
        Object[] infoValues;

        try {
            while (isRunning) {
                CanalBean canalBean = queue.take();
                infoValues = new String[5];
                String canalBeanJsonStr = JSON.toJSONString(canalBean);
                logger.debug("message context: {}", canalBeanJsonStr);
                ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(canalBean.getDatabase(), canalBeanJsonStr);
                SendResult<String, String> sendResult = future.get();
                infoValues[0] = sendResult.getRecordMetadata().toString();
                infoValues[1] = canalBean.getDatabase();
                infoValues[2] = canalBean.getTable();
                infoValues[4] = CanalEntry.EventType.valueOf(canalBean.getEventType()).name();
                Map<String, CanalBean.RowData.ColumnEntry> columnEntryMap = null;
                if(canalBean.getEventType() == INSERT.getNumber() || canalBean.getEventType() == UPDATE.getNumber()) {
                    columnEntryMap = canalBean.getRowData().getAfterColumns();
                } else if(canalBean.getEventType() == DELETE.getNumber()) {
                    columnEntryMap = canalBean.getRowData().getBeforeColumns();
                }
                List<String> keyValues = new ArrayList<>();
                columnEntryMap.forEach((k, v) -> {
                    if(v.getIsKey()) {
                        keyValues.add(StringUtils.join(new String[]{k, "=", v.getValue()}));
                    }
                });
                infoValues[3] = StringUtils.join(keyValues.toArray(), ",");
                logger.info(infoFormat, infoValues);
            }
        } catch (InterruptedException e) {
            logger.error("the kafka producer client failed. cause: {}, message: {}", e.getCause(), e.getMessage());
            isRunning = false;
        } catch (ExecutionException e) {
            logger.error("message send failure. cause: {}, message: {}", e.getCause(), e.getMessage());
            isRunning = false;
        }
    }

    public void stop() {
        isRunning = false;
    }
}

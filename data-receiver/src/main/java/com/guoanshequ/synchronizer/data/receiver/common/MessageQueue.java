package com.guoanshequ.synchronizer.data.receiver.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;

@Component
public class MessageQueue<CanalBean> extends LinkedBlockingQueue<CanalBean> {

    public MessageQueue(@Value("${canal.client.queueInitSize}") int capacity) {
        super(capacity);
    }

    public int getCapacity() {
        return size() + remainingCapacity();
    }
}

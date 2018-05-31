package com.guoanshequ.synchronizer.data.sender.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaConfig {

    private List<String> topics;

    KafkaConfig() {
        this.topics = new ArrayList<>();
    }

    public List<String> getTopics() {
        return this.topics;
    }

}

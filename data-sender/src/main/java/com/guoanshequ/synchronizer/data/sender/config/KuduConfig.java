package com.guoanshequ.synchronizer.data.sender.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "app.kudu")
public class KuduConfig {

    private String master;

    private Map<String, String> TableMappings;

    private int queueSize;

    public KuduConfig() {
        this.TableMappings = new HashMap<>();
    }

    public String getMaster() {
        return master;
    }

    public void setMaster(String master) {
        this.master = master;
    }

    public Map<String, String> getTableMappings() {
        return TableMappings;
    }

    public void setTableMappings(Map<String, String> tableMappings) {
        TableMappings = tableMappings;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }
}

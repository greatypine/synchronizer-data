package com.guoanshequ.synchronizer.data.sender.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "app.kudu", ignoreUnknownFields = false)
public class KuduConfig {

    private String master;

    private Map<String, String> TableMappings;

    private int queueSize;

    private int idleTimeout;

    private int scanInterval;

    private long maxWaitTime;

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

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public int getScanInterval() {
        return scanInterval;
    }

    public void setScanInterval(int scanInterval) {
        this.scanInterval = scanInterval;
    }

    public long getMaxWaitTime() {
        return maxWaitTime;
    }

    public void setMaxWaitTime(long maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }
}

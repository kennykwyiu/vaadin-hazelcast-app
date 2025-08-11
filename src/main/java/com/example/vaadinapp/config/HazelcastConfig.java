package com.example.vaadinapp.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;

@Configuration
@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes
public class HazelcastConfig {

    @Bean
    public HazelcastInstance hazelcastInstance() {
        Config config = new Config();
        config.setClusterName("vaadin-cluster");
        
        // Network configuration
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(5701);
        networkConfig.setPortAutoIncrement(true);
        networkConfig.setPortCount(100);
        
        // Join configuration for clustering
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(true);
        joinConfig.getTcpIpConfig().setEnabled(false);
        joinConfig.getAwsConfig().setEnabled(false);
        joinConfig.getGcpConfig().setEnabled(false);
        joinConfig.getAzureConfig().setEnabled(false);
        joinConfig.getKubernetesConfig().setEnabled(false);
        joinConfig.getEurekaConfig().setEnabled(false);
        
        // Management center (optional, for monitoring)
        config.getManagementCenterConfig().setConsoleEnabled(true);
        
        return Hazelcast.newHazelcastInstance(config);
    }
}


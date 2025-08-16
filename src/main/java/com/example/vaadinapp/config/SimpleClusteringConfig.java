package com.example.vaadinapp.config;


import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimpleClusteringConfig implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        // Minimal configuration - let Spring Session handle the clustering
        event.getSource().addSessionInitListener(sessionInitEvent -> {
            // Simple session timeout configuration
            try {
                sessionInitEvent.getSession().getSession()
                        .setMaxInactiveInterval(1800); // 30 minutes
            } catch (Exception e) {
                // Ignore if session is not available
            }
        });
    }
}

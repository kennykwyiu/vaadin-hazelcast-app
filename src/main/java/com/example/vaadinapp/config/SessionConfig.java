package com.example.vaadinapp.config;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to handle Vaadin session management with Hazelcast
 * and prevent recursive reloading issues.
 */
@Configuration
public class SessionConfig implements VaadinServiceInitListener {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionConfig.class);
    
    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(sessionInitEvent -> {
            VaadinSession session = sessionInitEvent.getSession();
            logger.debug("Vaadin session initialized: {}", session.getSession().getId());
            
            // Configure session to work properly with Hazelcast
            configureSessionForClustering(session);
        });
        
        event.getSource().addSessionDestroyListener(sessionDestroyEvent -> {
            VaadinSession session = sessionDestroyEvent.getSession();
            logger.debug("Vaadin session destroyed: {}", session.getSession().getId());
        });
    }
    
    private void configureSessionForClustering(VaadinSession vaadinSession) {
        try {
            // Set session attributes that are safe for clustering
            com.vaadin.flow.server.WrappedSession wrappedSession = vaadinSession.getSession();
            
            // Mark this as a Vaadin session for proper handling
            wrappedSession.setAttribute("vaadin.session.type", "clustered");
            wrappedSession.setAttribute("vaadin.session.created", System.currentTimeMillis());
            
            // Configure session timeout
            wrappedSession.setMaxInactiveInterval(1800); // 30 minutes
            
            logger.debug("Configured session for clustering: {}", wrappedSession.getId());
            
        } catch (Exception e) {
            logger.error("Error configuring session for clustering", e);
        }
    }
}


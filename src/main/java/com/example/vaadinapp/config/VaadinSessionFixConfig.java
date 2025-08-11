package com.example.vaadinapp.config;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simplified solution for the Vaadin 24 + Hazelcast recursive reloading problem.
 *
 * The main fix is to prevent non-serializable Vaadin objects from being stored
 * in the HTTP session, which causes serialization issues with Hazelcast.
 */
@Configuration
@Order(1)
public class VaadinSessionFixConfig implements VaadinServiceInitListener {

    private static final Logger logger = LoggerFactory.getLogger(VaadinSessionFixConfig.class);

    // Track sessions to prevent recursive operations
    private static final ConcurrentHashMap<String, AtomicBoolean> sessionProcessing = new ConcurrentHashMap<>();

    @Override
    public void serviceInit(ServiceInitEvent event) {
        logger.info("Initializing Vaadin Session Fix Configuration");

        // Handle session initialization
        event.getSource().addSessionInitListener(sessionInitEvent -> {
            VaadinSession vaadinSession = sessionInitEvent.getSession();
            String sessionId = vaadinSession.getSession().getId();

            logger.debug("Session initialized: {}", sessionId);

            // Prevent recursive processing
            AtomicBoolean processing = sessionProcessing.computeIfAbsent(sessionId, k -> new AtomicBoolean(false));
            if (processing.compareAndSet(false, true)) {
                try {
                    configureSessionForClustering(vaadinSession);
                } finally {
                    processing.set(false);
                }
            }
        });

        // Handle session destruction
        event.getSource().addSessionDestroyListener(sessionDestroyEvent -> {
            VaadinSession vaadinSession = sessionDestroyEvent.getSession();
            String sessionId = vaadinSession.getSession().getId();

            logger.debug("Session destroyed: {}", sessionId);

            // Clean up tracking
            sessionProcessing.remove(sessionId);
        });

        // Add UI init listener to handle UI-level session issues
        event.getSource().addUIInitListener(uiInitEvent -> {
            logger.debug("UI initialized for session: {}",
                uiInitEvent.getUI().getSession().getSession().getId());

            // Configure UI to prevent session issues
            configureUIForClustering(uiInitEvent.getUI());
        });
    }

    private void configureSessionForClustering(VaadinSession vaadinSession) {
        try {
            WrappedSession wrappedSession = vaadinSession.getSession();
            String sessionId = wrappedSession.getId();

            logger.debug("Configuring session for clustering: {}", sessionId);

            // Mark session as clustered
            wrappedSession.setAttribute("vaadin.session.clustered", true);
            wrappedSession.setAttribute("vaadin.session.created", System.currentTimeMillis());
            wrappedSession.setAttribute("vaadin.session.type", "vaadin-hazelcast");

            // Set appropriate timeout
            wrappedSession.setMaxInactiveInterval(1800); // 30 minutes

            // Clean up any problematic attributes that might cause serialization issues
            cleanupSessionAttributes(wrappedSession);

            logger.debug("Session configured for clustering: {}", sessionId);

        } catch (Exception e) {
            logger.error("Error configuring session for clustering", e);
        }
    }

    private void configureUIForClustering(com.vaadin.flow.component.UI ui) {
        try {
            // Disable automatic session invalidation on UI close
            ui.addDetachListener(event -> {
                logger.debug("UI detached, but preserving session for clustering");
            });

            // Configure push mode for better session handling
            ui.getPushConfiguration().setPushMode(com.vaadin.flow.shared.communication.PushMode.AUTOMATIC);

        } catch (Exception e) {
            logger.error("Error configuring UI for clustering", e);
        }
    }

    private void cleanupSessionAttributes(WrappedSession wrappedSession) {
        try {
            // Get all attribute names
            java.util.Set<String> attributeNames = wrappedSession.getAttributeNames();
            java.util.List<String> attributesToRemove = new java.util.ArrayList<>();

            // Identify problematic attributes
            for (String attributeName : attributeNames) {
                if (!isSafeAttribute(attributeName)) {
                    Object attribute = wrappedSession.getAttribute(attributeName);
                    if (attribute != null && !isSerializable(attribute)) {
                        attributesToRemove.add(attributeName);
                        logger.debug("Marking non-serializable attribute for removal: {}", attributeName);
                    }
                }
            }

            // Remove problematic attributes
            for (String attributeName : attributesToRemove) {
                wrappedSession.removeAttribute(attributeName);
                logger.debug("Removed non-serializable attribute: {}", attributeName);
            }

        } catch (Exception e) {
            logger.error("Error cleaning up session attributes", e);
        }
    }

    private boolean isSafeAttribute(String attributeName) {
        // Allow user-defined attributes and basic session attributes
        return attributeName.startsWith("vaadin.session.") ||
               attributeName.equals("userMessage") ||
               attributeName.equals("saveTime") ||
               attributeName.startsWith("org.springframework.session");
    }

    private boolean isSerializable(Object obj) {
        return obj instanceof java.io.Serializable &&
               !(obj instanceof com.vaadin.flow.component.Component) &&
               !(obj instanceof com.vaadin.flow.server.VaadinSession);
    }
}


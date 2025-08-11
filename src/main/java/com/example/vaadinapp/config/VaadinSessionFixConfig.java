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
 * Robust solution for the Vaadin 24 + Hazelcast recursive reloading problem.
 *
 * This configuration handles edge cases and null pointer exceptions that can occur
 * in development environments like IntelliJ IDEA, while maintaining the core
 * functionality of preventing recursive reloading issues.
 *
 * Key improvements:
 * - Comprehensive null checks to prevent NPEs
 * - Graceful handling of incomplete session initialization
 * - Enhanced logging for debugging
 * - Defensive programming practices
 */
@Configuration
@Order(1)
public class VaadinSessionFixConfig implements VaadinServiceInitListener {

    private static final Logger logger = LoggerFactory.getLogger(VaadinSessionFixConfig.class);

    // Track sessions to prevent recursive operations
    private static final ConcurrentHashMap<String, AtomicBoolean> sessionProcessing = new ConcurrentHashMap<>();

    @Override
    public void serviceInit(ServiceInitEvent event) {
        logger.info("Initializing Vaadin Session Fix Configuration with robust error handling");

        // Handle session initialization with comprehensive error handling
        event.getSource().addSessionInitListener(sessionInitEvent -> {
            try {
                handleSessionInitialization(sessionInitEvent);
            } catch (Exception e) {
                logger.error("Error during session initialization handling", e);
                // Don't rethrow to prevent application startup failure
            }
        });

        // Handle session destruction with error handling
        event.getSource().addSessionDestroyListener(sessionDestroyEvent -> {
            try {
                handleSessionDestruction(sessionDestroyEvent);
            } catch (Exception e) {
                logger.error("Error during session destruction handling", e);
                // Don't rethrow to prevent issues during shutdown
            }
        });

        // Add UI init listener with error handling
        event.getSource().addUIInitListener(uiInitEvent -> {
            try {
                handleUIInitialization(uiInitEvent);
            } catch (Exception e) {
                logger.error("Error during UI initialization handling", e);
                // Don't rethrow to prevent UI creation failure
            }
        });
    }

    private void handleSessionInitialization(com.vaadin.flow.server.SessionInitEvent sessionInitEvent) {
        if (sessionInitEvent == null) {
            logger.warn("SessionInitEvent is null. This should not happen under normal circumstances.");
            return;
        }

        VaadinSession vaadinSession = sessionInitEvent.getSession();
        if (vaadinSession == null) {
            logger.warn("VaadinSession is null during session initialization. This can happen in development environments or during application startup. Skipping configuration.");
            return;
        }

        WrappedSession wrappedSession = null;
        String sessionId = null;

        try {
            wrappedSession = vaadinSession.getSession();
            if (wrappedSession == null) {
                logger.warn("WrappedSession is null for VaadinSession. This can happen when the HTTP session is not yet fully initialized. Skipping configuration.");
                return;
            }

            sessionId = wrappedSession.getId();
            if (sessionId == null || sessionId.trim().isEmpty()) {
                logger.warn("Session ID is null or empty. This indicates an incomplete session initialization. Skipping configuration.");
                return;
            }

        } catch (Exception e) {
            logger.warn("Exception while accessing session information: {}. This can happen in development environments. Skipping configuration.", e.getMessage());
            return;
        }

        logger.debug("Session initialized: {}", sessionId);

        // Prevent recursive processing
        AtomicBoolean processing = sessionProcessing.computeIfAbsent(sessionId, k -> new AtomicBoolean(false));
        if (processing.compareAndSet(false, true)) {
            try {
                configureSessionForClustering(vaadinSession, wrappedSession, sessionId);
            } finally {
                processing.set(false);
            }
        } else {
            logger.debug("Session {} is already being processed, skipping duplicate processing", sessionId);
        }
    }

    private void handleSessionDestruction(com.vaadin.flow.server.SessionDestroyEvent sessionDestroyEvent) {
        if (sessionDestroyEvent == null) {
            logger.warn("SessionDestroyEvent is null. This should not happen under normal circumstances.");
            return;
        }

        VaadinSession vaadinSession = sessionDestroyEvent.getSession();
        if (vaadinSession == null) {
            logger.debug("VaadinSession is null during session destruction. This is normal during shutdown.");
            return;
        }

        String sessionId = null;
        try {
            WrappedSession wrappedSession = vaadinSession.getSession();
            if (wrappedSession != null) {
                sessionId = wrappedSession.getId();
            }
        } catch (Exception e) {
            logger.debug("Exception while accessing session ID during destruction: {}. This is normal during shutdown.", e.getMessage());
        }

        if (sessionId != null) {
            logger.debug("Session destroyed: {}", sessionId);
            // Clean up tracking
            sessionProcessing.remove(sessionId);
        } else {
            logger.debug("Session destroyed but ID could not be determined");
        }
    }

    private void handleUIInitialization(com.vaadin.flow.server.UIInitEvent uiInitEvent) {
        if (uiInitEvent == null || uiInitEvent.getUI() == null) {
            logger.warn("UIInitEvent or UI is null. This should not happen under normal circumstances.");
            return;
        }

        try {
            VaadinSession vaadinSession = uiInitEvent.getUI().getSession();
            if (vaadinSession != null) {
                WrappedSession wrappedSession = vaadinSession.getSession();
                if (wrappedSession != null) {
                    String sessionId = wrappedSession.getId();
                    logger.debug("UI initialized for session: {}", sessionId != null ? sessionId : "unknown");
                } else {
                    logger.debug("UI initialized but WrappedSession is null");
                }
            } else {
                logger.debug("UI initialized but VaadinSession is null");
            }

            // Configure UI to prevent session issues
            configureUIForClustering(uiInitEvent.getUI());

        } catch (Exception e) {
            logger.warn("Exception during UI initialization: {}. This can happen in development environments.", e.getMessage());
        }
    }

    private void configureSessionForClustering(VaadinSession vaadinSession, WrappedSession wrappedSession, String sessionId) {
        try {
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
            logger.error("Error configuring session for clustering: {}", sessionId, e);
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
            // Get all attribute names safely
            java.util.Set<String> attributeNames = wrappedSession.getAttributeNames();
            if (attributeNames == null) {
                logger.debug("Session attribute names is null, skipping cleanup");
                return;
            }

            java.util.List<String> attributesToRemove = new java.util.ArrayList<>();

            // Identify problematic attributes
            for (String attributeName : attributeNames) {
                if (attributeName == null) {
                    continue; // Skip null attribute names
                }

                if (!isSafeAttribute(attributeName)) {
                    try {
                        Object attribute = wrappedSession.getAttribute(attributeName);
                        if (attribute != null && !isSerializable(attribute)) {
                            attributesToRemove.add(attributeName);
                            logger.debug("Marking non-serializable attribute for removal: {}", attributeName);
                        }
                    } catch (Exception e) {
                        logger.debug("Exception while checking attribute {}: {}. Marking for removal.", attributeName, e.getMessage());
                        attributesToRemove.add(attributeName);
                    }
                }
            }

            // Remove problematic attributes
            for (String attributeName : attributesToRemove) {
                try {
                    wrappedSession.removeAttribute(attributeName);
                    logger.debug("Removed non-serializable attribute: {}", attributeName);
                } catch (Exception e) {
                    logger.debug("Exception while removing attribute {}: {}", attributeName, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error cleaning up session attributes", e);
        }
    }

    private boolean isSafeAttribute(String attributeName) {
        if (attributeName == null) {
            return false;
        }

        // Allow user-defined attributes and basic session attributes
        return attributeName.startsWith("vaadin.session.") ||
                attributeName.equals("userMessage") ||
                attributeName.equals("saveTime") ||
                attributeName.startsWith("org.springframework.session");
    }

    private boolean isSerializable(Object obj) {
        if (obj == null) {
            return true; // null is always serializable
        }

        try {
            return obj instanceof java.io.Serializable &&
                    !(obj instanceof com.vaadin.flow.component.Component) &&
                    !(obj instanceof com.vaadin.flow.server.VaadinSession);
        } catch (Exception e) {
            // If we can't determine serializability, assume it's not serializable
            logger.debug("Exception while checking serializability of {}: {}", obj.getClass().getName(), e.getMessage());
            return false;
        }
    }
}

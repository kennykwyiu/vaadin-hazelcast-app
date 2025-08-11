package com.example.vaadinapp.config;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Custom serializer for VaadinSession to prevent recursive reloading issues
 * when using Hazelcast for session clustering.
 */
public class VaadinSessionSerializer implements StreamSerializer<VaadinSession> {
    
    private static final Logger logger = LoggerFactory.getLogger(VaadinSessionSerializer.class);
    
    @Override
    public void write(ObjectDataOutput out, VaadinSession session) throws IOException {
        try {
            // Instead of serializing the entire VaadinSession (which can cause issues),
            // we'll serialize only essential session data
            out.writeUTF(session.getSession().getId());
            out.writeLong(session.getSession().getCreationTime());
            out.writeLong(session.getSession().getLastAccessedTime());
            out.writeInt(session.getSession().getMaxInactiveInterval());
            
            // Serialize session attributes that are safe to serialize
            java.util.Set<String> attributeNames = session.getSession().getAttributeNames();
            java.util.List<String> safeAttributes = new java.util.ArrayList<>();
            
            for (String attributeName : attributeNames) {
                Object attribute = session.getSession().getAttribute(attributeName);
                
                // Only serialize simple, safe attributes
                if (attribute instanceof String || 
                    attribute instanceof Number || 
                    attribute instanceof Boolean ||
                    attribute instanceof java.io.Serializable) {
                    safeAttributes.add(attributeName);
                }
            }
            
            out.writeInt(safeAttributes.size());
            for (String attributeName : safeAttributes) {
                out.writeUTF(attributeName);
                out.writeObject(session.getSession().getAttribute(attributeName));
            }
            
            logger.debug("Serialized VaadinSession with {} safe attributes", safeAttributes.size());
            
        } catch (Exception e) {
            logger.error("Error serializing VaadinSession", e);
            throw new IOException("Failed to serialize VaadinSession", e);
        }
    }

    @Override
    public VaadinSession read(ObjectDataInput in) throws IOException {
        try {
            // Read basic session info
            String sessionId = in.readUTF();
            long creationTime = in.readLong();
            long lastAccessedTime = in.readLong();
            int maxInactiveInterval = in.readInt();
            
            // Read safe attributes
            int attributeCount = in.readInt();
            java.util.Map<String, Object> attributes = new java.util.HashMap<>();
            
            for (int i = 0; i < attributeCount; i++) {
                String attributeName = in.readUTF();
                Object attributeValue = in.readObject();
                attributes.put(attributeName, attributeValue);
            }
            
            logger.debug("Deserialized VaadinSession with {} attributes", attributeCount);
            
            // Note: We cannot fully reconstruct a VaadinSession from serialized data
            // This is a placeholder implementation. In practice, Vaadin sessions
            // should be handled differently to avoid serialization issues.
            return null; // Return null to indicate this session needs to be recreated
            
        } catch (Exception e) {
            logger.error("Error deserializing VaadinSession", e);
            throw new IOException("Failed to deserialize VaadinSession", e);
        }
    }

    @Override
    public int getTypeId() {
        return 1001; // Unique type ID for this serializer
    }
}


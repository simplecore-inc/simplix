package dev.simplecore.simplix.event.model;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a message event in the SimpliX Event system.
 * This class encapsulates all necessary information for event messaging including payload,
 * headers, metadata, and routing information.
 */
public class SimpliXMessageEvent {
    /** The actual message content */
    private Object payload;
    
    /** Additional message metadata */
    private Map<String, Object> headers = new HashMap<>();
    
    /** Unique identifier for the event */
    private String eventId = UUID.randomUUID().toString();
    
    /** Timestamp when the event was created */
    private OffsetDateTime timestamp = OffsetDateTime.now();
    
    /** Source of the event */
    private String source;
    
    /** Channel name for routing the event */
    private String channelName;

    public SimpliXMessageEvent() {}

    public SimpliXMessageEvent(Object payload) {
        this.payload = payload;
    }

    public SimpliXMessageEvent(Object payload, String channelName) {
        this.payload = payload;
        this.channelName = channelName;
    }

    public SimpliXMessageEvent(Object payload, Map<String, Object> headers, String channelName) {
        this.payload = payload;
        this.headers = headers;
        this.channelName = channelName;
    }

    public Object getPayload() {
        return payload;
    }

    public SimpliXMessageEvent setPayload(Object payload) {
        this.payload = payload;
        return this;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public SimpliXMessageEvent setHeaders(Map<String, Object> headers) {
        this.headers = headers;
        return this;
    }

    public String getEventId() {
        return eventId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    public SimpliXMessageEvent setSource(String source) {
        this.source = source;
        return this;
    }

    public String getChannelName() {
        return channelName;
    }

    public SimpliXMessageEvent setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    @Override
    public String toString() {
        return "SimpliXMessageEvent{" +
                "eventId='" + eventId + '\'' +
                ", timestamp=" + timestamp +
                ", source='" + source + '\'' +
                ", channelName='" + channelName + '\'' +
                ", payload=" + payload +
                ", headers=" + headers +
                '}';
    }
}
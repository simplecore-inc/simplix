package dev.simplecore.simplix.event.constant;

/**
 * Constants used throughout the SimpliX Event system.
 */
public class SimpliXEventConstants {
    /** Default channel for outbound messages */
    public static final String DEFAULT_OUTBOUND_CHANNEL = "simpliXOutboundChannel";
    
    /** Default channel for inbound messages */
    public static final String DEFAULT_INBOUND_CHANNEL = "simpliXInboundChannel";
    
    /** Default channel for error messages */
    public static final String DEFAULT_ERROR_CHANNEL = "errorChannel";
    
    /** Default exchange name for message brokers */
    public static final String DEFAULT_EXCHANGE_NAME = "simplix.events";
    
    /** Default channel name when no specific channel is specified */
    public static final String DEFAULT_CHANNEL_NAME = "default";
    
    /** Header key for specifying channel name in messages */
    public static final String CHANNEL_NAME_HEADER = "channelName";
} 
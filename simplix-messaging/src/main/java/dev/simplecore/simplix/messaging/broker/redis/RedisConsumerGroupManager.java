package dev.simplecore.simplix.messaging.broker.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Manages Redis Consumer Group lifecycle for stream-based messaging.
 *
 * <p>Provides idempotent group creation, consumer name generation,
 * and group metadata queries.
 */
@Slf4j
public class RedisConsumerGroupManager {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisConsumerGroupManager(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
    }

    /**
     * Ensure the consumer group exists for the given channel.
     * Uses XGROUP CREATE with MKSTREAM flag and offset "0" so that
     * the stream is auto-created if it does not exist, and all existing
     * messages are available for processing.
     * Idempotent: BUSYGROUP errors are ignored.
     *
     * @param channel   the logical channel name
     * @param groupName the consumer group name
     */
    public void ensureConsumerGroup(String channel, String groupName) {
        String streamKey = resolveKey(channel);
        try {
            redisTemplate.execute((RedisCallback<String>) connection ->
                    connection.streamCommands().xGroupCreate(
                            streamKey.getBytes(StandardCharsets.UTF_8),
                            groupName,
                            ReadOffset.from("0"),
                            true
                    ));
            log.info("Created consumer group '{}' on stream '{}'", groupName, streamKey);
        } catch (RedisSystemException e) {
            if (isBusyGroupError(e)) {
                log.debug("Consumer group '{}' already exists on stream '{}', skipping creation",
                        groupName, streamKey);
            } else {
                throw e;
            }
        }
    }

    /**
     * Generate a unique consumer name scoped to the given instance.
     *
     * @param instanceId the application instance identifier
     * @return a consumer name in the format "{instanceId}-{short-uuid}"
     */
    public String generateConsumerName(String instanceId) {
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return instanceId + "-" + shortUuid;
    }

    /**
     * Retrieve consumer group information for the given channel.
     *
     * @param channel the logical channel name
     * @return the group info from XINFO GROUPS
     */
    public org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroups getGroupInfo(String channel) {
        String streamKey = resolveKey(channel);
        return redisTemplate.opsForStream().groups(streamKey);
    }

    /**
     * Retrieve the number of pending messages for a consumer group.
     *
     * @param channel   the logical channel name
     * @param groupName the consumer group name
     * @return the pending message count, or 0 if no pending messages
     */
    public long getPendingCount(String channel, String groupName) {
        String streamKey = resolveKey(channel);
        PendingMessagesSummary summary = redisTemplate.opsForStream().pending(streamKey, groupName);
        if (summary == null) {
            return 0;
        }
        return summary.getTotalPendingMessages();
    }

    private String resolveKey(String channel) {
        return keyPrefix + channel;
    }

    private boolean isBusyGroupError(RedisSystemException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}

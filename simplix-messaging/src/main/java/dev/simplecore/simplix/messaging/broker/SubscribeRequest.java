package dev.simplecore.simplix.messaging.broker;

import dev.simplecore.simplix.messaging.core.MessageListener;

import java.time.Duration;
import java.util.Objects;

/**
 * Encapsulates all parameters required to subscribe to a channel.
 *
 * @param channel      the channel/stream/topic to subscribe to
 * @param groupName    the consumer group name (empty string for non-grouped consumption)
 * @param consumerName the unique consumer name within the group
 * @param batchSize    maximum messages to fetch per poll
 * @param pollTimeout  how long to block waiting for new messages
 * @param listener     the message listener callback
 */
public record SubscribeRequest(
        String channel,
        String groupName,
        String consumerName,
        int batchSize,
        Duration pollTimeout,
        MessageListener<byte[]> listener
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String channel;
        private String groupName = "";
        private String consumerName;
        private int batchSize = 10;
        private Duration pollTimeout = Duration.ofSeconds(2);
        private MessageListener<byte[]> listener;

        private Builder() {
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder groupName(String groupName) {
            this.groupName = groupName;
            return this;
        }

        public Builder consumerName(String consumerName) {
            this.consumerName = consumerName;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder pollTimeout(Duration pollTimeout) {
            this.pollTimeout = pollTimeout;
            return this;
        }

        public Builder listener(MessageListener<byte[]> listener) {
            this.listener = listener;
            return this;
        }

        public SubscribeRequest build() {
            Objects.requireNonNull(channel, "channel must not be null");
            Objects.requireNonNull(listener, "listener must not be null");
            return new SubscribeRequest(channel, groupName, consumerName, batchSize, pollTimeout, listener);
        }
    }
}

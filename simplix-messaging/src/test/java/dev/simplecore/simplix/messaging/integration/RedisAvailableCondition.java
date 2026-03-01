package dev.simplecore.simplix.messaging.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.Socket;

/**
 * JUnit 5 condition that skips tests when Redis is not reachable at localhost:6379.
 *
 * <p>Usage:
 * <pre>{@code
 * @ExtendWith(RedisAvailableCondition.class)
 * class MyRedisTest { ... }
 * }</pre>
 */
public class RedisAvailableCondition implements ExecutionCondition {

    private static final String HOST = "localhost";
    private static final int PORT = 6379;
    private static final int TIMEOUT_MS = 500;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (isRedisAvailable()) {
            return ConditionEvaluationResult.enabled("Redis is available at " + HOST + ":" + PORT);
        }
        return ConditionEvaluationResult.disabled(
                "Redis is not available at " + HOST + ":" + PORT + " - skipping integration test");
    }

    private static boolean isRedisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(HOST, PORT), TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

package dev.simplecore.simplix.messaging.integration;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.time.Duration;

/**
 * JUnit 5 execution condition that disables tests when NATS is not reachable
 * or JetStream is not enabled. Reads {@code SIMPLIX_TEST_NATS_URL} env var
 * (default: {@code nats://app:apppass@localhost:4222}).
 */
public class NatsAvailableCondition implements ExecutionCondition {

    private static final String DEFAULT_URL = "nats://app:apppass@localhost:4222";
    private static final String ENV_VAR = "SIMPLIX_TEST_NATS_URL";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext ctx) {
        String url = System.getenv().getOrDefault(ENV_VAR, DEFAULT_URL);
        try (Connection c = Nats.connect(Options.builder()
                .server(url)
                .connectionTimeout(Duration.ofSeconds(2))
                .build())) {
            c.jetStreamManagement().getStreamNames();
            return ConditionEvaluationResult.enabled("NATS available at " + url);
        } catch (Exception e) {
            return ConditionEvaluationResult.disabled(
                    "NATS not available at " + url + ": " + e.getMessage());
        }
    }
}

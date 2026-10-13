package dev.simplecore.simplix.license.scheduler;

import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationError;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationOutcome;
import dev.simplecore.simplix.license.activation.LicenseActivationService;
import dev.simplecore.simplix.license.config.LicenseProperties;
import dev.simplecore.simplix.license.core.LicenseManager;
import dev.simplecore.simplix.license.model.LicenseState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * When a heartbeat goes out: the signed interval in hours decides when one is due, and a failure
 * holds the next attempt for the retry interval rather than for the next check.
 */
class LicenseHeartbeatSchedulerTest {

    private static final Instant LAST_HEARTBEAT = Instant.parse("2026-10-13T00:00:00Z");

    private final MutableClock clock = new MutableClock(LAST_HEARTBEAT);
    private final LicenseActivationService activationService = mock(LicenseActivationService.class);
    private final LicenseManager licenseManager = mock(LicenseManager.class);
    private final LicensePayload payload = mock(LicensePayload.class);
    private final LicenseProperties properties = new LicenseProperties();
    private LicenseHeartbeatScheduler scheduler;

    @BeforeEach
    void setUp() {
        LicenseState state = mock(LicenseState.class);
        when(state.payload()).thenReturn(payload);
        when(licenseManager.getState()).thenReturn(state);
        when(payload.heartbeatRequired()).thenReturn(true);
        when(licenseManager.currentRecord()).thenReturn(Optional.of(new RegistrationRecord(
                "KEY", "token", "act_1", "secret", "ins_1", null, LAST_HEARTBEAT, null)));
        scheduler = new LicenseHeartbeatScheduler(activationService, licenseManager, properties,
                clock);
    }

    @Test
    @DisplayName("A one-hour interval falls due an hour after the last heartbeat")
    void aOneHourIntervalFallsDueAfterAnHour() {
        when(payload.heartbeatIntervalHours()).thenReturn(1);
        ActivationOutcome success = success();
        when(activationService.heartbeat()).thenReturn(success);

        clock.set(LAST_HEARTBEAT.plus(Duration.ofMinutes(55)));
        scheduler.heartbeatIfDue();
        verify(activationService, never()).heartbeat();

        clock.set(LAST_HEARTBEAT.plus(Duration.ofMinutes(65)));
        scheduler.heartbeatIfDue();
        verify(activationService, times(1)).heartbeat();
    }

    @Test
    @DisplayName("A license carrying no interval is refreshed hourly, as the SDK reads it")
    void aZeroIntervalReadsAsOneHour() {
        when(payload.heartbeatIntervalHours()).thenReturn(0);
        ActivationOutcome success = success();
        when(activationService.heartbeat()).thenReturn(success);

        clock.set(LAST_HEARTBEAT.plus(Duration.ofMinutes(65)));
        scheduler.heartbeatIfDue();
        verify(activationService, times(1)).heartbeat();
    }

    @Test
    @DisplayName("A failed heartbeat is retried after the retry interval, not on the next check")
    void aFailureWaitsForTheRetryInterval() {
        when(payload.heartbeatIntervalHours()).thenReturn(1);
        when(activationService.heartbeat()).thenReturn(
                ActivationOutcome.failure(ActivationError.SERVER_UNREACHABLE, "down"));

        Instant failedAt = LAST_HEARTBEAT.plus(Duration.ofMinutes(65));
        clock.set(failedAt);
        scheduler.heartbeatIfDue();

        clock.set(failedAt.plus(Duration.ofMinutes(5)));
        scheduler.heartbeatIfDue();
        verify(activationService, times(1)).heartbeat();

        clock.set(failedAt.plus(Duration.ofMinutes(10)));
        scheduler.heartbeatIfDue();
        verify(activationService, times(2)).heartbeat();
    }

    @Test
    @DisplayName("A refusal holds the next attempt the same way an unreachable server does")
    void aRefusalAlsoWaitsForTheRetryInterval() {
        when(payload.heartbeatIntervalHours()).thenReturn(1);
        properties.getActivation().setHeartbeatRetryInterval(Duration.ofMinutes(30));
        when(activationService.heartbeat()).thenReturn(
                ActivationOutcome.failure(ActivationError.REVOKED, "revoked"));

        Instant failedAt = LAST_HEARTBEAT.plus(Duration.ofMinutes(65));
        clock.set(failedAt);
        scheduler.heartbeatIfDue();

        clock.set(failedAt.plus(Duration.ofMinutes(25)));
        scheduler.heartbeatIfDue();
        verify(activationService, times(1)).heartbeat();
    }

    @Test
    @DisplayName("The settings default to a five-minute check and a ten-minute retry")
    void theSettingsHaveDefaults() {
        LicenseProperties.Activation activation = new LicenseProperties().getActivation();
        assertThat(activation.getHeartbeatCheckInterval())
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(activation.getHeartbeatRetryInterval())
                .isEqualTo(Duration.ofMinutes(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5m", "PT5M", "300s"})
    @DisplayName("The scheduling annotation reads the check interval in the forms the property binds")
    void theCheckIntervalIsReadByTheScheduler(String interval) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("application.license.activation.heartbeat-check-interval", interval)));
            context.registerBean(SchedulingOn.class);
            context.registerBean(LicenseHeartbeatScheduler.class,
                    () -> new LicenseHeartbeatScheduler(activationService, licenseManager,
                            properties, clock));
            assertThatCode(context::refresh).doesNotThrowAnyException();
        }
    }

    @EnableScheduling
    static class SchedulingOn {
    }

    private static ActivationOutcome success() {
        ActivationOutcome outcome = mock(ActivationOutcome.class);
        when(outcome.isSuccess()).thenReturn(true);
        return outcome;
    }

    /** A clock a case moves by hand. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

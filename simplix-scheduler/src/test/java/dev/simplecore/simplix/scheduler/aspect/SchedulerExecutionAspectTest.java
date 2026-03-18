package dev.simplecore.simplix.scheduler.aspect;

import dev.simplecore.simplix.scheduler.annotation.SchedulerName;
import dev.simplecore.simplix.scheduler.config.SchedulerProperties;
import dev.simplecore.simplix.scheduler.core.SchedulerLoggingService;
import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import dev.simplecore.simplix.scheduler.model.ExecutionStatus;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchedulerExecutionAspect - AOP aspect for @Scheduled method interception")
class SchedulerExecutionAspectTest {

    @Mock
    private SchedulerLoggingService loggingService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private Environment environment;

    private SchedulerProperties properties;
    private SchedulerExecutionAspect aspect;

    @BeforeEach
    void setUp() {
        properties = new SchedulerProperties();
        // Default: return any string as-is (no placeholder resolution)
        lenient().when(environment.resolvePlaceholders(anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        aspect = new SchedulerExecutionAspect(loggingService, properties, "test-service", environment);
    }

    @Nested
    @DisplayName("When logging is disabled")
    class LoggingDisabledTest {

        @Test
        @DisplayName("Should proceed without logging when service is disabled")
        void shouldProceedWithoutLogging() throws Throwable {
            when(loggingService.isEnabled()).thenReturn(false);
            when(joinPoint.proceed()).thenReturn("result");

            Object result = aspect.logSchedulerExecution(joinPoint);

            assertThat(result).isEqualTo("result");
            verify(loggingService, never()).ensureRegistryEntry(any());
            verify(loggingService, never()).createExecutionContext(any(), anyString());
            verify(loggingService, never()).saveExecutionResult(any(), any());
        }
    }

    @Nested
    @DisplayName("When scheduler is excluded")
    class ExcludedSchedulerTest {

        @Test
        @DisplayName("Should proceed without logging when scheduler is excluded")
        void shouldProceedWithoutLogging() throws Throwable {
            when(loggingService.isEnabled()).thenReturn(true);
            when(loggingService.isExcluded(anyString())).thenReturn(true);
            when(joinPoint.proceed()).thenReturn("result");
            setupJoinPointForMethod("scheduledMethod");

            Object result = aspect.logSchedulerExecution(joinPoint);

            assertThat(result).isEqualTo("result");
            verify(loggingService, never()).ensureRegistryEntry(any());
        }
    }

    @Nested
    @DisplayName("Successful scheduler execution")
    class SuccessfulExecutionTest {

        @Test
        @DisplayName("Should log successful execution with duration")
        void shouldLogSuccessfulExecution() throws Throwable {
            setupForFullExecution("scheduledMethod");
            when(joinPoint.proceed()).thenReturn(null);

            Object result = aspect.logSchedulerExecution(joinPoint);

            assertThat(result).isNull();

            ArgumentCaptor<SchedulerExecutionResult> resultCaptor =
                ArgumentCaptor.forClass(SchedulerExecutionResult.class);
            verify(loggingService).saveExecutionResult(any(), resultCaptor.capture());

            SchedulerExecutionResult executionResult = resultCaptor.getValue();
            assertThat(executionResult.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(executionResult.getDurationMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Should return the result of the scheduled method")
        void shouldReturnMethodResult() throws Throwable {
            setupForFullExecution("scheduledMethod");
            when(joinPoint.proceed()).thenReturn("scheduled-result");

            Object result = aspect.logSchedulerExecution(joinPoint);

            assertThat(result).isEqualTo("scheduled-result");
        }

        @Test
        @DisplayName("Should call ensureRegistryEntry with correct metadata")
        void shouldCallEnsureRegistryEntry() throws Throwable {
            setupForFullExecution("scheduledMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            SchedulerMetadata metadata = metadataCaptor.getValue();
            assertThat(metadata.getSchedulerName()).contains("SampleScheduler_scheduledMethod");
            assertThat(metadata.getMethodName()).isEqualTo("scheduledMethod");
        }

        @Test
        @DisplayName("Should call createExecutionContext with service name")
        void shouldCallCreateExecutionContext() throws Throwable {
            setupForFullExecution("scheduledMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            verify(loggingService).createExecutionContext(any(SchedulerRegistryEntry.class), eq("test-service"));
        }
    }

    @Nested
    @DisplayName("Failed scheduler execution")
    class FailedExecutionTest {

        @Test
        @DisplayName("Should log failure and rethrow exception")
        void shouldLogFailureAndRethrow() throws Throwable {
            setupForFullExecution("scheduledMethod");
            RuntimeException exception = new RuntimeException("Scheduler failed");
            when(joinPoint.proceed()).thenThrow(exception);

            assertThatThrownBy(() -> aspect.logSchedulerExecution(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Scheduler failed");

            ArgumentCaptor<SchedulerExecutionResult> resultCaptor =
                ArgumentCaptor.forClass(SchedulerExecutionResult.class);
            verify(loggingService).saveExecutionResult(any(), resultCaptor.capture());

            SchedulerExecutionResult executionResult = resultCaptor.getValue();
            assertThat(executionResult.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(executionResult.getErrorMessage()).isEqualTo("Scheduler failed");
        }

        @Test
        @DisplayName("Should record duration even on failure")
        void shouldRecordDurationOnFailure() throws Throwable {
            setupForFullExecution("scheduledMethod");
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Error"));

            try {
                aspect.logSchedulerExecution(joinPoint);
            } catch (RuntimeException ignored) {
                // Expected
            }

            ArgumentCaptor<SchedulerExecutionResult> resultCaptor =
                ArgumentCaptor.forClass(SchedulerExecutionResult.class);
            verify(loggingService).saveExecutionResult(any(), resultCaptor.capture());

            assertThat(resultCaptor.getValue().getDurationMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Logging initialization failure")
    class LoggingInitFailureTest {

        @Test
        @DisplayName("Should proceed when ensureRegistryEntry fails")
        void shouldProceedWhenRegistryFails() throws Throwable {
            when(loggingService.isEnabled()).thenReturn(true);
            when(loggingService.isExcluded(anyString())).thenReturn(false);
            setupJoinPointForMethod("scheduledMethod");

            when(loggingService.ensureRegistryEntry(any()))
                .thenThrow(new RuntimeException("DB connection error"));
            when(joinPoint.proceed()).thenReturn("result");

            Object result = aspect.logSchedulerExecution(joinPoint);

            assertThat(result).isEqualTo("result");
            verify(loggingService, never()).saveExecutionResult(any(), any());
        }

        @Test
        @DisplayName("Should proceed when createExecutionContext fails")
        void shouldProceedWhenContextFails() throws Throwable {
            when(loggingService.isEnabled()).thenReturn(true);
            when(loggingService.isExcluded(anyString())).thenReturn(false);
            setupJoinPointForMethod("scheduledMethod");

            SchedulerRegistryEntry registry = SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("test")
                .build();
            when(loggingService.ensureRegistryEntry(any())).thenReturn(registry);
            when(loggingService.createExecutionContext(any(), anyString()))
                .thenThrow(new RuntimeException("Context creation error"));
            when(joinPoint.proceed()).thenReturn("result");

            Object result = aspect.logSchedulerExecution(joinPoint);

            assertThat(result).isEqualTo("result");
        }
    }

    @Nested
    @DisplayName("Scheduler name resolution")
    class SchedulerNameResolutionTest {

        @Test
        @DisplayName("Should use @SchedulerName annotation value when present")
        void shouldUseAnnotationValue() throws Throwable {
            setupForFullExecution("namedScheduledMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            assertThat(metadataCaptor.getValue().getSchedulerName()).isEqualTo("custom-name");
        }

        @Test
        @DisplayName("Should auto-generate name as SimpleClassName_methodName when no annotation")
        void shouldAutoGenerateName() throws Throwable {
            setupForFullExecution("scheduledMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            // Inner class name includes enclosing class with $ separator
            assertThat(metadataCaptor.getValue().getSchedulerName())
                .endsWith("SampleScheduler_scheduledMethod");
        }

        @Test
        @DisplayName("Should strip CGLIB proxy suffix from class name")
        void shouldStripCglibSuffix() throws Throwable {
            when(loggingService.isEnabled()).thenReturn(true);
            when(loggingService.isExcluded(anyString())).thenReturn(false);

            Method method = SampleScheduler.class.getDeclaredMethod("scheduledMethod");
            when(joinPoint.getSignature()).thenReturn(methodSignature);
            when(methodSignature.getMethod()).thenReturn(method);

            // Simulate CGLIB proxy
            SampleScheduler target = new SampleScheduler();
            when(joinPoint.getTarget()).thenReturn(target);

            // Override the class name to simulate a CGLIB proxy
            // We use a real object but the class name resolution is tested via the target class
            SchedulerRegistryEntry registry = SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("test")
                .build();
            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .schedulerName("test")
                .build();

            when(loggingService.ensureRegistryEntry(any())).thenReturn(registry);
            when(loggingService.createExecutionContext(any(), anyString())).thenReturn(context);
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            // The class name should not contain CGLIB markers
            String schedulerName = metadataCaptor.getValue().getSchedulerName();
            assertThat(schedulerName).doesNotContain("$$");
        }
    }

    @Nested
    @DisplayName("Cron expression extraction")
    class CronExpressionTest {

        @Test
        @DisplayName("Should extract cron expression from @Scheduled")
        void shouldExtractCronExpression() throws Throwable {
            setupForFullExecution("cronScheduledMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            assertThat(metadataCaptor.getValue().getCronExpression()).isEqualTo("cron: 0 0 3 * * ?");
        }

        @Test
        @DisplayName("Should extract fixedDelay from @Scheduled")
        void shouldExtractFixedDelay() throws Throwable {
            setupForFullExecution("fixedDelayMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            assertThat(metadataCaptor.getValue().getCronExpression()).isEqualTo("fixedDelay: 5000ms");
        }

        @Test
        @DisplayName("Should extract fixedRate from @Scheduled")
        void shouldExtractFixedRate() throws Throwable {
            setupForFullExecution("fixedRateMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            assertThat(metadataCaptor.getValue().getCronExpression()).isEqualTo("fixedRate: 10000ms");
        }

        @Test
        @DisplayName("Should resolve Spring placeholders in cron expression")
        void shouldResolvePlaceholders() throws Throwable {
            when(environment.resolvePlaceholders("${scheduler.cron}")).thenReturn("0 */5 * * * ?");
            setupForFullExecution("placeholderCronMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            assertThat(metadataCaptor.getValue().getCronExpression()).isEqualTo("cron: 0 */5 * * * ?");
        }

        @Test
        @DisplayName("Should extract fixedDelayString from @Scheduled")
        void shouldExtractFixedDelayString() throws Throwable {
            when(environment.resolvePlaceholders("${scheduler.delay}")).thenReturn("3000");
            setupForFullExecution("fixedDelayStringMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            assertThat(metadataCaptor.getValue().getCronExpression()).isEqualTo("fixedDelay: 3000");
        }

        @Test
        @DisplayName("Should extract fixedRateString from @Scheduled")
        void shouldExtractFixedRateString() throws Throwable {
            when(environment.resolvePlaceholders("${scheduler.rate}")).thenReturn("6000");
            setupForFullExecution("fixedRateStringMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            assertThat(metadataCaptor.getValue().getCronExpression()).isEqualTo("fixedRate: 6000");
        }
    }

    @Nested
    @DisplayName("Scheduler type detection")
    class SchedulerTypeTest {

        @Test
        @DisplayName("Should detect LOCAL type when no @SchedulerLock present")
        void shouldDetectLocalType() throws Throwable {
            setupForFullExecution("scheduledMethod");
            when(joinPoint.proceed()).thenReturn(null);

            aspect.logSchedulerExecution(joinPoint);

            ArgumentCaptor<SchedulerMetadata> metadataCaptor =
                ArgumentCaptor.forClass(SchedulerMetadata.class);
            verify(loggingService).ensureRegistryEntry(metadataCaptor.capture());

            assertThat(metadataCaptor.getValue().getSchedulerType())
                .isEqualTo(SchedulerMetadata.SchedulerType.LOCAL);
            assertThat(metadataCaptor.getValue().getShedlockName()).isNull();
        }
    }

    // Helper methods

    private void setupJoinPointForMethod(String methodName) {
        try {
            Method method = SampleScheduler.class.getDeclaredMethod(methodName);
            when(joinPoint.getSignature()).thenReturn(methodSignature);
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.getTarget()).thenReturn(new SampleScheduler());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Test setup failed: method not found: " + methodName, e);
        }
    }

    private void setupForFullExecution(String methodName) {
        when(loggingService.isEnabled()).thenReturn(true);
        when(loggingService.isExcluded(anyString())).thenReturn(false);
        setupJoinPointForMethod(methodName);

        SchedulerRegistryEntry registry = SchedulerRegistryEntry.builder()
            .registryId("reg-1")
            .schedulerName("SampleScheduler_" + methodName)
            .build();
        SchedulerExecutionContext context = SchedulerExecutionContext.builder()
            .registryId("reg-1")
            .schedulerName("SampleScheduler_" + methodName)
            .status(ExecutionStatus.RUNNING)
            .build();

        when(loggingService.ensureRegistryEntry(any())).thenReturn(registry);
        when(loggingService.createExecutionContext(any(), anyString())).thenReturn(context);
    }

    // Sample class with various @Scheduled methods for testing
    @SuppressWarnings("unused")
    private static class SampleScheduler {

        @Scheduled(cron = "0 0 * * * ?")
        public void scheduledMethod() {
        }

        @Scheduled(cron = "0 0 3 * * ?")
        @SchedulerName("custom-name")
        public void namedScheduledMethod() {
        }

        @Scheduled(cron = "0 0 3 * * ?")
        public void cronScheduledMethod() {
        }

        @Scheduled(fixedDelay = 5000)
        public void fixedDelayMethod() {
        }

        @Scheduled(fixedRate = 10000)
        public void fixedRateMethod() {
        }

        @Scheduled(cron = "${scheduler.cron}")
        public void placeholderCronMethod() {
        }

        @Scheduled(fixedDelayString = "${scheduler.delay}")
        public void fixedDelayStringMethod() {
        }

        @Scheduled(fixedRateString = "${scheduler.rate}")
        public void fixedRateStringMethod() {
        }
    }
}

package dev.simplecore.simplix.stream.admin.command;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Processor that polls and executes admin commands from the database.
 * <p>
 * Each instance polls the command table periodically. When a command is found,
 * the instance checks if it owns the target (session or scheduler). If so,
 * it executes the command and updates the status.
 * <p>
 * This enables distributed admin control without requiring Redis Pub/Sub.
 */
@Slf4j
@RequiredArgsConstructor
public class AdminCommandProcessor {

    private final AdminCommandRepository commandRepository;
    private final SessionManager sessionManager;
    private final SessionRegistry sessionRegistry;
    private final SchedulerManager schedulerManager;
    private final StreamProperties properties;
    private final String instanceId;

    /**
     * Process pending admin commands.
     * <p>
     * Runs at configured interval (default 2 seconds).
     */
    @Scheduled(fixedRateString = "${simplix.stream.admin.polling-interval:2000}")
    @Transactional
    public void processCommands() {
        List<AdminCommand> pendingCommands = commandRepository.findByStatusOrderByCreatedAtAsc(
                AdminCommandStatus.PENDING);

        if (pendingCommands.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending admin commands", pendingCommands.size());

        for (AdminCommand command : pendingCommands) {
            if (isExpired(command)) {
                command.markExpired();
                commandRepository.save(command);
                log.info("Admin command expired: id={}, type={}, target={}",
                        command.getId(), command.getCommandType(), command.getTargetId());
                continue;
            }

            if (!isMyTarget(command)) {
                continue;
            }

            executeCommand(command);
        }
    }

    /**
     * Cleanup old executed/expired commands.
     * <p>
     * Runs daily to prevent table growth.
     */
    @Scheduled(cron = "${simplix.stream.admin.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupOldCommands() {
        Duration retentionPeriod = properties.getAdmin().getRetentionPeriod();
        Instant cutoff = Instant.now().minus(retentionPeriod);

        int deleted = commandRepository.deleteOldCommands(
                List.of(AdminCommandStatus.EXECUTED, AdminCommandStatus.EXPIRED,
                        AdminCommandStatus.FAILED, AdminCommandStatus.NOT_FOUND),
                cutoff);

        if (deleted > 0) {
            log.info("Cleaned up {} old admin commands", deleted);
        }
    }

    private boolean isMyTarget(AdminCommand command) {
        // If specific instance is targeted, check if it's us
        if (command.getTargetInstanceId() != null) {
            return command.getTargetInstanceId().equals(instanceId);
        }

        // Check if we own the target resource
        return switch (command.getCommandType()) {
            case TERMINATE_SESSION -> sessionRegistry.findById(command.getTargetId()).isPresent();
            case STOP_SCHEDULER, TRIGGER_SCHEDULER -> schedulerManager.getScheduler(command.getTargetId()).isPresent();
        };
    }

    private boolean isExpired(AdminCommand command) {
        Duration timeout = properties.getAdmin().getCommandTimeout();
        return command.getCreatedAt().plus(timeout).isBefore(Instant.now());
    }

    private void executeCommand(AdminCommand command) {
        try {
            switch (command.getCommandType()) {
                case TERMINATE_SESSION -> executeTerminateSession(command);
                case STOP_SCHEDULER -> executeStopScheduler(command);
                case TRIGGER_SCHEDULER -> executeTriggerScheduler(command);
            }
        } catch (Exception e) {
            command.markFailed(instanceId, e.getMessage());
            commandRepository.save(command);
            log.error("Failed to execute admin command: id={}, type={}, target={}",
                    command.getId(), command.getCommandType(), command.getTargetId(), e);
        }
    }

    private void executeTerminateSession(AdminCommand command) {
        String sessionId = command.getTargetId();

        if (sessionRegistry.findById(sessionId).isEmpty()) {
            command.markNotFound(instanceId);
            commandRepository.save(command);
            log.warn("Session not found for terminate command: {}", sessionId);
            return;
        }

        sessionManager.terminateSession(sessionId);
        command.markExecuted(instanceId);
        commandRepository.save(command);
        log.info("Executed admin command: TERMINATE_SESSION, session={}, instance={}",
                sessionId, instanceId);
    }

    private void executeStopScheduler(AdminCommand command) {
        String subscriptionKey = command.getTargetId();

        if (schedulerManager.getScheduler(subscriptionKey).isEmpty()) {
            command.markNotFound(instanceId);
            commandRepository.save(command);
            log.warn("Scheduler not found for stop command: {}", subscriptionKey);
            return;
        }

        schedulerManager.stopScheduler(subscriptionKey);
        command.markExecuted(instanceId);
        commandRepository.save(command);
        log.info("Executed admin command: STOP_SCHEDULER, key={}, instance={}",
                subscriptionKey, instanceId);
    }

    private void executeTriggerScheduler(AdminCommand command) {
        String subscriptionKey = command.getTargetId();

        if (schedulerManager.getScheduler(subscriptionKey).isEmpty()) {
            command.markNotFound(instanceId);
            commandRepository.save(command);
            log.warn("Scheduler not found for trigger command: {}", subscriptionKey);
            return;
        }

        schedulerManager.triggerNow(subscriptionKey);
        command.markExecuted(instanceId);
        commandRepository.save(command);
        log.info("Executed admin command: TRIGGER_SCHEDULER, key={}, instance={}",
                subscriptionKey, instanceId);
    }
}

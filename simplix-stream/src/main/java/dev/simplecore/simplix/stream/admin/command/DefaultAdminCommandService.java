package dev.simplecore.simplix.stream.admin.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Default implementation of AdminCommandService.
 * <p>
 * Persists admin commands to the database for distributed execution.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultAdminCommandService implements AdminCommandService {

    private final AdminCommandRepository commandRepository;
    private final boolean enabled;

    @Override
    @Transactional
    public AdminCommand queueCommand(AdminCommand command) {
        AdminCommand saved = commandRepository.save(command);
        log.info("Queued admin command: type={}, target={}, id={}",
                command.getCommandType(), command.getTargetId(), saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public AdminCommand queueTerminateSession(String sessionId) {
        return queueCommand(AdminCommand.terminateSession(sessionId));
    }

    @Override
    @Transactional
    public AdminCommand queueStopScheduler(String subscriptionKey) {
        return queueCommand(AdminCommand.stopScheduler(subscriptionKey));
    }

    @Override
    @Transactional
    public AdminCommand queueTriggerScheduler(String subscriptionKey) {
        return queueCommand(AdminCommand.triggerScheduler(subscriptionKey));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminCommand> getCommand(Long commandId) {
        return commandRepository.findById(commandId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminCommand> getCommandsForTarget(String targetId) {
        return commandRepository.findByTargetIdOrderByCreatedAtDesc(targetId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminCommand> getPendingCommands() {
        return commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

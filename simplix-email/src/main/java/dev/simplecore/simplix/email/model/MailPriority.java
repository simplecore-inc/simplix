package dev.simplecore.simplix.email.model;

/**
 * Mail priority levels.
 * <p>
 * Determines the order in which emails are processed when queued.
 */
public enum MailPriority {

    /**
     * Low priority - marketing emails, newsletters.
     */
    LOW(1),

    /**
     * Normal priority - standard transactional emails.
     */
    NORMAL(3),

    /**
     * High priority - important notifications.
     */
    HIGH(5),

    /**
     * Critical priority - security alerts, password resets.
     */
    CRITICAL(10);

    private final int weight;

    MailPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}

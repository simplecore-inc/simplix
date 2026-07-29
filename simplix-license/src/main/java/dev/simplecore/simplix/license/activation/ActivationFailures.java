package dev.simplecore.simplix.license.activation;

import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationOutcome;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;

/**
 * Turns a failed activation outcome into this product's error envelope.
 *
 * <p>Registration is reachable from two surfaces — the licensed application screen and the
 * first-run installer — and an operator must read the same reason from both. The message key
 * itself is the SDK's, so a deployment in any language names a refusal the same way; what is
 * decided here is only which HTTP shape this product answers with.
 */
public final class ActivationFailures {

    private ActivationFailures() {
    }

    /**
     * Asserts an attempt succeeded.
     *
     * @param outcome the outcome to check
     * @throws SimpliXGeneralException conflict carrying the reason, when the attempt failed
     */
    public static void require(ActivationOutcome outcome) {
        if (outcome.isSuccess()) {
            return;
        }
        throw new SimpliXGeneralException(ErrorCode.GEN_CONFLICT, messageKeyFor(outcome), null);
    }

    /**
     * @param outcome the failed outcome
     * @return the message key describing it to the operator, as a resolvable placeholder
     */
    public static String messageKeyFor(ActivationOutcome outcome) {
        String key = outcome.messageKey() != null
                ? outcome.messageKey()
                : "error.license.serverError";
        return "{" + key + "}";
    }
}

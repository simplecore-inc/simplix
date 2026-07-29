package dev.simplecore.simplix.license.enforcement;

import dev.simplecore.simplix.license.core.LicenseAuditTrail;
import dev.accesscore.license.sdk.gate.LicenseGate;
import dev.accesscore.license.sdk.spi.LicenseSpi.QuotaCounter;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Refuses registrations that would take a deployment past a licensed ceiling.
 *
 * <p>Only new registrations are refused. A contract that shrinks below what is already
 * installed leaves the installed equipment working — stopping access control because a license
 * changed would leave people standing at locked doors. The excess is reported on the license
 * screen and in the audit trail instead.
 *
 * <p>Which quotas exist is a property of the product, not of the SDK, so the guard works from
 * the codes the contributed counters declare. That same set is what the deployment reports to
 * the issuer, which is how a ceiling that was sold but is counted by nothing becomes visible in
 * the back office rather than only here.
 *
 * <p>A code the license does not name is unlimited, and a ceiling of zero is zero. Both are the
 * SDK's rule, read through the gate.
 */
public class LicenseQuotaGuard {

    private static final Logger log = LoggerFactory.getLogger(LicenseQuotaGuard.class);

    private final LicenseGate gate;
    private final List<QuotaCounter> counters;
    private final LicenseAuditTrail auditTrail;
    private final Set<String> countedQuotaCodes;

    /**
     * @param gate the source of the current ceilings
     * @param counters the application-contributed counters, one per area
     * @param auditTrail where refusals are recorded
     */
    public LicenseQuotaGuard(LicenseGate gate, List<QuotaCounter> counters,
                             LicenseAuditTrail auditTrail) {
        this.gate = gate;
        this.counters = counters;
        this.auditTrail = auditTrail;
        this.countedQuotaCodes = new LinkedHashSet<>();
        for (QuotaCounter counter : counters) {
            countedQuotaCodes.addAll(counter.countedQuotaCodes());
        }
        if (countedQuotaCodes.isEmpty()) {
            log.warn("No counter answers for any quota; no ceiling is enforced");
        }
    }

    /**
     * Asserts one more of the given kind fits.
     *
     * @param quotaCode the quota to check
     * @throws SimpliXGeneralException when the ceiling is already reached
     */
    public void require(String quotaCode) {
        require(quotaCode, 1);
    }

    /**
     * Asserts the given number more fit. Derivation paths create several rows at once — a
     * controller model that brings eight reader ports has to check eight places, not one.
     *
     * <p>Registering nothing is always allowed. A caller that reconciles a collection down to
     * zero new rows is not a new registration, and a deployment already past its ceiling has to
     * keep operating.
     *
     * @param quotaCode the quota to check
     * @param additional how many are about to be created
     * @throws SimpliXGeneralException when they would not fit
     */
    public void require(String quotaCode, long additional) {
        if (additional <= 0) {
            return;
        }
        Long limit = gate.state().limitFor(quotaCode);
        if (limit == null) {
            return;
        }
        QuotaCounter counter = counterFor(quotaCode);
        if (counter == null) {
            return;
        }
        long current = counter.count(quotaCode);
        if (current + additional > limit) {
            log.warn("Licensed {} limit reached: limit={}", quotaCode, limit);
            auditTrail.limitReached(quotaCode, current, limit);
            throw new SimpliXGeneralException(ErrorCode.GEN_CONFLICT,
                    "{error.license.quotaReached}", null);
        }
    }

    /**
     * The current count for one quota.
     *
     * @param quotaCode the quota to count
     * @return the count, or zero when nothing answers for the quota
     */
    public long currentCount(String quotaCode) {
        QuotaCounter counter = counterFor(quotaCode);
        return counter != null ? counter.count(quotaCode) : 0L;
    }

    /**
     * Every quota some counter answers for, with its current count. The license screen shows
     * this beside the ceilings, which is how an operator sees a contract that shrank below what
     * is already installed.
     *
     * <p>A quota nothing answers for is left out rather than reported as zero, so the screen
     * does not state a count it cannot know.
     *
     * @return the quota code to current count
     */
    public Map<String, Long> currentUsage() {
        Map<String, Long> usage = new LinkedHashMap<>();
        for (String quotaCode : countedQuotaCodes) {
            QuotaCounter counter = counterFor(quotaCode);
            if (counter != null) {
                usage.put(quotaCode, counter.count(quotaCode));
            }
        }
        return usage;
    }

    /**
     * @return the quota codes this deployment can count, reported to the license server so the
     *         issuer can tell a sold ceiling from an enforced one
     */
    public List<String> countedQuotaCodes() {
        return List.copyOf(countedQuotaCodes);
    }

    /**
     * @param quotaCode the quota to look up
     * @return the counter answering for it, or null when none does
     */
    private QuotaCounter counterFor(String quotaCode) {
        return counters.stream().filter(c -> c.supports(quotaCode)).findFirst().orElse(null);
    }
}

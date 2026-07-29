package dev.simplecore.simplix.license.enforcement;

import dev.accesscore.license.sdk.adapter.RequiresFeature;
import dev.accesscore.license.sdk.gate.LicenseGate;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Enforces {@link RequiresFeature} on this application's beans.
 *
 * <p>Registered as a bean by the license auto-configuration rather than by component scan, so
 * the aspect exists exactly when licensing does.
 *
 * <p>It refuses through this product's own exception type rather than the SDK's, because a
 * refusal here has to reach the same handler every other authorization failure does and come
 * back in the same response envelope.
 *
 * <p>One of the independent verification points in the enforcement architecture — filter,
 * aspect, scheduler, and health indicator each read the same judgement.
 */
@Aspect
public class FeatureGateAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureGateAspect.class);

    private final LicenseGate gate;

    /**
     * @param gate the gate every enforcement point reads
     */
    public FeatureGateAspect(LicenseGate gate) {
        this.gate = gate;
    }

    /**
     * Checks {@link RequiresFeature} at both class and method level.
     *
     * @param joinPoint the call being made
     */
    @Before("@within(dev.accesscore.license.sdk.adapter.RequiresFeature) || "
            + "@annotation(dev.accesscore.license.sdk.adapter.RequiresFeature)")
    public void checkFeature(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        // A method annotation wins over a class one, so a controller gated as a whole can still
        // hold one endpoint that needs a different feature.
        RequiresFeature methodAnnotation = method.getAnnotation(RequiresFeature.class);
        if (methodAnnotation != null) {
            enforceFeature(methodAnnotation.value(), method);
            return;
        }

        RequiresFeature classAnnotation =
                joinPoint.getTarget().getClass().getAnnotation(RequiresFeature.class);
        if (classAnnotation != null) {
            enforceFeature(classAnnotation.value(), method);
        }
    }

    /**
     * @param feature the feature key the call needs
     * @param method the call being made
     */
    private void enforceFeature(String feature, Method method) {
        if (!gate.isLicensed(feature)) {
            log.warn("Feature gate denied: feature={}, method={}", feature, method.getName());
            throw new SimpliXGeneralException(ErrorCode.AUTHZ_ACCESS_DENIED,
                    "{error.system.featureNotLicensed}", null);
        }
    }
}

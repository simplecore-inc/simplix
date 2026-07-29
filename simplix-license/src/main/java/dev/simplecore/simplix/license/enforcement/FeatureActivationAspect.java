package dev.simplecore.simplix.license.enforcement;

import dev.accesscore.license.sdk.spi.LicenseSpi.FeatureActivationChecker;
import dev.accesscore.license.sdk.adapter.RequiresFeature;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Gates admin-toggleable feature modules on their activation state, on top of the
 * license check performed by the app-license {@code FeatureGateAspect}. An
 * endpoint reaches its handler only when the license permits its feature AND the
 * feature is effectively active (see {@link FeatureActivationChecker}).
 */
@Aspect
@Component
public class FeatureActivationAspect {

    private final FeatureActivationChecker activationChecker;

    public FeatureActivationAspect(FeatureActivationChecker activationChecker) {
        this.activationChecker = activationChecker;
    }

    /**
     * Denies access when a required feature is not effectively active.
     *
     * @param joinPoint the intercepted method call
     */
    @Before("@within(dev.accesscore.license.sdk.adapter.RequiresFeature) || "
            + "@annotation(dev.accesscore.license.sdk.adapter.RequiresFeature)")
    public void checkActivation(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        RequiresFeature annotation = method.getAnnotation(RequiresFeature.class);
        if (annotation == null) {
            annotation = joinPoint.getTarget().getClass().getAnnotation(RequiresFeature.class);
        }
        if (annotation == null) {
            return;
        }

        if (!activationChecker.isEffectivelyActive(annotation.value())) {
            throw new SimpliXGeneralException(
                    ErrorCode.AUTHZ_ACCESS_DENIED,
                    "{error.system.featureNotActive}",
                    null);
        }
    }
}

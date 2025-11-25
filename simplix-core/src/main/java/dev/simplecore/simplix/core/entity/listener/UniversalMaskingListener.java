package dev.simplecore.simplix.core.entity.listener;

import dev.simplecore.simplix.core.security.sanitization.DataMaskingUtils;
import dev.simplecore.simplix.core.security.sanitization.LogMasker;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

/**
 * Universal JPA Entity Listener that automatically masks fields annotated with @MaskSensitive.
 * This listener can be applied to any entity that needs data masking.
 * <p>
 * Usage:
 * <pre>
 * @Entity
 * @EntityListeners(UniversalMaskingListener.class)
 * public class SomeEntity {
 *     @MaskSensitive(type = MaskType.PAYMENT_TOKEN)
 *     private String paymentId;
 * }
 * </pre>
 */
@Slf4j
public class UniversalMaskingListener {


    @PrePersist
    @PreUpdate
    public void maskSensitiveFields(Object entity) {
        if (entity == null) {
            return;
        }

        try {
            processEntity(entity);
        } catch (Exception e) {
            log.error("Failed to mask sensitive fields for entity: {}", entity.getClass().getSimpleName(), e);
        }
    }

    private void processEntity(Object entity) throws IllegalAccessException {
        Class<?> clazz = entity.getClass();

        // Process all fields including inherited ones
        while (clazz != null && !clazz.equals(Object.class)) {
            for (Field field : clazz.getDeclaredFields()) {
                MaskSensitive annotation = field.getAnnotation(MaskSensitive.class);
                if (annotation != null && annotation.enabled()) {
                    maskField(entity, field, annotation);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private void maskField(Object entity, Field field, MaskSensitive annotation) throws IllegalAccessException {
        field.setAccessible(true);
        Object value = field.get(entity);

        if (!(value instanceof String stringValue)) {
            return;
        }

		// Check minimum length threshold
        if (stringValue.length() < annotation.minLength()) {
            return;
        }

        String maskedValue = applyMasking(stringValue, annotation);
        if (!stringValue.equals(maskedValue)) {
            field.set(entity, maskedValue);
            log.debug("Masked field '{}' in entity '{}'", field.getName(), entity.getClass().getSimpleName());
        }
    }

    private String applyMasking(String value, MaskSensitive annotation) {
        if (value == null || value.isEmpty()) {
            return value;
        }

		return switch (annotation.type()) {
			case FULL -> DataMaskingUtils.maskFull(value, 50);
			case PARTIAL -> DataMaskingUtils.maskGeneric(value, annotation.keepFirst(), annotation.keepLast());
			case EMAIL -> DataMaskingUtils.maskEmail(value);
			case PHONE -> DataMaskingUtils.maskPhoneNumber(value);
			case CREDIT_CARD -> DataMaskingUtils.maskCreditCard(value);
			case PAYMENT_TOKEN -> DataMaskingUtils.maskPaymentToken(value);
			case IP_ADDRESS -> DataMaskingUtils.maskIpAddress(value);
			case JSON -> LogMasker.maskSensitiveData(value);
			default -> value;
		};
    }

}
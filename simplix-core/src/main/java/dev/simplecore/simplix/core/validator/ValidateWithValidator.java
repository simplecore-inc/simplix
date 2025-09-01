package dev.simplecore.simplix.core.validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;

@Component
public class ValidateWithValidator implements ConstraintValidator<ValidateWith, Object> {

    @Autowired
    private ApplicationContext context;

    private String servicePath;

    @Override
    public void initialize(ValidateWith annotation) {
        this.servicePath = annotation.service();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) return true;

        try {
            String[] parts = servicePath.split("\\.");
            Object service = this.context.getBean(parts[0]);
            Method method = service.getClass().getMethod(parts[1], value.getClass());
            return (Boolean) method.invoke(service, value);
        } catch (Exception e) {
            return false;
        }
    }
}
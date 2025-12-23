package dev.simplecore.simplix.core.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

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
            Class<?> targetClass = AopUtils.getTargetClass(service);

            Method method = findCompatibleMethod(targetClass, parts[1], value.getClass());
            if (method == null) {
                return false;
            }

            return (Boolean) method.invoke(service, value);
        } catch (Exception e) {
            return false;
        }
    }

    private Method findCompatibleMethod(Class<?> clazz, String methodName, Class<?> paramType) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }

            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length == 1 && paramTypes[0].isAssignableFrom(paramType)) {
                return method;
            }
        }
        return null;
    }
}
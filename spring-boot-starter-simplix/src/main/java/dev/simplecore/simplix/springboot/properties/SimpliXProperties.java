package dev.simplecore.simplix.springboot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "simplix")
public class SimpliXProperties {
    private CoreProperties core = new CoreProperties();
    private ResponseViewProperties responseView = new ResponseViewProperties();
    private ExceptionHandlerProperties exceptionHandler = new ExceptionHandlerProperties();

    @Data
    public static class CoreProperties {
        private boolean enabled = true;
    }

    @Data
    public static class ResponseViewProperties {
        private boolean enabled = true;
    }

    @Data
    public static class ExceptionHandlerProperties {
        private boolean enabled = true;
    }

    @Data
    public static class MessageSourceProperties {
        private boolean enabled = true;
        private String basename = "messages,validation";
        private String encoding = "UTF-8";
        private int cacheDuration = 3600;
        private boolean useCodeAsDefaultMessage = false;
        private boolean fallbackToSystemLocale = true;
    }

    private MessageSourceProperties messageSource = new MessageSourceProperties();

    @Data
    public static class ValidatorProperties {
        private boolean enabled = true;
        private String providerClassName = "org.hibernate.validator.HibernateValidator";
    }

    private ValidatorProperties validator = new ValidatorProperties();
} 
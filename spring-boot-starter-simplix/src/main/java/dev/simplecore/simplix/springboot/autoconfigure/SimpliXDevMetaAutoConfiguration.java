package dev.simplecore.simplix.springboot.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.web.meta.SimpliXMetaContributor;
import dev.simplecore.simplix.web.meta.SimpliXMetaDevController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Set;

/**
 * Registers the DTO meta dev endpoint. Three gates, all must open: servlet web app,
 * {@code simplix.dev.meta.enabled=true}, and no production profile active.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "simplix.dev.meta.enabled", havingValue = "true")
@Conditional(SimpliXDevMetaAutoConfiguration.NotProductionCondition.class)
@EnableConfigurationProperties(SimpliXDevProperties.class)
public class SimpliXDevMetaAutoConfiguration {

    static class NotProductionCondition implements Condition {
        private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            for (String profile : context.getEnvironment().getActiveProfiles()) {
                if (PRODUCTION_PROFILES.contains(profile)) return false;
            }
            return true;
        }
    }

    @Bean
    public SimpliXMetaDevController simpliXMetaDevController(
            RequestMappingHandlerMapping requestMappingHandlerMapping,
            ObjectMapper objectMapper,
            ObjectProvider<SimpliXMetaContributor> contributors) {
        List<SimpliXMetaContributor> resolved = contributors.orderedStream().toList();
        return new SimpliXMetaDevController(requestMappingHandlerMapping, objectMapper, resolved);
    }
}

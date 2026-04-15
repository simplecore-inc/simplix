package dev.simplecore.simplix.egov;

import dev.simplecore.simplix.egov.autoconfigure.SimpliXEgovAutoConfiguration;
import dev.simplecore.simplix.egov.config.SimpliXEgovProperties;
import dev.simplecore.simplix.egov.config.SimpliXTraceHandler;
import org.egovframe.rte.fdl.cmmn.trace.LeaveaTrace;
import org.egovframe.rte.fdl.cmmn.trace.manager.DefaultTraceHandleManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliX eGov Auto Configuration Tests")
class SimpliXEgovAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SimpliXEgovAutoConfiguration.class));

    @Test
    @DisplayName("Should register all eGov beans when active")
    void shouldRegisterAllEgovBeansWhenActive() {
        contextRunner
            .run(context -> {
                assertThat(context).hasSingleBean(LeaveaTrace.class);
                assertThat(context).hasSingleBean(SimpliXTraceHandler.class);
                assertThat(context).hasSingleBean(DefaultTraceHandleManager.class);
                assertThat(context).hasSingleBean(SimpliXEgovProperties.class);
            });
    }

    @Test
    @DisplayName("Should not register beans when disabled via property")
    void shouldNotRegisterBeansWhenDisabled() {
        contextRunner
            .withPropertyValues("simplix.egov.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(LeaveaTrace.class);
                assertThat(context).doesNotHaveBean(SimpliXTraceHandler.class);
                assertThat(context).doesNotHaveBean(DefaultTraceHandleManager.class);
            });
    }

    @Test
    @DisplayName("Should allow user-provided SimpliXTraceHandler to override default")
    void shouldAllowUserProvidedTraceHandler() {
        contextRunner
            .withUserConfiguration(CustomTraceHandlerConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(SimpliXTraceHandler.class);
                SimpliXTraceHandler handler = context.getBean(SimpliXTraceHandler.class);
                assertThat(handler).isInstanceOf(CustomSimliXTraceHandler.class);
            });
    }

    @Test
    @DisplayName("Should register LeaveaTrace bean with name 'leaveaTrace' for eGovFrame @Resource compatibility")
    void shouldRegisterLeaveaTraceWithCorrectBeanName() {
        contextRunner
            .run(context -> {
                assertThat(context.containsBean("leaveaTrace")).isTrue();
                Object bean = context.getBean("leaveaTrace");
                assertThat(bean).isInstanceOf(LeaveaTrace.class);
            });
    }

    @Test
    @DisplayName("Should not register AntPathMatcher as a bean")
    void shouldNotRegisterAntPathMatcherAsBean() {
        contextRunner
            .run(context -> {
                assertThat(context).doesNotHaveBean(AntPathMatcher.class);
            });
    }

    @Configuration
    static class CustomTraceHandlerConfig {
        @Bean
        public SimpliXTraceHandler simplixTraceHandler() {
            return new CustomSimliXTraceHandler();
        }
    }

    static class CustomSimliXTraceHandler extends SimpliXTraceHandler {
        @Override
        public void todo(Class<?> clazz, String message) {
            // Custom implementation for testing
        }
    }
}

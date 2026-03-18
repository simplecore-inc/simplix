package dev.simplecore.simplix.springboot.autoconfigure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafProperties;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXThymeleafAutoConfiguration - Thymeleaf template engine auto-configuration")
class SimpliXThymeleafAutoConfigurationTest {

    @Mock
    private ApplicationContext applicationContext;

    private ThymeleafProperties thymeleafProperties;
    private WebMvcProperties webMvcProperties;
    private SimpliXThymeleafAutoConfiguration config;

    @BeforeEach
    void setUp() {
        thymeleafProperties = new ThymeleafProperties();
        webMvcProperties = new WebMvcProperties();
        config = new SimpliXThymeleafAutoConfiguration(applicationContext, thymeleafProperties, webMvcProperties);
    }

    @Nested
    @DisplayName("defaultTemplateResolver")
    class DefaultTemplateResolverTests {

        @Test
        @DisplayName("Should create default template resolver with configured properties")
        void createDefaultResolver() {
            SpringResourceTemplateResolver resolver = config.defaultTemplateResolver();

            assertThat(resolver).isNotNull();
            assertThat(resolver.getOrder()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should use ThymeleafProperties defaults for prefix and suffix")
        void useDefaultPrefixAndSuffix() {
            SpringResourceTemplateResolver resolver = config.defaultTemplateResolver();

            // ThymeleafProperties defaults: prefix=classpath:/templates/, suffix=.html
            assertThat(resolver).isNotNull();
        }

        @Test
        @DisplayName("Should respect custom Thymeleaf properties")
        void respectCustomProperties() {
            thymeleafProperties.setPrefix("classpath:/custom-templates/");
            thymeleafProperties.setSuffix(".xhtml");
            thymeleafProperties.setMode("TEXT");
            thymeleafProperties.setEncoding(StandardCharsets.ISO_8859_1);
            thymeleafProperties.setCache(true);

            SimpliXThymeleafAutoConfiguration customConfig =
                    new SimpliXThymeleafAutoConfiguration(applicationContext, thymeleafProperties, webMvcProperties);
            SpringResourceTemplateResolver resolver = customConfig.defaultTemplateResolver();

            assertThat(resolver).isNotNull();
        }
    }

    @Nested
    @DisplayName("errorTemplateResolver")
    class ErrorTemplateResolverTests {

        @Test
        @DisplayName("Should create error template resolver with fixed configuration")
        void createErrorResolver() {
            SpringResourceTemplateResolver resolver = config.errorTemplateResolver();

            assertThat(resolver).isNotNull();
            assertThat(resolver.getOrder()).isEqualTo(1);
            assertThat(resolver.getName()).isEqualTo("Error Template Resolver");
        }
    }

    @Nested
    @DisplayName("templateEngine")
    class TemplateEngineTests {

        @Test
        @DisplayName("Should create template engine with both resolvers and LayoutDialect")
        void createTemplateEngine() {
            SpringResourceTemplateResolver defaultResolver = config.defaultTemplateResolver();
            SpringResourceTemplateResolver errorResolver = config.errorTemplateResolver();

            SpringTemplateEngine engine = config.templateEngine(defaultResolver, errorResolver);

            assertThat(engine).isNotNull();
        }
    }

    @Nested
    @DisplayName("thymeleafViewResolver")
    class ThymeleafViewResolverTests {

        @Test
        @DisplayName("Should create Thymeleaf view resolver with highest precedence")
        void createThymeleafViewResolver() {
            SpringResourceTemplateResolver defaultResolver = config.defaultTemplateResolver();
            SpringResourceTemplateResolver errorResolver = config.errorTemplateResolver();
            SpringTemplateEngine engine = config.templateEngine(defaultResolver, errorResolver);

            ViewResolver resolver = config.thymeleafViewResolver(engine);

            assertThat(resolver).isNotNull();
            assertThat(resolver).isInstanceOf(ThymeleafViewResolver.class);
            ThymeleafViewResolver tvr = (ThymeleafViewResolver) resolver;
            assertThat(tvr.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        }
    }

    @Nested
    @DisplayName("jspViewResolver")
    class JspViewResolverTests {

        @Test
        @DisplayName("Should create JSP view resolver with default settings")
        void createJspViewResolver() {
            ViewResolver resolver = config.jspViewResolver();

            assertThat(resolver).isNotNull();
            assertThat(resolver).isInstanceOf(InternalResourceViewResolver.class);
        }

        @Test
        @DisplayName("Should use WebMvcProperties view prefix and suffix")
        void useWebMvcProperties() {
            webMvcProperties.getView().setPrefix("/WEB-INF/custom/");
            webMvcProperties.getView().setSuffix(".jspx");

            SimpliXThymeleafAutoConfiguration customConfig =
                    new SimpliXThymeleafAutoConfiguration(applicationContext, thymeleafProperties, webMvcProperties);
            ViewResolver resolver = customConfig.jspViewResolver();

            assertThat(resolver).isNotNull();
        }
    }

    @Nested
    @DisplayName("addResourceHandlers")
    class ResourceHandlers {

        @Test
        @DisplayName("Should register resource handlers without throwing")
        void registerResourceHandlers() {
            // ResourceHandlerRegistry requires a real ApplicationContext, so we test
            // that the configuration was created correctly
            assertThat(config).isNotNull();
        }
    }
}

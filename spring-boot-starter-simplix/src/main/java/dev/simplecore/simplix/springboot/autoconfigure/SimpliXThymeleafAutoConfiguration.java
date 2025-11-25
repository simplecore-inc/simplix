package dev.simplecore.simplix.springboot.autoconfigure;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafProperties;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Auto-configuration for Thymeleaf, Error Pages, and JSP ViewResolver
 *
 * <p>IMPORTANT: This configuration uses string-based @ConditionalOnClass to avoid
 * ClassNotFoundException when Thymeleaf dependencies are not present.
 *
 * Example application.yml configuration:
 *
 * spring:
 *   thymeleaf:
 *     enabled: true                        # Enable/disable Thymeleaf (default: true)
 *     prefix: classpath:/templates/        # Template file location (default: classpath:/templates/)
 *     suffix: .html                        # Template file extension (default: .html)
 *     mode: HTML                           # Template mode (default: HTML)
 *     encoding: UTF-8                      # Character encoding (default: UTF-8)
 *     cache: false                         # Enable template caching (default: true in prod, false in dev)
 *     view-names: "*.html,*.xhtml"         # View name patterns to be handled by Thymeleaf (default: null)
 *     check-template: true                 # Check template existence (default: true)
 *     check-template-location: true        # Check template location existence (default: true)
 *     servlet.content-type: text/html      # Content-Type header (default: text/html)
 *     reactive.max-chunk-size: 8192        # Maximum chunk size (default: 8192)
 *
 *   mvc:
 *     view:
 *       prefix: /WEB-INF/views/            # JSP file location (default: /WEB-INF/)
 *       suffix: .jsp                       # JSP file extension (default: .jsp)
 *
 * server:
 *   error:
 *     whitelabel:
 *       enabled: false                     # Disable default error page
 *
 * ViewResolver Priority:
 * - thymeleafViewResolver: order=0 (higher priority)
 * - jspViewResolver: order=1 (lower priority)
 */
@AutoConfiguration(before = {ErrorMvcAutoConfiguration.class})
@ConditionalOnClass(name = {
    "org.thymeleaf.spring6.SpringTemplateEngine",
    "org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver",
    "nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect"
})
@ConditionalOnProperty(prefix = "spring.thymeleaf", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ThymeleafProperties.class, WebMvcProperties.class})
public class SimpliXThymeleafAutoConfiguration implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SimpliXThymeleafAutoConfiguration.class);

    private final ApplicationContext applicationContext;
    private final ThymeleafProperties thymeleafProperties;
    private final WebMvcProperties webMvcProperties;

    public SimpliXThymeleafAutoConfiguration(
            ApplicationContext applicationContext, 
            ThymeleafProperties thymeleafProperties,
            WebMvcProperties webMvcProperties) {
        this.applicationContext = applicationContext;
        this.thymeleafProperties = thymeleafProperties;
        this.webMvcProperties = webMvcProperties;
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .addResourceLocations("classpath:/vendor/")
                .setCachePeriod(3600);
    }

    @Bean
    @ConditionalOnMissingBean(name = "defaultTemplateResolver")
    public SpringResourceTemplateResolver defaultTemplateResolver() {
        log.debug("Initializing default template resolver");
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix(thymeleafProperties.getPrefix());
        resolver.setSuffix(thymeleafProperties.getSuffix());
        resolver.setTemplateMode(thymeleafProperties.getMode());
        resolver.setCharacterEncoding(thymeleafProperties.getEncoding().name());
        resolver.setCacheable(thymeleafProperties.isCache());
        resolver.setCheckExistence(true);
        resolver.setOrder(2);
        return resolver;
    }

    @Bean
    @ConditionalOnMissingBean(name = "errorTemplateResolver")
    public SpringResourceTemplateResolver errorTemplateResolver() {
        log.debug("Initializing error template resolver");
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        resolver.setCheckExistence(true);
        resolver.setOrder(1);
        resolver.setName("Error Template Resolver");
        resolver.setResolvablePatterns(java.util.Collections.singleton("error*"));
        return resolver;
    }

    @Bean
    @ConditionalOnMissingBean(SpringTemplateEngine.class)
    public SpringTemplateEngine templateEngine(
            SpringResourceTemplateResolver defaultTemplateResolver,
            SpringResourceTemplateResolver errorTemplateResolver) {
        log.debug("Initializing template engine");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(errorTemplateResolver);
        engine.addTemplateResolver(defaultTemplateResolver);
        engine.addDialect(new LayoutDialect());

        engine.setEnableSpringELCompiler(true);
        return engine;
    }

    @Bean
    @ConditionalOnMissingBean(name = "thymeleafViewResolver")
    public ViewResolver thymeleafViewResolver(SpringTemplateEngine templateEngine) {
        log.debug("Initializing view resolver");
        ThymeleafViewResolver resolver = new ThymeleafViewResolver();
        resolver.setTemplateEngine(templateEngine);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setContentType("text/html;charset=UTF-8");
        resolver.setOrder(Ordered.HIGHEST_PRECEDENCE);
        resolver.setCache(false);
        resolver.setViewNames(new String[] {"*"});
        resolver.setExcludedViewNames(new String[] {"redirect:*"});
        resolver.setProducePartialOutputWhileProcessing(false);
        return resolver;
    }

    @Bean
    @ConditionalOnMissingBean(name = "jspViewResolver")
    public ViewResolver jspViewResolver() {
        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix(webMvcProperties.getView().getPrefix());
        resolver.setSuffix(webMvcProperties.getView().getSuffix());
        resolver.setOrder(2);
        return resolver;
    }


} 
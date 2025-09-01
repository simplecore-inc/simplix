package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import dev.simplecore.simplix.core.tree.factory.TreeRepositoryFactoryBean;
import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AliasFor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import java.lang.annotation.*;

/**
 * Auto-configuration for SimpliX Tree Repository support.
 * 
 * <p>This configuration automatically provides tree repository functionality when:
 * <ul>
 *   <li>TreeRepositoryFactoryBean is on the classpath</li>
 *   <li>JPA repositories are enabled</li>
 *   <li>A DataSource is available</li>
 * </ul>
 * 
 * <p>Configure in application.yml:
 * <pre>
 * simplix:
 *   tree-repository:
 *     enabled: true                                    # Enable/disable tree repository (default: true)
 * </pre>
 * 
 * <p>Usage example:
 * <pre>{@code
 * // 1. Entity configuration
 * @Entity
 * @TreeEntityAttributes(
 *     tableName = "categories",
 *     idColumn = "id",
 *     parentIdColumn = "parent_id",
 *     sortOrderColumn = "sort_order"
 * )
 * public class Category implements TreeEntity<Category, Long> {
 *     // Entity implementation
 * }
 * 
 * // 2. Repository interface
 * public interface CategoryRepository extends SimpliXTreeRepository<Category, Long> {
 *     // Additional custom methods if needed
 * }
 * 
 * // 3. Main application class - Option 1: Auto-scan (recommended)
 * @SpringBootApplication
 * @EnableSimplixTreeRepositories
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * 
 * // 3. Main application class - Option 2: Specific packages
 * @SpringBootApplication
 * @EnableSimplixTreeRepositories(basePackages = "com.example.domain")
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 * 
 * <p>The auto-configuration will:
 * <ul>
 *   <li>Automatically enable JPA repositories with TreeRepositoryFactoryBean</li>
 *   <li>Provide JdbcTemplate bean if not already present</li>
 *   <li>Scan specified base packages for tree repositories</li>
 * </ul>
 */
@Configuration
@AutoConfiguration(after = JpaRepositoriesAutoConfiguration.class)
@ConditionalOnClass({TreeRepositoryFactoryBean.class, SimpliXTreeRepository.class, TreeEntity.class})
@ConditionalOnProperty(prefix = "simplix.tree-repository", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXTreeRepositoryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpliXTreeRepositoryAutoConfiguration.class);

    @Bean
    @ConfigurationProperties(prefix = "simplix.tree-repository")
    public TreeRepositoryProperties treeRepositoryProperties() {
        return new TreeRepositoryProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate jdbcTemplate(DataSourceProperties dataSourceProperties) {
        log.info("Initializing JdbcTemplate for SimpliX Tree Repository...");
        return new JdbcTemplate(dataSourceProperties.initializeDataSourceBuilder().build());
    }

    /**
     * Annotation to enable SimpliX Tree Repository support.
     * This is a convenience annotation that wraps @EnableJpaRepositories with TreeRepositoryFactoryBean.
     * 
     * Usage:
     * <pre>{@code
     * @SpringBootApplication
     * @EnableSimplixTreeRepositories
     * public class Application {
     *     // ...
     * }
     * }</pre>
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @EnableJpaRepositories(repositoryFactoryBeanClass = TreeRepositoryFactoryBean.class)
    public @interface EnableSimplixTreeRepositories {
        
        /**
         * Base packages to scan for tree repositories.
         * If not specified, will scan from the package of the annotated class.
         */
        @AliasFor(annotation = EnableJpaRepositories.class, attribute = "value")
        String[] value() default {};
        
        /**
         * Base packages to scan for tree repositories.
         * If not specified, will scan from the package of the annotated class.
         */
        @AliasFor(annotation = EnableJpaRepositories.class, attribute = "basePackages")
        String[] basePackages() default {};
        
        /**
         * Type-safe alternative to basePackages for specifying the packages to scan.
         */
        @AliasFor(annotation = EnableJpaRepositories.class, attribute = "basePackageClasses")
        Class<?>[] basePackageClasses() default {};
        
        /**
         * Whether to consider nested repositories.
         */
        @AliasFor(annotation = EnableJpaRepositories.class, attribute = "considerNestedRepositories")
        boolean considerNestedRepositories() default false;
        
        /**
         * Whether to enable default transactions for repository methods.
         */
        @AliasFor(annotation = EnableJpaRepositories.class, attribute = "enableDefaultTransactions")
        boolean enableDefaultTransactions() default true;
        
        /**
         * Specifies which types are eligible for component scanning.
         */
        @AliasFor(annotation = EnableJpaRepositories.class, attribute = "includeFilters")
        org.springframework.context.annotation.ComponentScan.Filter[] includeFilters() default {};
        
        /**
         * Specifies which types are not eligible for component scanning.
         */
        @AliasFor(annotation = EnableJpaRepositories.class, attribute = "excludeFilters")
        org.springframework.context.annotation.ComponentScan.Filter[] excludeFilters() default {};
        
        /**
         * Returns the postfix to be used when looking up custom repository implementations.
         */
        @AliasFor(annotation = EnableJpaRepositories.class, attribute = "repositoryImplementationPostfix")
        String repositoryImplementationPostfix() default "Impl";
        
        /**
         * Configures the name of the EntityManagerFactory bean definition to be used.
         */
        @AliasFor(annotation = EnableJpaRepositories.class, attribute = "entityManagerFactoryRef")
        String entityManagerFactoryRef() default "entityManagerFactory";
        
        /**
         * Configures the name of the PlatformTransactionManager bean definition to be used.
         */
        @AliasFor(annotation = EnableJpaRepositories.class, attribute = "transactionManagerRef")
        String transactionManagerRef() default "transactionManager";
    }

    /**
     * Configuration properties for tree repository functionality.
     */
    public static class TreeRepositoryProperties {
        
        /**
         * Whether tree repository functionality is enabled.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
} 
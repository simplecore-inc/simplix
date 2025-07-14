package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import dev.simplecore.simplix.core.tree.factory.TreeRepositoryFactoryBean;
import dev.simplecore.simplix.core.tree.repository.TreeRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

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
 *     enabled: true                        # Enable/disable tree repository (default: true)
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
 * public interface CategoryRepository extends TreeRepository<Category, Long> {
 *     // Additional custom methods if needed
 * }
 * 
 * // 3. Configuration class (required for tree repositories)
 * @Configuration
 * @EnableJpaRepositories(
 *     repositoryFactoryBeanClass = TreeRepositoryFactoryBean.class,
 *     basePackages = "your.package.repository"
 * )
 * public class JpaConfig {
 *     // Additional configuration if needed
 * }
 * 
 * // 4. Main application class
 * @SpringBootApplication
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 * 
 * <p>The auto-configuration will:
 * <ul>
 *   <li>Provide JdbcTemplate bean if not already present</li>
 *   <li>Configure tree repository properties</li>
 *   <li>Work alongside existing JPA repository configurations</li>
 * </ul>
 * 
 * <p><strong>Important:</strong> You must manually configure @EnableJpaRepositories 
 * with TreeRepositoryFactoryBean to use tree repositories. This auto-configuration 
 * only provides supporting beans and does not interfere with your repository scanning.
 */
@Configuration
@AutoConfiguration(after = JpaRepositoriesAutoConfiguration.class)
@ConditionalOnClass({TreeRepositoryFactoryBean.class, TreeRepository.class, TreeEntity.class})
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
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        log.info("Initializing JdbcTemplate for SimpliX Tree Repository...");
        return new JdbcTemplate(dataSource);
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
package dev.simplecore.simplix.core.tree.factory;

import dev.simplecore.simplix.core.tree.annotation.LookupColumn;
import dev.simplecore.simplix.core.tree.annotation.TreeEntityAttributes;
import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import dev.simplecore.simplix.core.tree.repository.TreeRepositoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

import javax.persistence.EntityManager;
import javax.persistence.Table;
import java.io.Serializable;

/**
 * Factory bean for creating tree structure repositories.
 * This factory bean creates repositories that can handle hierarchical data structures
 * with support for various databases and automatic query optimization.
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Automatic repository creation for tree structure entities</li>
 *   <li>Database-specific query optimization</li>
 *   <li>Support for custom table and column names</li>
 *   <li>Dynamic lookup capabilities</li>
 *   <li>Flexible configuration through annotations</li>
 * </ul>
 *
 * <h2>Setup Instructions:</h2>
 * 
 * 1. Configuration in Spring Boot application:
 * <pre>{@code
 * @Configuration
 * @EnableJpaRepositories(
 *     repositoryFactoryBeanClass = TreeRepositoryFactoryBean.class,
 *     basePackages = "com.your.package"
 * )
 * public class JpaConfig {
 *     @Bean
 *     public JdbcTemplate jdbcTemplate(DataSource dataSource) {
 *         return new JdbcTemplate(dataSource);
 *     }
 * }
 * }</pre>
 *
 * 2. Entity configuration:
 * <pre>{@code
 * @Entity
 * @TreeEntityAttributes(
 *     tableName = "items",
 *     idColumn = "item_id",
 *     parentIdColumn = "parent_id",
 *     sortOrderColumn = "sort_order",
 *     lookupColumns = {
 *         @LookupColumn(name = "code", type = ColumnType.STRING),
 *         @LookupColumn(name = "active", type = ColumnType.BOOLEAN)
 *     }
 * )
 * public class Item implements TreeEntity<Item, Long> {
 *     // Entity implementation
 * }
 * }</pre>
 *
 * 3. Repository interface:
 * <pre>{@code
 * public interface ItemRepository extends TreeRepository<Item, Long> {
 *     // Additional custom methods if needed
 * }
 * }</pre>
 *
 * <h2>Supported Databases:</h2>
 * <ul>
 *   <li>PostgreSQL - Uses recursive CTEs</li>
 *   <li>MySQL/MariaDB - Uses recursive CTEs</li>
 *   <li>Oracle - Uses CONNECT BY</li>
 *   <li>SQL Server - Uses recursive CTEs</li>
 *   <li>H2 - Uses recursive CTEs</li>
 * </ul>
 *
 * <h2>Additional Notes:</h2>
 * <ul>
 *   <li>Table and column names can be inferred from JPA annotations if not specified</li>
 *   <li>Supports automatic fallback to in-memory hierarchy building if needed</li>
 *   <li>Provides optimized queries for each supported database</li>
 *   <li>Handles circular references in hierarchical data</li>
 * </ul>
 *
 * @param <R> The repository type
 * @param <T> The entity type that implements TreeEntity
 * @param <ID> The type of the entity's identifier
 */
public class TreeRepositoryFactoryBean<R extends JpaRepository<T, ID>, T extends TreeEntity<T, ID>, ID extends Serializable>
        extends JpaRepositoryFactoryBean<R, T, ID> {

    private JdbcTemplate jdbcTemplate;

    public TreeRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }

    @Autowired
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @NonNull
    protected RepositoryFactorySupport createRepositoryFactory(@NonNull EntityManager entityManager) {
        return new TreeRepositoryFactory<>(entityManager, jdbcTemplate);
    }

    /**
     * Internal factory class that creates the actual repository instances.
     * Handles the creation and configuration of TreeRepositoryImpl instances.
     */
    private static class TreeRepositoryFactory<T extends TreeEntity<T, ID>, ID extends Serializable>
            extends JpaRepositoryFactory {

        private final JdbcTemplate jdbcTemplate;

        public TreeRepositoryFactory(EntityManager entityManager, JdbcTemplate jdbcTemplate) {
            super(entityManager);
            this.jdbcTemplate = jdbcTemplate;
        }

        /**
         * Creates a repository instance with the appropriate configuration.
         * Extracts metadata from annotations and creates a properly configured TreeRepositoryImpl.
         */
        @Override
        @NonNull
        protected SimpleJpaRepository<?, ?> getTargetRepository(@NonNull RepositoryInformation information, @NonNull EntityManager entityManager) {
            @SuppressWarnings("unchecked")
            Class<T> domainClass = (Class<T>) information.getDomainType();
            JpaEntityInformation<T, ?> entityInformation = getEntityInformation(domainClass);
            @SuppressWarnings("unchecked")
            JpaEntityInformation<T, ID> typedEntityInformation = (JpaEntityInformation<T, ID>) entityInformation;
            
            String tableName = null;
            String idColumn = null;
            String parentIdColumn = null;
            String sortOrderColumn = null;
            LookupColumn[] lookupColumns = null;

            // Extract configuration from @TreeEntityAttributes
            TreeEntityAttributes attributes = domainClass.getAnnotation(TreeEntityAttributes.class);
            if (attributes != null) {
                tableName = attributes.tableName();
                idColumn = attributes.idColumn();
                parentIdColumn = attributes.parentIdColumn();
                sortOrderColumn = attributes.sortOrderColumn();
                lookupColumns = attributes.lookupColumns();
            }
            
            // Fall back to @Table annotation if table name not specified
            if (tableName == null || tableName.isEmpty()) {
                Table table = domainClass.getAnnotation(Table.class);
                if (table != null && !table.name().isEmpty()) {
                    tableName = table.name();
                } else {
                    tableName = camelToSnake(domainClass.getSimpleName());
                }
            }
            
            // Set default column names if not specified
            if (idColumn == null || idColumn.isEmpty()) {
                idColumn = "id";
            }
            if (parentIdColumn == null || parentIdColumn.isEmpty()) {
                parentIdColumn = "parent_id";
            }
            if (sortOrderColumn == null || sortOrderColumn.isEmpty()) {
                sortOrderColumn = "sort_order";
            }
            if (lookupColumns == null) {
                lookupColumns = new LookupColumn[0];
            }

            return new TreeRepositoryImpl<>(
                typedEntityInformation,
                entityManager,
                jdbcTemplate,
                tableName,
                idColumn,
                parentIdColumn,
                sortOrderColumn,
                lookupColumns
            );
        }

        @Override
        @NonNull
        protected Class<?> getRepositoryBaseClass(@NonNull RepositoryMetadata metadata) {
            return TreeRepositoryImpl.class;
        }

        /**
         * Utility method to convert camelCase class names to snake_case table names.
         */
        private String camelToSnake(String str) {
            if (str == null || str.isEmpty()) {
                return str;
            }
            StringBuilder result = new StringBuilder();
            result.append(Character.toLowerCase(str.charAt(0)));
            for (int i = 1; i < str.length(); i++) {
                char ch = str.charAt(i);
                if (Character.isUpperCase(ch)) {
                    result.append('_');
                    result.append(Character.toLowerCase(ch));
                } else {
                    result.append(ch);
                }
            }
            return result.toString();
        }
    }
} 
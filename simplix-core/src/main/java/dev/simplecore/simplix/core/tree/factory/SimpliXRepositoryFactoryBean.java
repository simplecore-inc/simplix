package dev.simplecore.simplix.core.tree.factory;

import dev.simplecore.simplix.core.tree.annotation.LookupColumn;
import dev.simplecore.simplix.core.tree.annotation.TreeEntityAttributes;
import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepository;
import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepositoryImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

import java.io.Serializable;

/**
 * Smart factory bean that automatically handles both standard JPA repositories and tree structure repositories.
 * This factory bean intelligently detects the repository type and creates the appropriate implementation.
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Automatic detection of tree vs standard repositories</li>
 *   <li>No need for separate @EnableJpaRepositories configurations</li>
 *   <li>Supports all standard JPA repository features</li>
 *   <li>Enhanced tree repository capabilities when needed</li>
 *   <li>Database-specific query optimization for tree structures</li>
 * </ul>
 *
 * <h2>Setup Instructions:</h2>
 *
 * <pre>{@code
 * @Configuration
 * @EnableJpaRepositories(
 *     repositoryFactoryBeanClass = SimpliXRepositoryFactoryBean.class,
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
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * // Standard JPA Repository - works as usual
 * public interface UserRepository extends JpaRepository<User, Long> {
 *     List<User> findByUsername(String username);
 * }
 *
 * // Tree Repository - automatically enhanced with tree functionality
 * @Entity
 * @TreeEntityAttributes(
 *     tableName = "categories",
 *     parentIdColumn = "parent_id"
 * )
 * public class Category implements TreeEntity<Category, Long> {
 *     // Entity implementation
 * }
 *
 * public interface CategoryRepository extends SimpliXTreeRepository<Category, Long> {
 *     // Inherits tree-specific methods automatically
 * }
 * }</pre>
 *
 * <h2>How Detection Works:</h2>
 * The factory automatically detects tree repositories by checking:
 * <ul>
 *   <li>If the repository interface extends SimpliXTreeRepository</li>
 *   <li>If the entity implements TreeEntity interface</li>
 * </ul>
 * If either condition is met, tree repository functionality is enabled.
 * Otherwise, standard JPA repository implementation is used.
 *
 * <h2>Benefits over separate configurations:</h2>
 * <ul>
 *   <li>No need for excludeFilters in @EnableJpaRepositories</li>
 *   <li>No need for separate @EnableSimplixTreeRepositories annotation</li>
 *   <li>Single, unified configuration for all repository types</li>
 *   <li>Automatic and transparent - just extend the appropriate interface</li>
 * </ul>
 *
 * @param <R> The repository type
 * @param <T> The entity type
 * @param <ID> The type of the entity's identifier
 */
public class SimpliXRepositoryFactoryBean<R extends JpaRepository<T, ID>, T, ID extends Serializable>
        extends JpaRepositoryFactoryBean<R, T, ID> {

    private JdbcTemplate jdbcTemplate;

    public SimpliXRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }

    @Autowired(required = false)
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @NonNull
    protected RepositoryFactorySupport createRepositoryFactory(@NonNull EntityManager entityManager) {
        return new SimpliXRepositoryFactory(entityManager, jdbcTemplate);
    }

    /**
     * Smart repository factory that automatically detects and creates the appropriate repository implementation.
     */
    private static class SimpliXRepositoryFactory extends JpaRepositoryFactory {

        private final JdbcTemplate jdbcTemplate;

        public SimpliXRepositoryFactory(EntityManager entityManager, JdbcTemplate jdbcTemplate) {
            super(entityManager);
            this.jdbcTemplate = jdbcTemplate;
        }

        /**
         * Creates a repository instance with automatic type detection.
         * - If repository extends SimpliXTreeRepository or entity implements TreeEntity: creates tree repository
         * - Otherwise: creates standard JPA repository
         */
        @Override
        @NonNull
        protected JpaRepositoryImplementation<?, ?> getTargetRepository(@NonNull RepositoryInformation information, @NonNull EntityManager entityManager) {
            Class<?> domainClass = information.getDomainType();

            // Check if this is a tree repository
            boolean isTreeRepository = SimpliXTreeRepository.class.isAssignableFrom(information.getRepositoryInterface());
            boolean isTreeEntity = TreeEntity.class.isAssignableFrom(domainClass);

            // If not a tree repository, delegate to standard JPA repository creation
            if (!isTreeRepository && !isTreeEntity) {
                return super.getTargetRepository(information, entityManager);
            }

            // Create tree repository with full configuration
            return createTreeRepository(information, entityManager, domainClass);
        }

        /**
         * Creates a tree repository instance with proper configuration extracted from annotations.
         */
        @SuppressWarnings("unchecked")
        private <T extends TreeEntity<T, ID>, ID extends Serializable> SimpliXTreeRepositoryImpl<T, ID> createTreeRepository(
                RepositoryInformation information,
                EntityManager entityManager,
                Class<?> domainClass) {

            Class<T> typedDomainClass = (Class<T>) domainClass;
            JpaEntityInformation<T, ?> entityInformation = getEntityInformation(typedDomainClass);
            JpaEntityInformation<T, ID> typedEntityInformation = (JpaEntityInformation<T, ID>) entityInformation;

            String tableName = null;
            String idColumn = null;
            String parentIdColumn = null;
            String sortOrderColumn = null;
            LookupColumn[] lookupColumns = null;

            // Extract configuration from @TreeEntityAttributes
            TreeEntityAttributes attributes = typedDomainClass.getAnnotation(TreeEntityAttributes.class);
            if (attributes != null) {
                tableName = attributes.tableName();
                idColumn = attributes.idColumn();
                parentIdColumn = attributes.parentIdColumn();
                sortOrderColumn = attributes.sortOrderColumn();
                lookupColumns = attributes.lookupColumns();
            }

            // Fall back to @Table annotation if table name not specified
            if (tableName == null || tableName.isEmpty()) {
                Table table = typedDomainClass.getAnnotation(Table.class);
                if (table != null && !table.name().isEmpty()) {
                    tableName = table.name();
                } else {
                    tableName = camelToSnake(typedDomainClass.getSimpleName());
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

            return new SimpliXTreeRepositoryImpl<>(
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
            // Determine base class based on repository type
            boolean isTreeRepository = SimpliXTreeRepository.class.isAssignableFrom(metadata.getRepositoryInterface());
            boolean isTreeEntity = TreeEntity.class.isAssignableFrom(metadata.getDomainType());

            return (isTreeRepository || isTreeEntity) ? SimpliXTreeRepositoryImpl.class : SimpleJpaRepository.class;
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
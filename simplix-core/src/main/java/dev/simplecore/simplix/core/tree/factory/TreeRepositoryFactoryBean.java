package dev.simplecore.simplix.core.tree.factory;

import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.io.Serializable;

/**
 * Factory bean for creating tree structure repositories.
 *
 * @param <R> The repository type
 * @param <T> The entity type that implements TreeEntity
 * @param <ID> The type of the entity's identifier
 *
 * @deprecated Use {@link SimpliXRepositoryFactoryBean} instead.
 * SimpliXRepositoryFactoryBean provides the same functionality with automatic detection
 * of tree vs standard repositories, eliminating the need for separate configurations.
 *
 * <p>Migration example:
 * <pre>{@code
 * // Old approach (deprecated):
 * @EnableJpaRepositories(
 *     repositoryFactoryBeanClass = TreeRepositoryFactoryBean.class,
 *     basePackages = "com.your.package"
 * )
 *
 * // New approach (recommended):
 * @EnableJpaRepositories(
 *     repositoryFactoryBeanClass = SimpliXRepositoryFactoryBean.class,
 *     basePackages = "com.your.package"
 * )
 * }</pre>
 *
 * <p>The new SimpliXRepositoryFactoryBean automatically handles both tree repositories
 * and standard JPA repositories without requiring separate configurations or exclude filters.
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public class TreeRepositoryFactoryBean<R extends JpaRepository<T, ID>, T extends TreeEntity<T, ID>, ID extends Serializable>
        extends SimpliXRepositoryFactoryBean<R, T, ID> {

    public TreeRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }
} 
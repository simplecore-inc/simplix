package dev.simplecore.simplix.core.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.hibernate.annotations.Filter;
import org.springframework.data.jpa.provider.PersistenceProvider;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Base repository implementation that applies the entity's enabled Hibernate session filters to a
 * read by id, the way Hibernate applies them to every query.
 * <p>
 * {@link SimpleJpaRepository#findById} loads through {@code EntityManager.find}, which Hibernate
 * session filters never reach, while {@code existsById}, {@code findAllById}, {@code findAll} and
 * every search run as queries and are filtered. A soft-deleted row hidden by a {@code @Filter} is
 * therefore missing from the list and from {@code existsById} but still answered by
 * {@code findById}, and so by every detail, edit and update read built on it. This class closes
 * that gap: when the entity, or a mapped superclass of it, declares a {@code @Filter} that is
 * enabled in the current session, the row found by id is returned only if the same id also passes
 * a filtered existence query. The filter's own condition and parameters decide, so a filter
 * switched to "deleted rows only" hides the active rows instead, and a disabled filter hides
 * nothing.
 * <p>
 * Hibernate's own {@code @FilterDef(applyToLoadByKey = true)} is not used for this: it also applies
 * the filter to to-one associations and throws {@code EntityFilterException} when an association
 * points at a filtered row, so a row that refers to a soft-deleted parent could no longer be read.
 * Associations are left as they are here.
 * <p>
 * {@code getReferenceById} returns an uninitialised proxy and is not judged.
 *
 * @param <T> the entity type
 * @param <ID> the identifier type
 */
@Transactional(readOnly = true)
public class SimpliXJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityManager entityManager;
    private final List<String> filterNames;
    private final String existsQuery;

    public SimpliXJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
        this.filterNames = declaredFilterNames(entityInformation.getJavaType());
        this.existsQuery = filterNames.isEmpty() ? null : QueryUtils.getExistsQueryString(
                entityInformation.getEntityName(),
                PersistenceProvider.fromEntityManager(entityManager).getCountQueryPlaceholder(),
                entityInformation.getIdAttributeNames());
    }

    @Override
    @NonNull
    public Optional<T> findById(@NonNull ID id) {
        Optional<T> found = super.findById(id);
        if (found.isEmpty() || !anyFilterEnabled()) {
            return found;
        }
        return passesEnabledFilters(id) ? found : Optional.empty();
    }

    /** The names of the session filters the entity type and its superclasses declare. */
    static List<String> declaredFilterNames(Class<?> entityType) {
        List<String> names = new ArrayList<>();
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Filter filter : type.getDeclaredAnnotationsByType(Filter.class)) {
                if (!names.contains(filter.name())) {
                    names.add(filter.name());
                }
            }
        }
        return List.copyOf(names);
    }

    private boolean anyFilterEnabled() {
        if (filterNames.isEmpty()) {
            return false;
        }
        Session session = entityManager.unwrap(Session.class);
        for (String name : filterNames) {
            if (session.getEnabledFilter(name) != null) {
                return true;
            }
        }
        return false;
    }

    /** Runs the existence query Spring Data's {@code existsById} runs, which the filters reach. */
    private boolean passesEnabledFilters(ID id) {
        TypedQuery<Long> query = entityManager.createQuery(existsQuery, Long.class);
        Iterable<String> idAttributeNames = entityInformation.getIdAttributeNames();
        if (entityInformation.hasCompositeId()) {
            for (String name : idAttributeNames) {
                query.setParameter(name, entityInformation.getCompositeIdAttributeValue(id, name));
            }
        } else {
            query.setParameter(idAttributeNames.iterator().next(), id);
        }
        Long count = query.getSingleResult();
        return count != null && count > 0;
    }
}

package dev.simplecore.simplix.web.service;

import com.github.thkwag.searchable.core.service.SearchableService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SimpliXService<E, ID> extends SearchableService<E> {
    
//    E save(Object saveDto);
//
//    List<E> saveAll(Iterable<?> saveDtos);
//
//    <P> P save(Object saveDto, Class<P> projection);
//
//    <P> List<P> saveAll(Iterable<?> saveDtos, Class<P> projection);
    Optional<E> findById(ID id);

    <P> Optional<P> findById(ID id, Class<P> projection);

    List<E> findAllById(Iterable<ID> ids);

    <P> List<P> findAllById(Iterable<ID> ids, Class<P> projection);

    Page<E> findAll(Pageable pageable);

    <P> Page<P> findAll(Pageable pageable, Class<P> projection);

    <P> List<P> findAll(Class<P> projection);

    Boolean existsById(ID id);

    void deleteById(ID id);

    void delete(E entity);

    void deleteAll(Iterable<? extends E> entities);

    void deleteAllByIds(Iterable<ID> ids);

    // void deleteAll();

    List<? extends E> saveAll(Iterable<? extends E> entities);

    E saveAndFlush(E entity);

    /**
     * Check if the entity has the specified permission
     * @param permission Permission string to check
     * @param id Entity ID
     * @param dto Data transfer object
     * @return true if has permission, false otherwise
     */
    boolean hasOwnerPermission(String permission, ID id, Object dto);

    /**
     * Check if the entity has the specified permission
     * @param permission Permission string to check
     * @param id Entity ID
     * @return true if has permission, false otherwise
     */
    default boolean hasPermission(String permission, ID id) {
        return hasOwnerPermission(permission, id, null);
    }
} 
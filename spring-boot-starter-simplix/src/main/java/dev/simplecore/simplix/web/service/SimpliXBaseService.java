package dev.simplecore.simplix.web.service;

import dev.simplecore.simplix.web.repository.SimpliXBaseRepository;
import dev.simplecore.searchable.core.service.DefaultSearchableService;
import dev.simplecore.searchable.core.condition.SearchCondition;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
public abstract class SimpliXBaseService<E, ID> extends DefaultSearchableService<E, ID> implements SimpliXService<E, ID> {
    protected final SimpliXBaseRepository<E, ID> repository;
    protected final EntityManager entityManager;
    
    @Autowired
    protected ModelMapper modelMapper;

    protected SimpliXBaseService(SimpliXBaseRepository<E, ID> repository, EntityManager entityManager) {
        super(repository, entityManager);
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<E> findById(ID id) {
        return repository.findById(id);
    }

    @Override
    public <P> Optional<P> findById(ID id, Class<P> projection) {
        return repository.findById(id).map(entity -> modelMapper.map(entity, projection));
    }

    @Override
    public List<E> findAllById(Iterable<ID> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public <P> List<P> findAllById(Iterable<ID> ids, Class<P> projection) {
        return repository.findAllById(ids).stream()
            .map(entity -> modelMapper.map(entity, projection))
            .collect(Collectors.toList());
    }

    @Override
    public Page<E> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Override
    public <P> Page<P> findAll(Pageable pageable, Class<P> projection) {
        return repository.findAll(pageable)
            .map(entity -> modelMapper.map(entity, projection));
    }

    @Override
    public <P> List<P> findAll(Class<P> projection) {
        return repository.findAll().stream()
            .map(entity -> modelMapper.map(entity, projection))
            .collect(Collectors.toList());
    }

    @Override
    public Boolean existsById(ID id) {
        return repository.existsById(id);
    }

    @Override
    @Transactional
    public void deleteById(ID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteAllByIds(Iterable<ID> ids) {
        ids.forEach(this::deleteById);
    }

    @Override
    @Transactional
    public void delete(E entity) {
        repository.delete(entity);
    }

    @Override
    @Transactional
    public void deleteAll(Iterable<? extends E> entities) {
        repository.deleteAll(entities);
    }

    // @Override
    // @Transactional
    // public void deleteAll() {
    //     repository.deleteAll();
    // }

    @Override
    @Transactional
    public List<? extends E> saveAll(Iterable<? extends E> entities) {
        return repository.saveAll(entities);
    }

    @Override
    @Transactional
    public E saveAndFlush(E entity) {
        return repository.saveAndFlush(entity);
    }
    
    public Page<E> findAllWithSearch(SearchCondition<?> searchCondition) {
        return super.findAllWithSearch(searchCondition);
    }
    
    public <D> Page<D> findAllWithSearch(SearchCondition<?> searchCondition, Class<D> dtoClass) {
        return findAllWithSearch(searchCondition)
                .map(entity -> modelMapper.map(entity, dtoClass));
    }

    @SuppressWarnings({ "unchecked", "unused" })
    private Class<E> getEntityClass() {
        return (Class<E>) ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments()[0];
    }

} 
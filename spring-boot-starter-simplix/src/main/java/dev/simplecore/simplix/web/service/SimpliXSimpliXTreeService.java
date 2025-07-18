package dev.simplecore.simplix.web.service;

import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepository;
import dev.simplecore.simplix.core.tree.service.SimpliXTreeBaseService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public abstract class SimpliXSimpliXTreeService<E extends TreeEntity<E, ID>, ID> extends SimpliXTreeBaseService<E, ID> {

    @Autowired
    protected ModelMapper modelMapper;
    
    public SimpliXSimpliXTreeService(SimpliXTreeRepository<E, ID> simpliXTreeRepository) {
        super(simpliXTreeRepository);
    }
    
} 
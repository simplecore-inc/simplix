package dev.simplecore.simplix.web.service;

import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import dev.simplecore.simplix.core.tree.service.TreeBaseService;
import dev.simplecore.simplix.core.tree.repository.TreeRepository;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public abstract class SimpliXTreeService<E extends TreeEntity<E, ID>, ID> extends TreeBaseService<E, ID> {

    @Autowired
    protected ModelMapper modelMapper;
    
    public SimpliXTreeService(TreeRepository<E, ID> treeRepository) {
        super(treeRepository);
    }
    
} 
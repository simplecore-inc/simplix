package dev.simplecore.simplix.demo.domain.common.system.repository;


import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepository;
import dev.simplecore.simplix.demo.domain.common.system.entity.CodeItem;
import org.springframework.stereotype.Repository;

@Repository
public interface CodeItemTreeRepository extends SimpliXTreeRepository<CodeItem, String> {
} 
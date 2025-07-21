package dev.simplecore.simplix.demo.domain.common.code.repository;


import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepository;
import dev.simplecore.simplix.demo.domain.common.code.entity.CodeGroup;
import org.springframework.stereotype.Repository;

@Repository
public interface CodeGroupTreeRepository extends SimpliXTreeRepository<CodeGroup, String> {
} 
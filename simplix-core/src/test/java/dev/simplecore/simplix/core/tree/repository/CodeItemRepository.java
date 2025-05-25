package dev.simplecore.simplix.core.tree.repository;

import dev.simplecore.simplix.core.tree.entity.CodeItem;
import org.springframework.stereotype.Repository;

@Repository
public interface CodeItemRepository extends TreeRepository<CodeItem, Long> {
} 
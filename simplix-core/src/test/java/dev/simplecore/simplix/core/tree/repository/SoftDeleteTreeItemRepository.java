package dev.simplecore.simplix.core.tree.repository;

import dev.simplecore.simplix.core.tree.entity.SoftDeleteTreeItem;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftDeleteTreeItemRepository extends SimpliXTreeRepository<SoftDeleteTreeItem, Long> {
}

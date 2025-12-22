package dev.simplecore.simplix.core.tree.repository;

import dev.simplecore.simplix.core.tree.entity.CodeGroup;
import dev.simplecore.simplix.core.tree.entity.CodeItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Test configuration for JPA testing
@org.springframework.boot.autoconfigure.SpringBootApplication
@EntityScan(basePackages = "dev.simplecore.simplix.core.tree.entity")
@EnableJpaRepositories(
    basePackages = "dev.simplecore.simplix.core.tree.repository",
    repositoryFactoryBeanClass = dev.simplecore.simplix.core.tree.factory.SimpliXRepositoryFactoryBean.class
)
class TestApplication {
    
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

@org.springframework.boot.test.context.SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Tree Repository Test")
class SimpliXTreeRepositoryTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CodeItemRepository treeRepository;

    private CodeGroup codeGroup;
    private CodeItem root;

	@BeforeEach
    void setUp() {
        // Create code group
        codeGroup = new CodeGroup();
        codeGroup.setGroupKey("TEST_GROUP");
        codeGroup.setGroupName("Test Group");
        codeGroup.setDescription("Code group for testing");
        entityManager.persist(codeGroup);
        entityManager.flush();

        // Create root code item
        root = createCodeItem("ROOT", "Root", null, 1);
        entityManager.persist(root);
        entityManager.flush();

        // Create child code items
		CodeItem child1 = createCodeItem("CHILD1", "Child1", root.getId(), 1);
		CodeItem child2 = createCodeItem("CHILD2", "Child2", root.getId(), 2);
        entityManager.persist(child1);
        entityManager.persist(child2);
        entityManager.flush();

        // Create grandchild code item
		CodeItem grandChild1 = createCodeItem("GRANDCHILD1", "Grandchild1", child1.getId(), 1);
        entityManager.persist(grandChild1);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findDirectChildren - Retrieve only direct child nodes")
    void findDirectChildren() {
        // when
        List<CodeItem> children = treeRepository.findDirectChildren(root.getId());

        // then
        assertThat(children).hasSize(2)
            .extracting("codeKey")
            .containsExactly("CHILD1", "CHILD2");
    }

    @Test
    @DisplayName("findItemWithAllDescendants - Retrieve all descendant nodes")
    void findItemWithAllDescendants() {
        // when
        List<CodeItem> descendants = treeRepository.findItemWithAllDescendants(root.getId());

        // then
        assertThat(descendants).hasSize(4)
            .extracting("codeKey")
            .containsExactly("ROOT", "CHILD1", "GRANDCHILD1", "CHILD2");
    }

    @Test
    @DisplayName("findCompleteHierarchy - Retrieve entire hierarchy")
    void findCompleteHierarchy() {
        // when
        List<CodeItem> hierarchy = treeRepository.findCompleteHierarchy();

        // then
        assertThat(hierarchy).hasSize(1); // Only root node returned
        assertThat(hierarchy.get(0).getChildren()).hasSize(2); // 2 child nodes
        assertThat(hierarchy.get(0).getChildren().get(0).getChildren()).hasSize(1); // 1 grandchild node
    }

    @Test
    @DisplayName("findRootItems - Retrieve only root nodes")
    void findRootItems() {
        // when
        List<CodeItem> rootItems = treeRepository.findRootItems();

        // then
        assertThat(rootItems).hasSize(1)
            .extracting("codeKey")
            .containsExactly("ROOT");
    }

    @Test
    @DisplayName("countChildrenByParentId - Count direct children for each parent")
    void countChildrenByParentId() {
        // when
        List<Object[]> results = treeRepository.countChildrenByParentId();

        // then
        assertThat(results).isNotEmpty();

        // Find child count for root (should have 2 children)
        Long rootChildCount = results.stream()
            .filter(row -> root.getId().equals(row[0]))
            .map(row -> ((Number) row[1]).longValue())
            .findFirst()
            .orElse(0L);

        assertThat(rootChildCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("findRootItemsWithChildCount - Retrieve root items with child count in single query")
    void findRootItemsWithChildCount() {
        // when
        List<Object[]> results = treeRepository.findRootItemsWithChildCount();

        // then
        assertThat(results).hasSize(1);

        // First result should be the root node with child count
        Object[] rootResult = results.get(0);

        // Verify child count (root has 2 direct children)
        Number childCount = (Number) rootResult[rootResult.length - 1];
        assertThat(childCount.intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("findDirectChildrenWithChildCount - Retrieve children with child count in single query")
    void findDirectChildrenWithChildCount() {
        // when
        List<Object[]> results = treeRepository.findDirectChildrenWithChildCount(root.getId());

        // then
        assertThat(results).hasSize(2); // CHILD1 and CHILD2

        // CHILD1 should have 1 grandchild, CHILD2 should have 0
        int totalGrandchildren = results.stream()
            .mapToInt(row -> ((Number) row[row.length - 1]).intValue())
            .sum();

        assertThat(totalGrandchildren).isEqualTo(1); // Only GRANDCHILD1 under CHILD1
    }

    private CodeItem createCodeItem(String key, String value, Long parentId, int sortOrder) {
        CodeItem item = new CodeItem();
        item.setCodeGroup(codeGroup);
        item.setCodeKey(key);
        item.setCodeValue(value);
        item.setDescription(value + " Description");
        item.setSortOrder(sortOrder);
        item.setIsActive(true);
        item.setParentId(parentId);
        return item;
    }
} 
package dev.simplecore.simplix.core.tree.repository;

import dev.simplecore.simplix.core.tree.entity.CodeGroup;
import dev.simplecore.simplix.core.tree.entity.CodeItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 테스트용 Spring Boot Application 설정
@SpringBootApplication
@EntityScan(basePackages = "dev.simplecore.simplix.core.tree.entity")
@EnableJpaRepositories(
    basePackages = "dev.simplecore.simplix.core.tree.repository",
    repositoryFactoryBeanClass = dev.simplecore.simplix.core.tree.factory.TreeRepositoryFactoryBean.class
)
class TestApplication {
    
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
    }
    
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Tree Repository 테스트")
class TreeRepositoryTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CodeItemRepository treeRepository;

    private CodeGroup codeGroup;
    private CodeItem root;
    private CodeItem child1;
    private CodeItem child2;
    private CodeItem grandChild1;

    @BeforeEach
    void setUp() {
        // 코드 그룹 생성
        codeGroup = new CodeGroup();
        codeGroup.setGroupKey("TEST_GROUP");
        codeGroup.setGroupName("테스트 그룹");
        codeGroup.setDescription("테스트용 코드 그룹");
        entityManager.persist(codeGroup);
        entityManager.flush();

        // 루트 코드 항목 생성
        root = createCodeItem("ROOT", "루트", null, 1);
        entityManager.persist(root);
        entityManager.flush();

        // 자식 코드 항목 생성
        child1 = createCodeItem("CHILD1", "자식1", root.getId(), 1);
        child2 = createCodeItem("CHILD2", "자식2", root.getId(), 2);
        entityManager.persist(child1);
        entityManager.persist(child2);
        entityManager.flush();

        // 손자 코드 항목 생성
        grandChild1 = createCodeItem("GRANDCHILD1", "손자1", child1.getId(), 1);
        entityManager.persist(grandChild1);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findDirectChildren - 직계 자식 노드만 조회")
    void findDirectChildren() {
        // when
        List<CodeItem> children = treeRepository.findDirectChildren(root.getId());

        // then
        assertThat(children).hasSize(2)
            .extracting("codeKey")
            .containsExactly("CHILD1", "CHILD2");
    }

    @Test
    @DisplayName("findItemWithAllDescendants - 모든 하위 노드 조회")
    void findItemWithAllDescendants() {
        // when
        List<CodeItem> descendants = treeRepository.findItemWithAllDescendants(root.getId());

        // then
        assertThat(descendants).hasSize(4)
            .extracting("codeKey")
            .containsExactly("ROOT", "CHILD1", "GRANDCHILD1", "CHILD2");
    }

    @Test
    @DisplayName("findCompleteHierarchy - 전체 계층 구조 조회")
    void findCompleteHierarchy() {
        // when
        List<CodeItem> hierarchy = treeRepository.findCompleteHierarchy();

        // then
        assertThat(hierarchy).hasSize(1); // 루트 노드만 반환
        assertThat(hierarchy.get(0).getChildren()).hasSize(2); // 자식 노드 2개
        assertThat(hierarchy.get(0).getChildren().get(0).getChildren()).hasSize(1); // 손자 노드 1개
    }

    @Test
    @DisplayName("findRootItems - 루트 노드만 조회")
    void findRootItems() {
        // when
        List<CodeItem> rootItems = treeRepository.findRootItems();

        // then
        assertThat(rootItems).hasSize(1)
            .extracting("codeKey")
            .containsExactly("ROOT");
    }

    private CodeItem createCodeItem(String key, String value, Long parentId, int sortOrder) {
        CodeItem item = new CodeItem();
        item.setCodeGroup(codeGroup);
        item.setCodeKey(key);
        item.setCodeValue(value);
        item.setDescription(value + " 설명");
        item.setSortOrder(sortOrder);
        item.setIsActive(true);
        item.setParentId(parentId);
        return item;
    }
} 
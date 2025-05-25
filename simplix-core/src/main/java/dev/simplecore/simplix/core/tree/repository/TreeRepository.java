package dev.simplecore.simplix.core.tree.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import dev.simplecore.simplix.core.tree.entity.TreeEntity;

import java.util.List;
import java.util.Map;

/**
 * 트리 구조를 가진 엔티티를 위한 기본 리포지토리 인터페이스
 * @param <T> 엔티티 타입
 * @param <ID> ID 타입
 */
@NoRepositoryBean
public interface TreeRepository<T extends TreeEntity<T, ID>, ID> extends JpaRepository<T, ID> {
    /**
     * 전체 계층 구조를 조회
     */
    List<T> findCompleteHierarchy();
    
    /**
     * 특정 항목과 그 하위 항목들을 모두 조회
     */
    List<T> findItemWithAllDescendants(ID itemId);
    
    /**
     * 최상위 항목들을 조회
     */
    List<T> findRootItems();
    
    /**
     * 특정 항목의 직계 자식 항목들을 조회
     */
    List<T> findDirectChildren(ID parentId);
    
    /**
     * 메모리에서 계층 구조를 구성
     */
    List<T> buildHierarchy(List<T> allItems);

    /**
     * 추가 조회 조건으로 항목을 조회
     * @param parameters 조회 조건 (컬럼명: 값)
     */
    List<T> findByLookup(Map<String, String> parameters);
} 
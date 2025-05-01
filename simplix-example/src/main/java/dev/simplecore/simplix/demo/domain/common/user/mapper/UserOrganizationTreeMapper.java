package dev.simplecore.simplix.demo.domain.common.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import dev.simplecore.simplix.demo.web.common.user.dto.UserOrganizationDTOs.UserOrganizationTreeDTO;
import dev.simplecore.simplix.demo.web.common.user.dto.UserOrganizationDTOs.UserOrganizationMyBatisDTO;

@Mapper
public interface UserOrganizationTreeMapper {
    
    /**
     * 전체 조직 트리 조회
     */
    List<UserOrganizationTreeDTO> organizationTree(@Param("orgId") String orgId);
    
    /**
     * 특정 조직을 루트로 하는 서브트리 조회
     */
    List<UserOrganizationTreeDTO> organizationSubTree(@Param("orgId") String orgId);
    
    /**
     * 특정 조직의 모든 하위 조직 ID 목록 조회
     */
    List<String> findAllChildrenIds(@Param("orgId") String orgId);
    
    /**
     * 특정 조직의 모든 상위 조직 ID 목록 조회
     */
    List<String> findAllParentIds(@Param("orgId") String orgId);
    
    /**
     * 특정 조직의 직계 하위 조직 목록 조회
     */
    List<UserOrganizationMyBatisDTO> findDirectChildren(@Param("orgId") String orgId);
    
    /**
     * 특정 조직의 직계 상위 조직 조회
     */
    UserOrganizationMyBatisDTO findDirectParent(@Param("orgId") String orgId);
} 
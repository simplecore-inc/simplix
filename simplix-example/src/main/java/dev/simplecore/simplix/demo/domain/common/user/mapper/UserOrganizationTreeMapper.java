package dev.simplecore.simplix.demo.domain.common.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import dev.simplecore.simplix.demo.web.common.user.dto.UserOrganizationDTOs.UserOrganizationTreeDTO;
import dev.simplecore.simplix.demo.web.common.user.dto.UserOrganizationDTOs.UserOrganizationMyBatisDTO;

@Mapper
public interface UserOrganizationTreeMapper {
    
    /**
     * Retrieve the entire organization tree
     */
    List<UserOrganizationTreeDTO> organizationTree(@Param("orgId") String orgId);
    
    /**
     * Retrieve subtree with a specific organization as root
     */
    List<UserOrganizationTreeDTO> organizationSubTree(@Param("orgId") String orgId);
    
    /**
     * Retrieve all subordinate organization IDs for a specific organization
     */
    List<String> findAllChildrenIds(@Param("orgId") String orgId);
    
    /**
     * Retrieve all parent organization IDs for a specific organization
     */
    List<String> findAllParentIds(@Param("orgId") String orgId);
    
    /**
     * Retrieve direct subordinate organizations for a specific organization
     */
    List<UserOrganizationMyBatisDTO> findDirectChildren(@Param("orgId") String orgId);
    
    /**
     * Retrieve direct parent organization of a specific organization
     */
    UserOrganizationMyBatisDTO findDirectParent(@Param("orgId") String orgId);
} 
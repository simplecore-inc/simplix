package dev.simplecore.simplix.demo.permission;

import dev.simplecore.simplix.demo.domain.common.auth.entity.AuthRolePermission;
import dev.simplecore.simplix.demo.domain.common.auth.enums.PermissionTargetType;
import dev.simplecore.simplix.demo.domain.common.auth.repository.AuthRolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final AuthRolePermissionRepository rolePermissionRepository;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }

        if (targetDomainObject instanceof String) {
            return hasRolePermission(authentication, (String) targetDomainObject, permission.toString());
        }

        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        return false;
    }

    private boolean hasRolePermission(Authentication authentication, String permissionName, String actionType) {
        if (!(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return false;
        }

        if (authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .anyMatch(a -> a.equals("ADMIN"))) {
            return true;
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        List<String> roleIds = userDetails.getRoleIds();
        List<String> orgIds = userDetails.getOrganizationIds();

        List<AuthRolePermission> permissions = rolePermissionRepository.findPermissions(
                permissionName, actionType.toLowerCase(), roleIds, orgIds, userDetails.getId(),
                PermissionTargetType.ROLE,
                PermissionTargetType.ORGANIZATION,
                PermissionTargetType.USER
        );

        return !permissions.isEmpty();
    }
} 
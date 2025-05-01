package dev.simplecore.simplix.demo.permission;

import dev.simplecore.simplix.demo.domain.common.user.entity.UserAccount;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserOrganization;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserPosition;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class CustomUserDetails extends User {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String username;
    private final String realName;
    private final String email;
    private final Boolean enabled;
    
    private final String positionId;
    private final String positionName;
    
    private final List<String> roleIds;
    private final List<String> organizationIds;

    public CustomUserDetails(UserAccount account, Collection<? extends GrantedAuthority> authorities) {
        super(account.getUsername(), 
              account.getPassword(), 
              account.getEnabled(),
              true, true, true,
              authorities);
              
        this.id = account.getId();
        this.username = account.getUsername();
        this.realName = account.getRealName();
        this.email = account.getEmail();
        this.enabled = account.getEnabled();
        
        UserPosition position = account.getPosition();
        this.positionId = position != null ? position.getId() : null;
        this.positionName = position != null ? position.getName() : null;
        
        this.roleIds = account.getRoles().stream()
            .map(UserRole::getId)
            .collect(Collectors.toList());
        this.organizationIds = account.getOrganizations().stream()
            .map(UserOrganization::getId)
            .collect(Collectors.toList());
    }
}
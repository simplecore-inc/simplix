package dev.simplecore.simplix.auth.security;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collection;
import java.util.stream.Collectors;

public interface SimpliXUserDetailsService extends UserDetailsService {
    
    MessageSource getMessageSource();
    
    @Override
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
    
    /**
     * Check if user exists
     * @param username User identifier
     * @return boolean
     */
    default boolean exists(String username) {
        try {
            loadUserByUsername(username);
            return true;
        } catch (UsernameNotFoundException e) {
            return false;
        }
    }

    /**
     * Load UserDetails for currently authenticated user
     */
    default UserDetails loadCurrentUser(Authentication authentication) throws UsernameNotFoundException {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UsernameNotFoundException(
                getMessageSource().getMessage("user.auth.not.found", null, 
                    "No authenticated user found", 
                    LocaleContextHolder.getLocale())
            );
        }
        return loadUserByUsername(authentication.getName());
    }

    /**
     * Check if user account is locked
     */
    default boolean isAccountLocked(String username) {
        try {
            UserDetails user = loadUserByUsername(username);
            return !user.isAccountNonLocked();
        } catch (UsernameNotFoundException e) {
            return false;
        }
    }

    /**
     * Check if user account is expired
     */
    default boolean isAccountExpired(String username) {
        try {
            UserDetails user = loadUserByUsername(username);
            return !user.isAccountNonExpired();
        } catch (UsernameNotFoundException e) {
            return false;
        }
    }

    /**
     * Check if user credentials are expired
     */
    default boolean isCredentialsExpired(String username) {
        try {
            UserDetails user = loadUserByUsername(username);
            return !user.isCredentialsNonExpired();
        } catch (UsernameNotFoundException e) {
            return false;
        }
    }

    /**
     * Get list of user authorities
     */
    default Collection<String> getUserAuthorities(String username) {
        try {
            UserDetails user = loadUserByUsername(username);
            return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        } catch (UsernameNotFoundException e) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Check if user has specific authority
     */
    default boolean hasAuthority(String username, String authority) {
        return getUserAuthorities(username)
            .contains(authority);
    }

    /**
     * Check if user has specific role
     */
    default boolean hasRole(String username, String role) {
        String roleAuthority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return hasAuthority(username, roleAuthority);
    }

    /**
     * Check if user account is active (all status checks)
     */
    default boolean isActive(String username) {
        try {
            UserDetails user = loadUserByUsername(username);
            return user.isEnabled() &&
                   user.isAccountNonLocked() &&
                   user.isAccountNonExpired() &&
                   user.isCredentialsNonExpired();
        } catch (UsernameNotFoundException e) {
            return false;
        }
    }
} 
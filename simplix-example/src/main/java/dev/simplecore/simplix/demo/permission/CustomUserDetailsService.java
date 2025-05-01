package dev.simplecore.simplix.demo.permission;

import dev.simplecore.simplix.auth.security.SimpliXUserDetailsService;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserAccount;
import dev.simplecore.simplix.demo.domain.common.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Primary
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CustomUserDetailsService implements SimpliXUserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final MessageSource messageSource;

    @Override
    public MessageSource getMessageSource() {
        return messageSource;
    }

    @Override
    public CustomUserDetails loadUserByUsername(String username) {
        return userAccountRepository.findByUsername(username)
            .map(this::createUserDetails)
            .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    private CustomUserDetails createUserDetails(UserAccount account) {
        List<SimpleGrantedAuthority> authorities = account.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority(role.getRole()))
            .collect(Collectors.toList());

        return new CustomUserDetails(account, authorities);
    }

    @Override
    public boolean exists(String username) {
        return userAccountRepository.existsByUsername(username);
    }

    @Transactional
    public void updateAuthenticationPrincipal(String username) {
        CustomUserDetails userDetails = loadUserByUsername(username);
        Authentication newAuth = new UsernamePasswordAuthenticationToken(
            userDetails, 
            userDetails.getPassword(),
            userDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }
}

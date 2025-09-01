package dev.simplecore.simplix.demo.config;

import dev.simplecore.simplix.auth.security.SimpliXUserDetailsService;
import dev.simplecore.simplix.demo.permission.CustomUserDetailsService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class UserDetailsConfiguration {

    private final CustomUserDetailsService userDetailsService;

    public UserDetailsConfiguration(
        CustomUserDetailsService userDetailsService
    ) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SimpliXUserDetailsService userDetailsService() {
        return userDetailsService;
    }

} 
package dev.simplecore.simplix.auth.autoconfigure;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.security.SimpliXAccessDeniedHandler;
import dev.simplecore.simplix.auth.security.SimpliXAuthenticationEntryPoint;
import dev.simplecore.simplix.auth.security.SimpliXTokenAuthenticationFilter;
import dev.simplecore.simplix.auth.security.SimpliXUserDetailsService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@AutoConfiguration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "simplix.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXAuthSecurityConfiguration {

    private final SimpliXAuthProperties properties;
    private final SimpliXUserDetailsService userDetailsService;
    private final SimpliXTokenAuthenticationFilter tokenAuthenticationFilter;
    private final SimpliXAuthenticationEntryPoint authenticationEntryPoint;
    private final SimpliXAccessDeniedHandler accessDeniedHandler;

    public SimpliXAuthSecurityConfiguration(
            SimpliXAuthProperties properties,
            SimpliXUserDetailsService userDetailsService,
            SimpliXTokenAuthenticationFilter tokenAuthenticationFilter,
            SimpliXAuthenticationEntryPoint authenticationEntryPoint,
            SimpliXAccessDeniedHandler accessDeniedHandler) {
        this.properties = properties;
        this.userDetailsService = userDetailsService;
        this.tokenAuthenticationFilter = tokenAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    // AuthenticationProvider는 Spring Security가 자동으로 설정하므로 별도 빈 정의 불필요
    // UserDetailsService와 PasswordEncoder만 제공하면 됨

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    @Order(100)
    @ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-token-endpoints", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain tokenSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatchers(matchers -> matchers
                .requestMatchers(request -> {
                    String path = request.getServletPath();
                    return path.endsWith("/auth/token/issue") ||
                           path.endsWith("/auth/token/refresh") ||
                           path.contains("/auth/token/");
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().permitAll())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(101)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        // Get permit patterns for API endpoints
        String[] apiPermitPatterns = getApiPermitPatterns();

        http
            .securityMatchers(matchers -> matchers.requestMatchers("/api/**"))
            .authorizeHttpRequests(auth -> {
                if (apiPermitPatterns.length > 0) {
                    auth.requestMatchers(apiPermitPatterns).permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }
    

    @SuppressWarnings("deprecation")
    @Bean
    @Order(102)
    @ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-web-security", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        final String loginPage = properties.getSecurity().getLoginPageTemplate().startsWith("/") ? 
            properties.getSecurity().getLoginPageTemplate() : "/" + properties.getSecurity().getLoginPageTemplate();
        
        final String loginProcessingUrl = properties.getSecurity().getLoginProcessingUrl().startsWith("/") ? 
            properties.getSecurity().getLoginProcessingUrl() : "/" + properties.getSecurity().getLoginProcessingUrl();
        
        final String logoutUrl = properties.getSecurity().getLogoutUrl().startsWith("/") ? 
            properties.getSecurity().getLogoutUrl() : "/" + properties.getSecurity().getLogoutUrl();

        String[] defaultPermitPatterns = {
            loginPage, loginProcessingUrl, logoutUrl, "/error", "/",
            "/css/**", "/js/**", "/images/**", "/webjars/**",
            "/favicon.ico", "/vendor/**",
            "/h2-console/**", "/h2-console",
            "/actuator/**", "/actuator/health"
        };

        String[] permitAllPatterns = properties.getSecurity().getPermitAllPatterns() != null ?
            combineArrays(defaultPermitPatterns, properties.getSecurity().getPermitAllPatterns()) :
            defaultPermitPatterns;

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(permitAllPatterns).permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage(loginPage)
                .loginProcessingUrl(loginProcessingUrl)
                .defaultSuccessUrl("/", false)
                .failureHandler((request, response, exception) -> {
                    response.sendRedirect(loginPage + "?error");
                })
                .permitAll())
            .logout(logout -> logout
                .logoutUrl(logoutUrl)
                .logoutSuccessHandler((request, response, authentication) -> {
                    SecurityContextHolder.clearContext();
                    response.sendRedirect(loginPage + "?logout");
                })
                .clearAuthentication(true)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "accessToken", "refreshToken")
                .permitAll())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .userDetailsService(userDetailsService)
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src * 'unsafe-inline' 'unsafe-eval'; " +
                                    "script-src * 'unsafe-inline' 'unsafe-eval'; " +
                                    "img-src * data: blob: 'unsafe-inline'; " +
                                    "font-src * data: 'unsafe-inline'; " +
                                    "style-src * 'unsafe-inline'; " +
                                    "frame-src *; " +
                                    "connect-src *;")));

        // HTTP Basic authentication configuration
        if (properties.getSecurity() != null && properties.getSecurity().isEnableHttpBasic()) {
            http.httpBasic(Customizer.withDefaults());
        }

        // CSRF configuration
        if (properties.getSecurity() != null && properties.getSecurity().isEnableCsrf()) {
            String[] csrfIgnorePatterns = properties.getSecurity().getCsrfIgnorePatterns();
            if (csrfIgnorePatterns != null && csrfIgnorePatterns.length > 0) {
                http.csrf(csrf -> csrf.ignoringRequestMatchers(csrfIgnorePatterns));
            }
        } else {
            http.csrf(AbstractHttpConfigurer::disable);
        }

        // CORS configuration
        if (properties.getSecurity() != null && properties.getSecurity().isEnableCors()) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        }

        // Security headers configuration
        if (properties.getSecurity() != null) {
            http.headers(headers -> {
                headers.frameOptions(frameOptions -> frameOptions.sameOrigin());
            });
        }

        // HTTPS requirements
        if (properties.getSecurity() != null && properties.getSecurity().isRequireHttps()) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        return http.build();
    }


    @Bean
    @ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-cors", havingValue = "true", matchIfMissing = true)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        configuration.applyPermitDefaultValues();
        
        String[] allowedOrigins = properties.getCors().getAllowedOrigins();
        if (allowedOrigins != null && allowedOrigins.length > 0) {
            configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        }
        
        String[] allowedMethods = properties.getCors().getAllowedMethods();
        if (allowedMethods != null && allowedMethods.length > 0) {
            configuration.setAllowedMethods(Arrays.asList(allowedMethods));
        }
        
        String[] allowedHeaders = properties.getCors().getAllowedHeaders();
        if (allowedHeaders != null && allowedHeaders.length > 0) {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));
        }
        
        String[] exposedHeaders = properties.getCors().getExposedHeaders();
        if (exposedHeaders != null && exposedHeaders.length > 0) {
            configuration.setExposedHeaders(Arrays.asList(exposedHeaders));
        }
        
        Boolean allowCredentials = properties.getCors().getAllowCredentials();
        if (allowCredentials != null) {
            configuration.setAllowCredentials(allowCredentials);
        }
        
        Long maxAge = properties.getCors().getMaxAge();
        if (maxAge != null) {
            configuration.setMaxAge(maxAge);
        }
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private String[] combineArrays(String[] arr1, String[] arr2) {
        String[] result = new String[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, result, 0, arr1.length);
        System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
        return result;
    }

    private String[] getApiPermitPatterns() {
        String[] permitAllPatterns = properties.getSecurity().getPermitAllPatterns();
        if (permitAllPatterns == null || permitAllPatterns.length == 0) {
            return new String[0];
        }
        
        // Filter patterns that are API-related (including Swagger and Actuator)
        return Arrays.stream(permitAllPatterns)
            .filter(pattern -> pattern.startsWith("/api/") ||
                             pattern.contains("swagger") ||
                             pattern.contains("api-docs") ||
                             pattern.contains("webjars") ||
                             pattern.contains("actuator") ||
                             pattern.equals("/api/test/**"))
            .toArray(String[]::new);
    }
} 
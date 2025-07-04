package dev.simplecore.simplix.auth.security;

import dev.simplecore.simplix.auth.exception.TokenValidationException;
import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@AutoConfiguration
@ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-token-endpoints", havingValue = "true", matchIfMissing = true)
public class SimpliXTokenAuthenticationFilter extends OncePerRequestFilter {
    private final SimpliXJweTokenProvider tokenProvider;
    private final SimpliXUserDetailsService userDetailsService;
    private static final Logger logger = LoggerFactory.getLogger(SimpliXTokenAuthenticationFilter.class);

    public SimpliXTokenAuthenticationFilter(SimpliXJweTokenProvider tokenProvider, SimpliXUserDetailsService userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    @ConditionalOnMissingBean
    public static SimpliXTokenAuthenticationFilter tokenAuthenticationFilter(
        SimpliXJweTokenProvider tokenProvider, 
        SimpliXUserDetailsService userDetailsService
    ) {
        return new SimpliXTokenAuthenticationFilter(tokenProvider, userDetailsService);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
            
        // Check for existing authentication
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null && existingAuth.isAuthenticated() && 
            !(existingAuth instanceof AnonymousAuthenticationToken)) {
            chain.doFilter(request, response);
            return;
        }

        // Proceed with token validation if no session authentication exists
        String token = extractToken(request);
        if (token != null) {
            try {
                JWTClaimsSet claims = tokenProvider.parseToken(token);
                String username = claims.getSubject();
                logger.debug("Token username: " + username);
                
                boolean isValid = tokenProvider.validateToken(token,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
                
                logger.debug("Token validation result: " + isValid);  // Add token validation result log
                
                if (isValid) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    logger.debug("Loaded user details: " + userDetails);  // Add user details log
                    
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(
                            userDetails, 
                            null, 
                            userDetails.getAuthorities()
                        );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.debug("Authentication set for user: " + username);
                } else {
                    logger.debug("Token validation failed");
                }
            } catch (TokenValidationException e) {
                logger.error("Token validation failed: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

                throw new TokenValidationException(e.getMessage(), e.getDetail() != null ? e.getDetail().toString() : null);
            } catch (Exception e) {
                logger.error("Authentication failed", e);
                SecurityContextHolder.clearContext();
            }
        }
        
        Authentication finalAuth = SecurityContextHolder.getContext().getAuthentication();
        logger.debug("Final auth state: " + (finalAuth != null ? finalAuth.getName() + ", authenticated=" + finalAuth.isAuthenticated() : "none"));
        
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
} 
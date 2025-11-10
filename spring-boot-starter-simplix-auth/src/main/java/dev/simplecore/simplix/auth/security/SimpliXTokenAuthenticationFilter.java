package dev.simplecore.simplix.auth.security;

import com.nimbusds.jwt.JWTClaimsSet;
import dev.simplecore.simplix.auth.exception.TokenValidationException;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.Arrays;

@AutoConfiguration
@ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-token-endpoints", havingValue = "true", matchIfMissing = true)
public class SimpliXTokenAuthenticationFilter extends OncePerRequestFilter {
    private final SimpliXJweTokenProvider tokenProvider;
    private final SimpliXUserDetailsService userDetailsService;
    private final SimpliXAuthProperties properties;
    private static final Logger logger = LoggerFactory.getLogger(SimpliXTokenAuthenticationFilter.class);

    public SimpliXTokenAuthenticationFilter(SimpliXJweTokenProvider tokenProvider, SimpliXUserDetailsService userDetailsService, SimpliXAuthProperties properties) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public static SimpliXTokenAuthenticationFilter tokenAuthenticationFilter(
        SimpliXJweTokenProvider tokenProvider, 
        SimpliXUserDetailsService userDetailsService,
        SimpliXAuthProperties properties
    ) {
        return new SimpliXTokenAuthenticationFilter(tokenProvider, userDetailsService, properties);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        // Check if request matches permit-all patterns
        if (isPermitAllRequest(request)) {
            chain.doFilter(request, response);
            return;
        }

        // Extract token first to determine authentication strategy
        String token = extractToken(request);
        boolean preferTokenOverSession = properties.getSecurity().isPreferTokenOverSession();

        // Check for existing authentication
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasSessionAuth = existingAuth != null && existingAuth.isAuthenticated() &&
            !(existingAuth instanceof AnonymousAuthenticationToken);

        // If token exists and preferTokenOverSession is enabled, prioritize token authentication
        if (token != null && preferTokenOverSession) {
            logger.debug("Token found and prefer-token-over-session enabled, prioritizing token authentication");
            if (hasSessionAuth) {
                logger.debug("Existing session authentication will be replaced by token authentication");
            }
            processTokenAuthentication(request, response, token);
        } else if (!hasSessionAuth && token != null) {
            // No session auth exists, try token authentication
            logger.debug("No session authentication found, attempting token authentication");
            processTokenAuthentication(request, response, token);
        } else if (hasSessionAuth && !preferTokenOverSession) {
            // Session authentication exists and token is not preferred, use session
            logger.debug("Using existing session authentication");
            chain.doFilter(request, response);
            return;
        }

        Authentication finalAuth = SecurityContextHolder.getContext().getAuthentication();
        logger.debug("Final auth state: " + (finalAuth != null ? finalAuth.getName() + ", authenticated=" + finalAuth.isAuthenticated() : "none"));

        chain.doFilter(request, response);
    }

    private void processTokenAuthentication(HttpServletRequest request, HttpServletResponse response, String token)
            throws ServletException, IOException {
        try {
            JWTClaimsSet claims = tokenProvider.parseToken(token);
            String username = claims.getSubject();
            logger.debug("Token username: " + username);

            boolean isValid = tokenProvider.validateToken(token,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));

            logger.debug("Token validation result: " + isValid);

            if (isValid) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                logger.debug("Loaded user details: " + userDetails);

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

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private boolean isPermitAllRequest(HttpServletRequest request) {
        String[] permitAllPatterns = properties.getSecurity().getPermitAllPatterns();
        if (permitAllPatterns == null || permitAllPatterns.length == 0) {
            return false;
        }

        String requestPath = request.getRequestURI();
        return Arrays.stream(permitAllPatterns)
            .anyMatch(pattern -> {
                // Simple path matching without deprecated classes
                return requestPath.matches(pattern.replace("**", ".*").replace("*", "[^/]*"));
            });
    }
} 
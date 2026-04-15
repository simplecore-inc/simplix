package dev.simplecore.simplix.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds request correlation data to MDC for structured logging.
 * <p>
 * Runs at highest precedence to ensure all downstream filters and handlers
 * have access to the correlation context. MDC keys set:
 * <ul>
 *   <li>{@code requestId} — unique UUID per request</li>
 *   <li>{@code sessionId} — HTTP session ID or "none"</li>
 *   <li>{@code username} — authenticated principal name (set after chain completes)</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthRequestCorrelationFilter extends OncePerRequestFilter {

    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_SESSION_ID = "sessionId";
    static final String MDC_USERNAME = "username";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_SESSION_ID, resolveSessionId(request));

        try {
            filterChain.doFilter(request, response);

            // After the chain completes, set username if authentication succeeded
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                MDC.put(MDC_USERNAME, auth.getName());
            }
        } finally {
            MDC.clear();
        }
    }

    private String resolveSessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null ? session.getId() : "none";
    }
}

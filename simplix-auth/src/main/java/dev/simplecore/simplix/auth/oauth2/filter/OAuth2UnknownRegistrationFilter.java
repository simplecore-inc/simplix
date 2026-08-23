package dev.simplecore.simplix.auth.oauth2.filter;

import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns 「there is no such provider」 into the sign-in screen rather than into an error page.
 *
 * <p>Spring's {@code OAuth2AuthorizationRequestRedirectFilter} answers a registration id its
 * repository does not know with {@code sendError(500)}, and it writes that itself rather than
 * throwing — so nothing downstream can turn it into anything else. This runs in front of it and
 * asks the same question first.
 *
 * <p><b>Null is the ordinary answer for an application whose registrations are configured at
 * runtime</b> rather than written into a properties file: the connection was switched off between
 * the sign-in screen and the press, its provider stopped answering, or somebody typed the address.
 * None of those is a fault of the server, and all of them have the same remedy — go back and use
 * another way in. So the reader is returned to the configured failure address, which is the screen
 * the other ways in are on.
 *
 * <p>The repository is asked twice per sign-in, once here and once by the filter behind it. That
 * is the cost of the answer being a redirect rather than a stack trace, and a repository that
 * cannot afford two reads of one row is a repository that cannot afford one.
 */
@Slf4j
@RequiredArgsConstructor
public class OAuth2UnknownRegistrationFilter extends OncePerRequestFilter {

    private final SimpliXOAuth2Properties properties;
    private final ClientRegistrationRepository registrations;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String registrationId = registrationIdOf(request);
        if (registrationId != null && !isKnown(registrationId)) {
            log.warn("A sign-in was started against a provider this application does not have: {}",
                    request.getRequestURI());
            response.sendRedirect(properties.getFailureUrl());
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * @param request the request
     * @return the registration this request starts a sign-in against, or null when it starts none
     */
    private String registrationIdOf(HttpServletRequest request) {
        String base = properties.getAuthorizationBaseUrl() + "/";
        String uri = request.getRequestURI();
        String path = uri.startsWith(request.getContextPath())
                ? uri.substring(request.getContextPath().length()) : uri;
        if (!path.startsWith(base)) {
            return null;
        }
        String rest = path.substring(base.length());
        // Nothing past the base, or a deeper path than a registration id: neither is a sign-in
        // this filter has anything to say about, so both go through untouched.
        return rest.isEmpty() || rest.indexOf('/') >= 0 ? null : rest;
    }

    /**
     * @param registrationId the registration named in the path
     * @return whether this application has it
     */
    private boolean isKnown(String registrationId) {
        try {
            return registrations.findByRegistrationId(registrationId) != null;
        } catch (RuntimeException unreadable) {
            // A repository that cannot answer is not a registration that exists. Reported as
            // unknown for the same reason the null is: the reader's remedy is the same, and the
            // cause belongs in the log rather than on the sign-in screen.
            log.warn("The client registrations could not be read: {}", unreadable.getMessage());
            return false;
        }
    }
}

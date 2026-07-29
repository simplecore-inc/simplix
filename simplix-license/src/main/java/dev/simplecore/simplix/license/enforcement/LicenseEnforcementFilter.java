package dev.simplecore.simplix.license.enforcement;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.license.core.LicenseManager;
import dev.simplecore.simplix.license.model.LicenseState;
import dev.accesscore.license.sdk.model.LicenseModel.Denial;
import dev.accesscore.license.sdk.model.LicenseStatus;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * HTTP filter that enforces license status at the request level.
 *
 * <p>Behaviour by license status:
 * <ul>
 *   <li>{@code VALID} — all requests allowed</li>
 *   <li>{@code GRACE_PERIOD} in READ_ONLY mode — only GET, HEAD, and OPTIONS allowed</li>
 *   <li>every other status — only the always-permitted paths are reachable</li>
 * </ul>
 *
 * <p>The permitted paths keep a deployment recoverable without shell access: first-run setup,
 * the license surface, and the public endpoints stay reachable while the license is missing,
 * expired, or rejected, so an operator can finish setup and register a license through the
 * product itself. Their prefixes are derived from the configured API version prefix rather than
 * hard-coded, so a deployment that changes the prefix does not silently lose them.
 *
 * <p><strong>Authentication is never gated by licensing.</strong> The license surface requires
 * an authenticated operator, so gating authentication too would make an unlicensed deployment
 * unrecoverable through the product: the operator could not sign in because no license is
 * registered, and could not register one because signing in was refused. The permitted scope is
 * therefore exactly the token endpoints of {@code {prefix}/auth/token} — {@code issue} to sign
 * in, {@code refresh} to stay signed in, and {@code revoke} to sign out, which is the non-form
 * counterpart of the already-permitted {@code /backend/logout}. They are listed as exact paths
 * rather than a prefix so an endpoint added under {@code /auth/token} later is denied until it
 * is deliberately reviewed and added here.
 *
 * <p>Permitting them grants nothing on its own. A session carries no licensed capability: every
 * other API stays behind this filter, and the feature gates still deny every licensed module to
 * the operator that just signed in. What the operator gains is the ability to reach the license
 * surface and register a license.
 *
 * <p>The refusal itself is the core's — the status, the message key, and its arguments all come
 * from the judgement. What this class adds is this product's response envelope and its locale.
 */
public class LicenseEnforcementFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LicenseEnforcementFilter.class);

    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /** The general denial line, used when the judgement carries no specific one. */
    private static final Denial GENERAL_DENIAL = new Denial("error.license.notValid", List.of());

    /** Paths outside the API prefix that must stay reachable regardless of license status. */
    private static final List<String> ALWAYS_PERMITTED_EXACT = List.of(
            "/actuator/health",
            "/backend/login",
            "/backend/logout"
    );

    /**
     * Authentication endpoints, relative to the API prefix. Signing in is what the operator
     * needs before the license surface will answer, so licensing never gates these.
     */
    private static final List<String> AUTHENTICATION_PATHS = List.of(
            "/auth/token/issue",
            "/auth/token/refresh",
            "/auth/token/revoke"
    );

    private static final List<String> STATIC_PREFIXES = List.of(
            "/css/", "/js/", "/images/", "/fonts/", "/favicon"
    );

    private final LicenseManager licenseManager;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    /** Path prefixes reachable while the license is not usable. */
    private final List<String> permittedPrefixes;

    /** Exact paths reachable while the license is not usable. */
    private final Set<String> permittedExactPaths;

    /**
     * @param licenseManager the source of the current judgement
     * @param objectMapper how the refusal envelope is written
     * @param messageSource where the message key is translated
     * @param apiVersionPrefix the configured API version prefix
     */
    public LicenseEnforcementFilter(LicenseManager licenseManager,
                                    ObjectMapper objectMapper,
                                    MessageSource messageSource,
                                    String apiVersionPrefix) {
        this.licenseManager = licenseManager;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.permittedPrefixes = buildPermittedPrefixes(apiVersionPrefix);
        this.permittedExactPaths = buildPermittedExactPaths(apiVersionPrefix);
    }

    /**
     * Builds the exact paths reachable while the license is not usable: the fixed non-API paths
     * plus the authentication endpoints under the configured API prefix.
     *
     * @param apiVersionPrefix the configured API version prefix (e.g. {@code /api/v1})
     * @return the permitted exact paths
     */
    private static Set<String> buildPermittedExactPaths(String apiVersionPrefix) {
        String prefix = normalizePrefix(apiVersionPrefix);
        Set<String> paths = new LinkedHashSet<>(ALWAYS_PERMITTED_EXACT);
        AUTHENTICATION_PATHS.forEach(path -> paths.add(prefix + path));
        return Set.copyOf(paths);
    }

    /**
     * Builds the reachable-while-unlicensed prefixes for the given API version prefix.
     *
     * @param apiVersionPrefix the configured API version prefix (e.g. {@code /api/v1})
     * @return the permitted path prefixes
     */
    private static List<String> buildPermittedPrefixes(String apiVersionPrefix) {
        String prefix = normalizePrefix(apiVersionPrefix);
        return List.of(
                prefix + "/system/setup",
                prefix + "/install",
                prefix + "/public/",
                prefix + "/deployment-license/"
        );
    }

    /**
     * @param apiVersionPrefix the configured prefix, possibly blank
     * @return the prefix with a leading slash and no trailing slash
     */
    private static String normalizePrefix(String apiVersionPrefix) {
        if (apiVersionPrefix == null || apiVersionPrefix.isBlank()) {
            return "";
        }
        String prefix = apiVersionPrefix.trim();
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (isPermittedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        LicenseState.Snapshot snapshot = licenseManager.getState().snapshot();
        LicenseStatus status = snapshot.status();

        switch (status) {
            case VALID -> filterChain.doFilter(request, response);

            // A deployment running above its entitled release keeps serving requests. The
            // ceiling is an entitlement control, and blocking the filter here would stop access
            // control at the doors; what it loses is the paid modules, which the feature gates
            // deny on their own.
            case RELEASE_NOT_ENTITLED -> filterChain.doFilter(request, response);

            case GRACE_PERIOD -> {
                if (licenseManager.isGracePeriodReadOnly() && !READ_ONLY_METHODS.contains(method)) {
                    writeErrorResponse(response, HttpStatus.FORBIDDEN, status,
                            new Denial("error.license.gracePeriodReadOnly", List.of()));
                    return;
                }
                filterChain.doFilter(request, response);
            }

            case EXPIRED, INVALID_SIGNATURE, MACHINE_MISMATCH, FINGERPRINT_UNAVAILABLE,
                 NOT_ACTIVATED, NOT_LOADED, SCHEMA_UNSUPPORTED, REVOKED, CLOCK_TAMPERED,
                 INTEGRITY_FAILED, HEARTBEAT_OVERDUE, PRODUCT_MISMATCH -> {
                log.warn("Request blocked by license enforcement: status={}, path={}, method={}",
                        status, path, method);
                writeErrorResponse(response, HttpStatus.FORBIDDEN, status, denialOf(snapshot));
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return STATIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * @param path the request path
     * @return whether it stays reachable while the license is not usable
     */
    private boolean isPermittedPath(String path) {
        if (permittedExactPaths.contains(path)) {
            return true;
        }
        return permittedPrefixes.stream().anyMatch(path::startsWith);
    }

    /**
     * @param snapshot the judgement that denied the request
     * @return its denial, falling back to the general line when it carries none
     */
    private static Denial denialOf(LicenseState.Snapshot snapshot) {
        Denial denial = snapshot.evaluation().denial();
        return denial != null ? denial : GENERAL_DENIAL;
    }

    /**
     * Writes the standard SimpliX error envelope with a localized message.
     *
     * <p>The envelope carries {@code LICENSE_<status>} as its error code, which is what lets a
     * client tell a licensing denial apart from an ordinary authorization failure and offer the
     * license screen instead of a permissions message.
     *
     * @param response the servlet response
     * @param httpStatus the HTTP status to send
     * @param licenseStatus the license status that caused the denial
     * @param denial the message key describing the denial, with its placeholder values
     */
    private void writeErrorResponse(HttpServletResponse response, HttpStatus httpStatus,
                                    LicenseStatus licenseStatus, Denial denial)
            throws IOException {
        response.setStatus(httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String message = messageSource.getMessage(denial.messageKey(), denial.argumentArray(),
                denial.messageKey(), LocaleContextHolder.getLocale());
        SimpliXApiResponse<Void> body = SimpliXApiResponse.error(message,
                licenseStatus.errorCode());
        objectMapper.writeValue(response.getWriter(), body);
    }
}

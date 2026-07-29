package dev.simplecore.simplix.license.setup;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Blocks API access until first-run setup has completed. While the app is
 * uninitialized, only the setup and installer endpoints are reachable under the
 * API prefix; every other API request returns 409 with a {@code SETUP_REQUIRED}
 * body so the frontend routes to the setup wizard. Non-API requests (the SPA,
 * static assets, login) pass through so the wizard UI can load.
 *
 * <p>The state-changing and secret installer endpoints (the setup submission and
 * the env-profile editor) are additionally restricted to a trusted caller while
 * uninitialized: a loopback client, or one that presents the configured bootstrap
 * token. This closes the pre-setup window in which an unauthenticated network
 * caller could otherwise read/rewrite deployment secrets or seize the first admin
 * account. Set {@code simplix.license.setup.token} ({@code SIMPLIX_LICENSE_SETUP_TOKEN}) to permit remote
 * setup; leave it unset and only a local operator can complete setup.
 */
@Component
public class InitializationGateFilter extends OncePerRequestFilter {

    private static final String SETUP_TOKEN_HEADER = "X-Setup-Token";

    private final SetupState setupState;
    private final String apiPrefix;
    private final String setupToken;

    public InitializationGateFilter(SetupState setupState,
                                    @Value("${api.version.prefix:/api/v1}") String apiPrefix,
                                    @Value("${simplix.license.setup.token:}") String setupToken) {
        this.setupState = setupState;
        this.apiPrefix = apiPrefix;
        this.setupToken = setupToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (setupState.isInitialized()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isApiRequest(path) && !isAllowedBeforeSetup(path)) {
            writeError(response, HttpServletResponse.SC_CONFLICT, "SETUP_REQUIRED", "Application setup is required");
            return;
        }
        if (isProtectedInstallerPath(path) && !isTrustedInstaller(request)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "SETUP_FORBIDDEN",
                    "Setup must be completed by a local operator or with a valid setup token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isApiRequest(String path) {
        return path.startsWith(apiPrefix + "/");
    }

    /**
     * Endpoints reachable before setup: the setup wizard, the installer, and the
     * public endpoints the frontend calls while bootstrapping (e.g. public access
     * sync), which must resolve rather than 409 so the wizard UI can load.
     */
    private boolean isAllowedBeforeSetup(String path) {
        return path.startsWith(apiPrefix + "/system/setup")
                || path.startsWith(apiPrefix + "/install")
                || path.startsWith(apiPrefix + "/public/");
    }

    /**
     * The installer endpoints that mutate state or expose secrets: the setup
     * submission (POST {@code /system/setup}), the whole env-profile editor, and
     * the license step, which claims a seat against the deployment's contract and
     * reports its machine fingerprint. The read-only status probe
     * ({@code /system/setup/status}) stays open so the SPA can decide where to route.
     */
    private boolean isProtectedInstallerPath(String path) {
        if (path.startsWith(apiPrefix + "/system/setup/env")
                || path.startsWith(apiPrefix + "/system/setup/license")) {
            return true;
        }
        String setupBase = apiPrefix + "/system/setup";
        return path.equals(setupBase) || path.equals(setupBase + "/");
    }

    /**
     * A trusted installer caller: one presenting the configured bootstrap token,
     * or — when no token is configured — a loopback client. The remote address is
     * read directly (never a forwarded header, which a caller can spoof), so a
     * request arriving through a proxy is not treated as local.
     */
    private boolean isTrustedInstaller(HttpServletRequest request) {
        if (StringUtils.hasText(setupToken) && setupToken.equals(request.getHeader(SETUP_TOKEN_HEADER))) {
            return true;
        }
        return isLoopback(request.getRemoteAddr());
    }

    private boolean isLoopback(String remoteAddr) {
        if (!StringUtils.hasText(remoteAddr)) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddr).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}

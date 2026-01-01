package dev.simplecore.simplix.auth.oauth2.handler;

import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationException;
import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService;
import dev.simplecore.simplix.auth.oauth2.OAuth2Intent;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import dev.simplecore.simplix.auth.oauth2.filter.OAuth2IntentFilter;
import dev.simplecore.simplix.auth.oauth2.extractor.OAuth2UserInfoExtractor;
import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import dev.simplecore.simplix.auth.security.SimpliXJweTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Handles successful OAuth2 authentication.
 * Supports both login/signup mode and account linking mode.
 */
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    /**
     * Session attribute key for storing user ID during account linking flow.
     */
    public static final String LINKING_USER_ID_ATTR = "oauth2_linking_user_id";

    private final OAuth2AuthenticationService authService;
    private final OAuth2UserInfoExtractor extractor;
    private final SimpliXJweTokenProvider tokenProvider;
    private final SimpliXOAuth2Properties properties;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        try {
            OAuth2UserInfo userInfo = extractor.extract(authentication);
            HttpSession session = request.getSession(false);

            // Atomically get and remove linking user ID to prevent race conditions
            // when multiple OAuth2 callbacks arrive simultaneously (e.g., double-click)
            String linkingUserId = null;
            if (session != null) {
                synchronized (session) {
                    linkingUserId = (String) session.getAttribute(LINKING_USER_ID_ATTR);
                    if (linkingUserId != null) {
                        session.removeAttribute(LINKING_USER_ID_ATTR);
                    }
                }
            }

            if (linkingUserId != null) {
                // Account linking mode
                handleLinking(request, response, linkingUserId, userInfo);
            } else {
                // Login/signup mode
                handleLogin(request, response, userInfo);
            }
        } catch (OAuth2AuthenticationException e) {
            log.warn("OAuth2 authentication failed: {} - {}", e.getErrorCode(), e.getMessage());
            handleError(request, response, e.getErrorCode());
        } catch (Exception e) {
            log.error("Unexpected error during OAuth2 authentication", e);
            handleError(request, response, OAuth2AuthenticationException.PROVIDER_ERROR);
        }
    }

    private void handleLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            OAuth2UserInfo userInfo) throws IOException {

        log.trace("Processing OAuth2 login for provider: {}, providerId: {}",
                userInfo.getProvider(), userInfo.getProviderId());

        // Get intent from session
        OAuth2Intent intent = getOAuth2Intent(request);
        log.trace("OAuth2 intent: {}", intent);

        // Authenticate or create user with intent
        UserDetails user = authService.authenticateOAuth2User(userInfo, intent);

        // Callback for logging
        authService.onAuthenticationSuccess(
                user,
                userInfo,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );

        log.info("OAuth2 login successful for user: {} via {}",
                user.getUsername(), userInfo.getProvider());

        // Issue tokens (same as /auth/token/issue)
        try {
            var tokens = tokenProvider.createTokenPair(
                    user.getUsername(),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent")
            );

            // Deliver tokens based on configured method
            deliverTokens(response, tokens);
        } catch (Exception e) {
            log.error("Failed to create tokens for OAuth2 login", e);
            handleError(request, response, OAuth2AuthenticationException.PROVIDER_ERROR);
        }
    }

    /**
     * Deliver tokens to the client based on the configured token delivery method.
     */
    private void deliverTokens(
            HttpServletResponse response,
            SimpliXJweTokenProvider.TokenResponse tokens) throws IOException {

        switch (properties.getTokenDeliveryMethod()) {
            case POST_MESSAGE -> deliverTokensViaPostMessage(response, tokens);
            case COOKIE -> deliverTokensViaCookie(response, tokens);
            case REDIRECT -> deliverTokensViaRedirect(response, tokens);
        }
    }

    /**
     * Deliver tokens via HttpOnly cookies.
     */
    private void deliverTokensViaCookie(
            HttpServletResponse response,
            SimpliXJweTokenProvider.TokenResponse tokens) throws IOException {

        var cookieSettings = properties.getCookie();

        // Access token cookie
        addTokenCookie(response, cookieSettings.getAccessTokenName(),
                tokens.getAccessToken(), cookieSettings);

        // Refresh token cookie
        addTokenCookie(response, cookieSettings.getRefreshTokenName(),
                tokens.getRefreshToken(), cookieSettings);

        // Redirect to success URL
        response.sendRedirect(properties.getSuccessUrl());
    }

    private void addTokenCookie(
            HttpServletResponse response,
            String name,
            String value,
            SimpliXOAuth2Properties.CookieSettings settings) {

        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append("=").append(value);
        cookie.append("; Path=").append(settings.getPath());

        if (settings.isHttpOnly()) {
            cookie.append("; HttpOnly");
        }
        if (settings.isSecure()) {
            cookie.append("; Secure");
        }
        if (settings.getSameSite() != null) {
            cookie.append("; SameSite=").append(settings.getSameSite());
        }

        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Deliver tokens via redirect URL query parameters.
     * <p>
     * Warning: Tokens may be logged in browser history.
     */
    private void deliverTokensViaRedirect(
            HttpServletResponse response,
            SimpliXJweTokenProvider.TokenResponse tokens) throws IOException {

        String redirectUrl = UriComponentsBuilder
                .fromUriString(properties.getSuccessUrl())
                .queryParam("accessToken", tokens.getAccessToken())
                .queryParam("refreshToken", tokens.getRefreshToken())
                .queryParam("accessTokenExpiry", tokens.getAccessTokenExpiry().toString())
                .queryParam("refreshTokenExpiry", tokens.getRefreshTokenExpiry().toString())
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    /**
     * Deliver tokens to the client via PostMessage.
     * <p>
     * This method renders an HTML page that sends the tokens to the parent window
     * using postMessage, then closes the popup window.
     */
    private void deliverTokensViaPostMessage(
            HttpServletResponse response,
            SimpliXJweTokenProvider.TokenResponse tokens) throws IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter writer = response.getWriter();

        String html = """
                <!DOCTYPE html>
                <html>
                <head><title>Login Successful</title></head>
                <body>
                    <script>
                        if (window.opener) {
                            window.opener.postMessage({
                                type: 'OAUTH2_SUCCESS',
                                accessToken: '%s',
                                refreshToken: '%s',
                                accessTokenExpiry: '%s',
                                refreshTokenExpiry: '%s'
                            }, '%s');
                            window.close();
                        } else {
                            document.body.innerHTML = '<p>Login successful. You can close this window.</p>';
                        }
                    </script>
                    <noscript><p>Login successful. Please close this window.</p></noscript>
                </body>
                </html>
                """.formatted(
                tokens.getAccessToken(),
                tokens.getRefreshToken(),
                tokens.getAccessTokenExpiry().toString(),
                tokens.getRefreshTokenExpiry().toString(),
                properties.getPostMessageOrigin()
        );

        writer.write(html);
        writer.flush();
    }

    private void handleLinking(
            HttpServletRequest request,
            HttpServletResponse response,
            String userId,
            OAuth2UserInfo userInfo) throws IOException {

        log.trace("Processing account linking for user: {}, provider: {}",
                userId, userInfo.getProvider());

        try {
            // Only save to DB
            authService.linkSocialAccount(userId, userInfo);

            log.info("Account linked successfully: user={}, provider={}",
                    userId, userInfo.getProvider());

            // Restore the user's app authentication in session
            // OAuth2 flow replaces session auth with OAuth2AuthenticationToken,
            // so we need to restore the original app authentication
            restoreUserAuthentication(request, userId);

            // Notify success
            notifyLinkingResult(response, true, null);
        } catch (OAuth2AuthenticationException e) {
            log.warn("Account linking failed: {}", e.getMessage());
            notifyLinkingResult(response, false, e.getErrorCode());
        }
    }

    /**
     * Restore the user's app authentication in session after OAuth2 linking.
     * <p>
     * OAuth2 flow replaces the session authentication with OAuth2AuthenticationToken.
     * After linking, we restore the user's original app authentication
     * so they remain logged in with their app account.
     */
    private void restoreUserAuthentication(HttpServletRequest request, String userId) {
        try {
            UserDetails user = authService.loadUserDetailsByUserId(userId);
            if (user != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                user.getAuthorities()
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.trace("Restored app authentication for user: {}", userId);
            } else {
                log.warn("Could not restore app authentication - user not found: {}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to restore app authentication: {}", e.getMessage());
        }
    }

    /**
     * Notify linking result to client without affecting session.
     */
    private void notifyLinkingResult(
            HttpServletResponse response,
            boolean success,
            String errorCode) throws IOException {

        if (properties.getTokenDeliveryMethod() == SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE) {
            // Send result via postMessage for popup flow
            response.setContentType("text/html;charset=UTF-8");
            PrintWriter writer = response.getWriter();

            String origin = properties.getPostMessageOrigin();
            String html;
            if (success) {
                html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Account Linked</title></head>
                    <body>
                        <script>
                            if (window.opener) {
                                window.opener.postMessage({
                                    type: 'OAUTH2_LINK_SUCCESS'
                                }, '%s');
                                window.close();
                            } else {
                                document.body.innerHTML = '<p>Account linked successfully. You can close this window.</p>';
                            }
                        </script>
                        <noscript><p>Account linked successfully. Please close this window.</p></noscript>
                    </body>
                    </html>
                    """.formatted(origin);
            } else {
                html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Account Link Failed</title></head>
                    <body>
                        <script>
                            if (window.opener) {
                                window.opener.postMessage({
                                    type: 'OAUTH2_LINK_FAILURE',
                                    error: '%s'
                                }, '%s');
                                window.close();
                            } else {
                                document.body.innerHTML = '<p>Account linking failed. Please close this window.</p>';
                            }
                        </script>
                        <noscript><p>Account linking failed. Please close this window.</p></noscript>
                    </body>
                    </html>
                    """.formatted(errorCode != null ? errorCode : "unknown_error", origin);
            }

            writer.write(html);
            writer.flush();
        } else {
            // Redirect flow
            String redirectUrl = success
                    ? properties.getLinkSuccessUrl()
                    : UriComponentsBuilder
                            .fromUriString(properties.getLinkFailureUrl())
                            .queryParam("error", errorCode)
                            .build()
                            .toUriString();
            response.sendRedirect(redirectUrl);
        }
    }

    private void handleError(
            HttpServletRequest request,
            HttpServletResponse response,
            String errorCode) throws IOException {

        log.trace("handleError called: errorCode={}, tokenDeliveryMethod={}",
                errorCode, properties.getTokenDeliveryMethod());

        // For POST_MESSAGE mode, send errors via postMessage
        if (properties.getTokenDeliveryMethod() == SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE) {
            if (OAuth2AuthenticationException.NO_LINKED_ACCOUNT.equals(errorCode)) {
                log.trace("Delivering NO_LINKED_ACCOUNT via postMessage");
                deliverNoLinkedAccountViaPostMessage(request, response);
            } else if (OAuth2AuthenticationException.EMAIL_ACCOUNT_EXISTS_NOT_LINKED.equals(errorCode)) {
                log.trace("Delivering EMAIL_ACCOUNT_EXISTS_NOT_LINKED via postMessage");
                deliverErrorViaPostMessage(response, "OAUTH2_EMAIL_EXISTS_NOT_LINKED", errorCode);
            } else if (OAuth2AuthenticationException.SOCIAL_ALREADY_REGISTERED.equals(errorCode)) {
                log.trace("Delivering SOCIAL_ALREADY_REGISTERED via postMessage");
                deliverErrorViaPostMessage(response, "OAUTH2_ERROR", errorCode);
            } else {
                log.trace("Delivering error via postMessage: {}", errorCode);
                deliverErrorViaPostMessage(response, "OAUTH2_ERROR", errorCode);
            }
            return;
        }

        // For other delivery methods, redirect to failure URL
        String redirectUrl = UriComponentsBuilder
                .fromUriString(properties.getFailureUrl())
                .queryParam("error", errorCode)
                .build()
                .toUriString();

        log.trace("Redirecting to failure URL: {}", redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    /**
     * Deliver error via postMessage for popup flow.
     *
     * @param response    HTTP response
     * @param messageType postMessage type (e.g., OAUTH2_ERROR, OAUTH2_EMAIL_EXISTS_NOT_LINKED)
     * @param errorCode   the error code
     */
    private void deliverErrorViaPostMessage(
            HttpServletResponse response,
            String messageType,
            String errorCode) throws IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter writer = response.getWriter();

        String origin = properties.getPostMessageOrigin();
        String html = """
                <!DOCTYPE html>
                <html>
                <head><title>OAuth2 Error</title></head>
                <body>
                    <script>
                        if (window.opener) {
                            window.opener.postMessage({
                                type: '%s',
                                error: '%s'
                            }, '%s');
                            window.close();
                        } else {
                            document.body.innerHTML = '<p>An error occurred. Please close this window.</p>';
                        }
                    </script>
                    <noscript><p>An error occurred. Please close this window.</p></noscript>
                </body>
                </html>
                """.formatted(messageType, escapeJs(errorCode), origin);

        writer.write(html);
        writer.flush();
    }

    /**
     * Deliver NO_LINKED_ACCOUNT error via postMessage for popup flow.
     * <p>
     * Retrieves pending social registration info from session if available.
     */
    private void deliverNoLinkedAccountViaPostMessage(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        log.trace("deliverNoLinkedAccountViaPostMessage called");
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter writer = response.getWriter();

        // Try to get pending social info from session
        HttpSession session = request.getSession(false);
        String provider = null;
        String email = null;
        String name = null;

        log.trace("Session available: {}", session != null);

        if (session != null) {
            Object pendingObj = session.getAttribute("oauth2.pending");
            log.trace("Pending social info in session: {}", pendingObj != null);
            if (pendingObj != null) {
                // Use reflection or duck typing to get values
                // The actual type is PendingSocialRegistration from the demo server
                try {
                    var providerMethod = pendingObj.getClass().getMethod("getProvider");
                    var emailMethod = pendingObj.getClass().getMethod("getEmail");
                    var nameMethod = pendingObj.getClass().getMethod("getName");

                    provider = (String) providerMethod.invoke(pendingObj);
                    email = (String) emailMethod.invoke(pendingObj);
                    name = (String) nameMethod.invoke(pendingObj);
                } catch (Exception e) {
                    log.warn("Failed to extract pending social info from session: {}", e.getMessage());
                }
            }
        }

        log.info("Sending NO_LINKED_ACCOUNT postMessage: provider={}, email={}, name={}",
                provider, email != null ? email.substring(0, Math.min(3, email.length())) + "***" : null, name);

        String origin = properties.getPostMessageOrigin();
        String html = """
                <!DOCTYPE html>
                <html>
                <head><title>No Linked Account</title></head>
                <body>
                    <script>
                        if (window.opener) {
                            window.opener.postMessage({
                                type: 'OAUTH2_NO_LINKED_ACCOUNT',
                                provider: %s,
                                email: %s,
                                name: %s
                            }, '%s');
                            window.close();
                        } else {
                            document.body.innerHTML = '<p>No linked account found. Please close this window and sign up.</p>';
                        }
                    </script>
                    <noscript><p>No linked account found. Please close this window.</p></noscript>
                </body>
                </html>
                """.formatted(
                provider != null ? "'" + escapeJs(provider) + "'" : "null",
                email != null ? "'" + escapeJs(email) + "'" : "null",
                name != null ? "'" + escapeJs(name) + "'" : "null",
                origin
        );

        writer.write(html);
        writer.flush();
    }

    /**
     * Get OAuth2 intent from session and clear it.
     *
     * @param request HTTP request
     * @return OAuth2Intent from session, or AUTO if not set
     */
    private OAuth2Intent getOAuth2Intent(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return OAuth2Intent.AUTO;
        }

        String intentValue = (String) session.getAttribute(OAuth2IntentFilter.OAUTH2_INTENT_ATTR);
        session.removeAttribute(OAuth2IntentFilter.OAUTH2_INTENT_ATTR);

        if (intentValue == null) {
            return OAuth2Intent.AUTO;
        }

        return switch (intentValue) {
            case OAuth2IntentFilter.INTENT_LOGIN -> OAuth2Intent.LOGIN;
            case OAuth2IntentFilter.INTENT_REGISTER -> OAuth2Intent.REGISTER;
            default -> OAuth2Intent.AUTO;
        };
    }

    /**
     * Escape string for JavaScript.
     */
    private String escapeJs(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

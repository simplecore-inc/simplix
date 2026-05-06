package dev.simplecore.simplix.messaging.autoconfigure;

import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * Helpers that translate {@link MessagingProperties.NatsProperties} into a
 * jnats {@link Options} configuration. Extracted as static methods so they
 * can be unit-tested without opening a NATS connection.
 *
 * <p>Auth precedence (first match wins):
 * <ol>
 *   <li>URL-embedded credentials — jnats parses these from the server URL automatically</li>
 *   <li>username / password</li>
 *   <li>token</li>
 *   <li>creds file</li>
 *   <li>nkey file (not yet supported; warns and skips)</li>
 * </ol>
 */
@Slf4j
final class NatsOptionsBuilder {

    private NatsOptionsBuilder() {}

    /**
     * Applies authentication to the given {@link Options.Builder} according to
     * the precedence rules documented on this class.
     *
     * @param opts  the options builder to configure
     * @param props the NATS properties containing auth credentials
     */
    static void applyAuth(Options.Builder opts, MessagingProperties.NatsProperties props) {
        String servers = props.getServers() == null ? "" : props.getServers();
        if (urlHasAuth(servers)) {
            // jnats parses URL-embedded credentials automatically; no further action needed.
            return;
        }
        String username = props.getUsername();
        String password = props.getPassword();
        if (username != null && !username.isEmpty()
                && password != null && !password.isEmpty()) {
            opts.userInfo(username, password);
            return;
        }
        String token = props.getToken();
        if (token != null && !token.isEmpty()) {
            opts.token(token.toCharArray());
            return;
        }
        String credsFile = props.getCredsFile();
        if (credsFile != null && !credsFile.isEmpty()) {
            opts.authHandler(Nats.credentials(credsFile));
            return;
        }
        // nkey-file auth requires NKey.createFromSeed and a custom AuthHandler.
        // Supported in a follow-up; for now log a warning and skip.
        String nkeyFile = props.getNkeyFile();
        if (nkeyFile != null && !nkeyFile.isEmpty()) {
            log.warn("simplix.messaging.nats.nkey-file is set but nkey auth is not yet supported");
        }
    }

    /**
     * Applies TLS configuration to the given {@link Options.Builder}.
     * Does nothing when {@code tls} is {@code null} or disabled.
     *
     * @param opts the options builder to configure
     * @param tls  the TLS properties; may be {@code null}
     * @throws IllegalStateException if the SSLContext cannot be built from the provided stores
     */
    static void applyTls(Options.Builder opts,
                         MessagingProperties.NatsProperties.TlsProperties tls) {
        if (tls == null || !tls.isEnabled()) return;
        try {
            SSLContext ctx = buildSslContext(tls);
            opts.sslContext(ctx);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build SSLContext for NATS TLS", e);
        }
    }

    private static SSLContext buildSslContext(
            MessagingProperties.NatsProperties.TlsProperties tls) throws Exception {
        TrustManagerFactory tmf = null;
        String trustStore = tls.getTrustStore();
        if (trustStore != null && !trustStore.isEmpty()) {
            KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] tsPass = tls.getTrustStorePassword() == null
                    ? null : tls.getTrustStorePassword().toCharArray();
            try (FileInputStream fis = new FileInputStream(trustStore)) {
                ts.load(fis, tsPass);
            }
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
        }

        KeyManagerFactory kmf = null;
        String keyStore = tls.getKeyStore();
        if (keyStore != null && !keyStore.isEmpty()) {
            char[] ksPass = tls.getKeyStorePassword() == null
                    ? null : tls.getKeyStorePassword().toCharArray();
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(keyStore)) {
                ks.load(fis, ksPass);
            }
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, ksPass);
        }

        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(
                kmf == null ? null : kmf.getKeyManagers(),
                tmf == null ? null : tmf.getTrustManagers(),
                null);
        return ctx;
    }

    /**
     * Returns {@code true} when the servers string contains URL-embedded credentials
     * (i.e., the first server URL has an {@code @} after the scheme separator).
     */
    private static boolean urlHasAuth(String servers) {
        int schemeIdx = servers.indexOf("://");
        if (schemeIdx < 0) return false;
        String afterScheme = servers.substring(schemeIdx + 3);
        int firstComma = afterScheme.indexOf(',');
        String firstUrl = firstComma < 0 ? afterScheme : afterScheme.substring(0, firstComma);
        return firstUrl.contains("@");
    }
}

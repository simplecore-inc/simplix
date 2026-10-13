package dev.simplecore.simplix.license.activation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.accesscore.license.sdk.LicenseManager;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationOperation;
import dev.accesscore.license.sdk.protocol.ActivationModel.PreparedRequest;
import dev.accesscore.license.sdk.spi.LicenseSpi.ActivationServerDirectory;
import dev.simplecore.simplix.license.config.ContactIdentity;
import dev.simplecore.simplix.license.config.InstallationDiscriminator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * What rides beside the SDK's body: the installation discriminator goes out on the claim and on
 * every refresh when the application contributes one, and nowhere when it does not — the second
 * half is what keeps an application that never heard of it sending exactly what it sent before.
 */
class LicenseActivationClientTest {

    private static final String INSTALLATION_HEADER = "X-Activation-Installation";
    private static final String VALUE = "path=sha256:aa,ino=sha256:bb,dev=sha256:cc";

    private HttpServer server;
    /** The installation headers the last request carried; the server's headers are case-blind. */
    private final AtomicReference<List<String>> received = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            List<String> values = exchange.getRequestHeaders().get(INSTALLATION_HEADER);
            received.set(values == null ? List.of() : List.copyOf(values));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @ParameterizedTest
    @EnumSource(ActivationOperation.class)
    @DisplayName("sends no installation header from an application that contributes none")
    void absentWithoutABean(ActivationOperation operation) {
        client(null).send(operation, prepared());

        assertThat(received.get()).isEmpty();
    }

    @Test
    @DisplayName("sends the installation header on the claim")
    void sentOnTheClaim() {
        client(() -> VALUE).send(ActivationOperation.ACTIVATE, prepared());

        assertThat(received.get()).containsExactly(VALUE);
    }

    @Test
    @DisplayName("sends the installation header on every refresh, where a change is watched for")
    void sentOnTheRefresh() {
        client(() -> VALUE).send(ActivationOperation.HEARTBEAT, prepared());

        assertThat(received.get()).containsExactly(VALUE);
    }

    @Test
    @DisplayName("leaves the header off a release, which judges nothing")
    void absentOnTheRelease() {
        client(() -> VALUE).send(ActivationOperation.DEACTIVATE, prepared());

        assertThat(received.get()).isEmpty();
    }

    @Test
    @DisplayName("leaves the header off when the environment could observe nothing")
    void absentWhenBlank() {
        client(() -> " ").send(ActivationOperation.HEARTBEAT, prepared());

        assertThat(received.get()).isEmpty();
    }

    /**
     * @param discriminator what the application contributes, null for no bean at all
     * @return a client pointed at the local server
     */
    private LicenseActivationClient client(InstallationDiscriminator discriminator) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (discriminator != null) {
            beans.addBean("installationDiscriminator", discriminator);
        }
        ObjectProvider<ActivationServerDirectory> directory =
                beans.getBeanProvider(ActivationServerDirectory.class);
        ObjectProvider<ContactIdentity> contact = beans.getBeanProvider(ContactIdentity.class);
        return new LicenseActivationClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper(),
                directory, contact, beans.getBeanProvider(InstallationDiscriminator.class),
                mock(LicenseManager.class));
    }

    /**
     * @return a request with an empty body, which the server above accepts whatever it says
     */
    private PreparedRequest prepared() {
        return new PreparedRequest("POST", "/api/v1/activations", "{}", Map.of(), "nonce");
    }
}

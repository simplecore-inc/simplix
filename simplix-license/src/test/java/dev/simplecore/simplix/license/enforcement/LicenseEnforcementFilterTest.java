package dev.simplecore.simplix.license.enforcement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.license.LicenseTestFixture;
import dev.simplecore.simplix.license.config.LicenseProperties;
import dev.simplecore.simplix.license.core.LicenseAuditTrail;
import dev.simplecore.simplix.license.core.LicenseManager;
import dev.simplecore.simplix.license.integrity.RuntimeIntegrityChecker;
import dev.simplecore.simplix.license.model.LicenseState;
import dev.accesscore.license.sdk.gate.LicenseGate;
import dev.accesscore.license.sdk.model.LicenseModel.EvaluationResponse;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.model.LicenseStatus;
import dev.accesscore.license.sdk.spi.LicenseSpi.AuditRecorder;
import dev.accesscore.license.sdk.spi.LicenseSpi.LicenseStore;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The HTTP state gate.
 *
 * <p>What matters here is which states keep serving. A deployment running above its entitled
 * release must still answer — the doors it controls do not stop opening because a contract caps
 * the version, and what it loses is the paid modules the feature gates deny on their own —
 * while a license that has run out, or that belongs to another product, must not.
 */
@DisplayName("License enforcement filter")
class LicenseEnforcementFilterTest {

    private static final String API_PREFIX = "/api/v1";

    private LicenseGate gate;
    private LicenseState state;
    private LicenseManager manager;
    private LicenseEnforcementFilter filter;
    private StaticMessageSource messages;

    @BeforeEach
    void setUp() {
        gate = new LicenseGate();
        state = new LicenseState(gate);
        messages = new StaticMessageSource();

        LicenseProperties properties = new LicenseProperties();
        // A path that does not exist, so no token is imported from disk.
        properties.setTokenPath("./build/no-such-license.key");

        manager = new LicenseManager(properties,
                dev.accesscore.license.sdk.LicenseManager
                        .builder(new EmptyLicenseStore(), LicenseTestFixture.identity(),
                                LicenseTestFixture.keys())
                        .gate(gate)
                        .build(),
                new RuntimeIntegrityChecker(null),
                state,
                new LicenseAuditTrail(noRecorder()),
                "SHA256:test");

        // The response envelope carries an instant, so the mapper needs the same time module
        // Spring hands the filter in production.
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new LicenseEnforcementFilter(manager, objectMapper, messages, API_PREFIX);
    }

    /**
     * @param status the status to publish
     */
    private void publish(LicenseStatus status) {
        gate.refresh(new EvaluationResponse(status, status.isUsable(), status.errorCode(),
                status.isUsable() ? null : new dev.accesscore.license.sdk.model.LicenseModel
                        .Denial("error.license.notValid", List.of()),
                null, List.of(), Map.of(), null, null, null));
    }

    /**
     * @param method the HTTP method
     * @param path the request path
     * @return the response the filter produced
     */
    private MockHttpServletResponse serve(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("A valid license serves every request")
    void validServesEverything() throws Exception {
        publish(LicenseStatus.VALID);
        assertThat(serve("POST", API_PREFIX + "/doors").getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("A release above the ceiling keeps the doors open")
    void aboveTheCeilingKeepsServing() throws Exception {
        // The ceiling is an entitlement control. Closing the gate here would stop access
        // control at the doors, which is not what was sold or withheld.
        publish(LicenseStatus.RELEASE_NOT_ENTITLED);
        assertThat(serve("POST", API_PREFIX + "/doors").getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("A grace period in read-only mode serves reads and refuses writes")
    void gracePeriodIsReadOnly() throws Exception {
        publish(LicenseStatus.GRACE_PERIOD);
        assertThat(serve("GET", API_PREFIX + "/doors").getStatus())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(serve("POST", API_PREFIX + "/doors").getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("An expired license refuses, naming the reason as a licensing one")
    void expiredRefuses() throws Exception {
        messages.addMessage("error.license.notValid", Locale.getDefault(), "not valid here");
        publish(LicenseStatus.EXPIRED);

        MockHttpServletResponse response = serve("GET", API_PREFIX + "/doors");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        // The code is what lets a client offer the license screen rather than a permissions
        // message, so it has to say licensing rather than authorization.
        assertThat(response.getContentAsString()).contains("LICENSE_EXPIRED");
    }

    @Test
    @DisplayName("The license surface stays reachable while unlicensed")
    void theLicenseSurfaceStaysReachable() throws Exception {
        publish(LicenseStatus.NOT_ACTIVATED);
        assertThat(serve("GET", API_PREFIX + "/deployment-license/status").getStatus())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(serve("POST", API_PREFIX + "/deployment-license/activate").getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("Signing in stays reachable while unlicensed")
    void signingInStaysReachable() throws Exception {
        // Gating authentication would make an unlicensed deployment unrecoverable: the operator
        // could not sign in because no license is registered, and could not register one
        // because signing in was refused.
        publish(LicenseStatus.NOT_ACTIVATED);
        assertThat(serve("POST", API_PREFIX + "/auth/token/issue").getStatus())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(serve("POST", API_PREFIX + "/auth/token/refresh").getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("An endpoint added under the auth prefix is not permitted by accident")
    void onlyTheNamedAuthEndpointsArePermitted() throws Exception {
        publish(LicenseStatus.NOT_ACTIVATED);
        assertThat(serve("POST", API_PREFIX + "/auth/token/something-new").getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("First-run setup stays reachable while unlicensed")
    void setupStaysReachable() throws Exception {
        publish(LicenseStatus.NOT_ACTIVATED);
        assertThat(serve("GET", API_PREFIX + "/system/setup").getStatus())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(serve("GET", API_PREFIX + "/install/license").getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    /**
     * @return a provider that resolves no recorder, as a deployment contributing none has
     */
    private static ObjectProvider<AuditRecorder> noRecorder() {
        return new ObjectProvider<>() {
            @Override
            public AuditRecorder getObject() {
                throw new IllegalStateException("no recorder");
            }

            @Override
            public AuditRecorder getIfAvailable() {
                return null;
            }
        };
    }

    /** A store holding nothing, so the judgement reaches whatever the test published. */
    private static final class EmptyLicenseStore implements LicenseStore {

        @Override
        public Optional<RegistrationRecord> load() {
            return Optional.empty();
        }

        @Override
        public void save(RegistrationRecord record) {
            // Nothing is kept: these cases publish a status directly rather than through a
            // stored token.
        }

        @Override
        public void clear() {
            // Nothing to clear.
        }
    }
}

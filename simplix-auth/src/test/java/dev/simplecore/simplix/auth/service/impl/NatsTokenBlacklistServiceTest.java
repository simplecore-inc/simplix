package dev.simplecore.simplix.auth.service.impl;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import io.nats.client.Connection;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NatsTokenBlacklistService")
class NatsTokenBlacklistServiceTest {

    @Mock
    private Connection connection;

    @Mock
    private KeyValueManagement kvManagement;

    @Mock
    private KeyValue keyValue;

    @Mock
    private KeyValueStatus kvStatus;

    private SimpliXAuthProperties properties;
    private NatsTokenBlacklistService service;

    @BeforeEach
    void setUp() throws Exception {
        when(connection.keyValueManagement()).thenReturn(kvManagement);
        when(kvManagement.create(any())).thenReturn(kvStatus);
        when(connection.keyValue(anyString())).thenReturn(keyValue);

        properties = new SimpliXAuthProperties();
        properties.getToken().setEnableBlacklist(true);
        properties.getToken().setBlacklistStore(SimpliXAuthProperties.BlacklistStore.NATS);

        service = new NatsTokenBlacklistService(connection, properties);
        service.init();
    }

    @Nested
    @DisplayName("blacklist")
    class BlacklistTests {

        @Test
        @DisplayName("writes a present marker keyed by JTI")
        void writesMarkerForJti() throws Exception {
            service.blacklist("jti-1", Duration.ofMinutes(30));
            verify(keyValue).put(eq("jti-1"), any(byte[].class));
        }
    }

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklistedTests {

        @Test
        @DisplayName("returns true when KV entry exists with non-null value")
        void returnsTrueWhenEntryPresent() throws Exception {
            KeyValueEntry entry = mock(KeyValueEntry.class);
            when(entry.getValue()).thenReturn(new byte[]{'1'});
            when(keyValue.get("jti-1")).thenReturn(entry);

            assertThat(service.isBlacklisted("jti-1")).isTrue();
        }

        @Test
        @DisplayName("returns false when KV entry is missing")
        void returnsFalseWhenMissing() throws Exception {
            when(keyValue.get("jti-1")).thenReturn(null);
            assertThat(service.isBlacklisted("jti-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("FAIL_OPEN failure mode")
    class FailOpenTests {

        @Test
        @DisplayName("returns false on KV read error in FAIL_OPEN mode")
        void readErrorReturnsFalse() throws Exception {
            properties.getToken().setBlacklistFailureMode(
                    SimpliXAuthProperties.BlacklistFailureMode.FAIL_OPEN);
            when(keyValue.get(anyString())).thenThrow(new IOException("kaboom"));

            assertThat(service.isBlacklisted("jti-x")).isFalse();
        }

        @Test
        @DisplayName("swallows write error silently in FAIL_OPEN mode")
        void writeErrorSwallowed() throws Exception {
            properties.getToken().setBlacklistFailureMode(
                    SimpliXAuthProperties.BlacklistFailureMode.FAIL_OPEN);
            doThrowOnPut(new IOException("kaboom"));

            // Should not throw
            service.blacklist("jti-x", Duration.ofSeconds(60));
        }
    }

    @Nested
    @DisplayName("FAIL_CLOSED failure mode")
    class FailClosedTests {

        @Test
        @DisplayName("propagates read error in FAIL_CLOSED mode")
        void readErrorPropagates() throws Exception {
            properties.getToken().setBlacklistFailureMode(
                    SimpliXAuthProperties.BlacklistFailureMode.FAIL_CLOSED);
            when(keyValue.get(anyString())).thenThrow(new IOException("kaboom"));

            assertThatThrownBy(() -> service.isBlacklisted("jti-x"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private void doThrowOnPut(Throwable t) throws Exception {
        org.mockito.Mockito.doThrow(t).when(keyValue).put(anyString(), any(byte[].class));
    }
}

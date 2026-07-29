package dev.simplecore.simplix.stream.core.session;

import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A session that belongs to nobody.
 *
 * <p>Transports admit unauthenticated connections, and applications that stream to anonymous
 * clients create exactly these — a public status page, a checkout screen whose buyer has no
 * account yet. The model has to carry one without the ownership checks treating it as a fault.
 */
@DisplayName("Anonymous stream session")
class AnonymousSessionTest {

    @Test
    @DisplayName("is created with no user rather than refusing")
    void createsWithoutUser() {
        StreamSession session = StreamSession.create(null, TransportType.SSE);

        assertNull(session.getUserId());
        assertEquals(TransportType.SSE, session.getTransportType());
    }
}

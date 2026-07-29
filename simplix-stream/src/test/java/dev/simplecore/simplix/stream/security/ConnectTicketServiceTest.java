package dev.simplecore.simplix.stream.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ConnectTicketService.
 */
@DisplayName("ConnectTicketService")
class ConnectTicketServiceTest {

    private ConnectTicketService service;

    @BeforeEach
    void setUp() {
        service = new ConnectTicketService(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("redeems to the user it was issued for")
    void redeemsToItsUser() {
        String ticket = service.issue("user-1");

        assertEquals(Optional.of("user-1"), service.redeem(ticket));
    }

    @Test
    @DisplayName("redeems once and never again")
    void redeemsOnlyOnce() {
        String ticket = service.issue("user-1");

        assertTrue(service.redeem(ticket).isPresent());
        assertTrue(service.redeem(ticket).isEmpty(),
                "a spent ticket must not open a second stream");
    }

    @Test
    @DisplayName("refuses a ticket nobody issued")
    void refusesUnknownTicket() {
        assertTrue(service.redeem("not-a-ticket").isEmpty());
        assertTrue(service.redeem(null).isEmpty());
        assertTrue(service.redeem("  ").isEmpty());
    }

    @Test
    @DisplayName("refuses a ticket whose time has run out")
    void refusesExpiredTicket() throws InterruptedException {
        ConnectTicketService brief = new ConnectTicketService(Duration.ofMillis(20));
        String ticket = brief.issue("user-1");

        Thread.sleep(60);

        assertTrue(brief.redeem(ticket).isEmpty());
    }

    @Test
    @DisplayName("tells an anonymous ticket apart from no ticket at all")
    void distinguishesAnonymousFromMissing() {
        String anonymous = service.issue(null);

        // Present but empty: the ticket was real, and the client it stands for has no user.
        Optional<String> redeemed = service.redeem(anonymous);
        assertTrue(redeemed.isPresent());
        assertTrue(redeemed.get().isEmpty());
    }

    @Test
    @DisplayName("never issues the same ticket twice")
    void issuesDistinctTickets() {
        Set<String> issued = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            issued.add(service.issue("user-1"));
        }

        assertEquals(500, issued.size());
    }

    @Test
    @DisplayName("a ticket carries no readable identity")
    void ticketRevealsNothing() {
        String ticket = service.issue("alice@example.com");

        assertFalse(ticket.contains("alice"),
                "a ticket in a URL must not put the user it stands for into a log");
    }
}

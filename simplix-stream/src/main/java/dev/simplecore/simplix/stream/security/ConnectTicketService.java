package dev.simplecore.simplix.stream.security;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time tickets that let a browser open a stream it cannot authenticate directly.
 * <p>
 * {@code EventSource} cannot send request headers. An application whose session travels in one —
 * a bearer token, or a header of its own — therefore has no way to authenticate
 * {@code GET /api/stream/connect}, even though every other call it makes is authenticated. The
 * usual workaround is to put the session in the query string, which writes it into every access
 * log, proxy log, and browser history entry between the client and this server.
 * <p>
 * A ticket is the alternative. The client asks for one over an ordinary authenticated request,
 * and hands that back on the connect URL instead of its session. A ticket is single-use, expires
 * in seconds rather than hours, and identifies nothing beyond the user it was minted for — so a
 * leaked one buys an attacker a stream that is already closed.
 * <p>
 * Held in this instance's memory. In distributed mode a client must therefore redeem its ticket
 * against the instance that minted it, which is what an ordinary sticky load balancer already
 * does — and a redemption that misses simply fails closed, leaving the client to ask for another.
 */
@Slf4j
public class ConnectTicketService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Bytes of entropy per ticket. */
    private static final int TICKET_BYTES = 32;

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Duration validity;

    /**
     * @param validity how long a ticket may be redeemed for; seconds, not hours
     */
    public ConnectTicketService(Duration validity) {
        this.validity = validity;
    }

    /**
     * Mints a ticket for whoever is asking.
     *
     * @param userId the authenticated user the ticket stands for, or null for an anonymous one
     * @return the ticket, which is handed out once and never stored anywhere else
     */
    public String issue(String userId) {
        // Opportunistic: a ticket is short-lived, and clearing on issue keeps the map bounded
        // without a scheduler of its own.
        purgeExpired();

        byte[] bytes = new byte[TICKET_BYTES];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Ticket(userId, Instant.now().plus(validity)));
        return ticket;
    }

    /**
     * Redeems a ticket, which can happen exactly once.
     *
     * @param ticket what the client presented, or null when it presented nothing
     * @return who it stands for, or empty when it is unknown, spent, or expired; note that a
     *         ticket minted for an anonymous client redeems to an empty user id, not to empty
     */
    public Optional<String> redeem(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        // Removed as it is read, so two connections cannot share one ticket.
        Ticket found = tickets.remove(ticket);
        if (found == null || found.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(found.userId() == null ? "" : found.userId());
    }

    /**
     * Drops tickets nobody redeemed in time.
     */
    private void purgeExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    /**
     * @param userId who the ticket stands for
     * @param expiresAt when it stops being redeemable
     */
    private record Ticket(String userId, Instant expiresAt) {
    }
}

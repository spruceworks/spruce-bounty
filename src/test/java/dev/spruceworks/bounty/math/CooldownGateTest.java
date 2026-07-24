package dev.spruceworks.bounty.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CooldownGateTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    @Test
    void nullExpiryIsNeverActive() {
        assertFalse(CooldownGate.isActive(null, NOW));
    }

    @Test
    void expiryInTheFutureIsActive() {
        assertTrue(CooldownGate.isActive(NOW.plus(Duration.ofMinutes(10)), NOW));
    }

    @Test
    void expiryInThePastIsNotActive() {
        assertFalse(CooldownGate.isActive(NOW.minus(Duration.ofMinutes(1)), NOW));
    }

    @Test
    void expiryExactlyNowIsNotActive() {
        assertFalse(CooldownGate.isActive(NOW, NOW));
    }

    @Test
    void expiryAfterAddsDurationToNow() {
        assertEquals(Instant.parse("2026-07-24T12:30:00Z"), CooldownGate.expiryAfter(NOW, Duration.ofMinutes(30)));
    }
}

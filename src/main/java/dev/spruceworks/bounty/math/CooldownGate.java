package dev.spruceworks.bounty.math;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure logic for the per killer-victim claim cooldown (blunts alt/farm
 * trading — STRATEGY.md §3). No Bukkit dependency, so it is directly
 * unit-testable.
 */
public final class CooldownGate {

    private CooldownGate() {
    }

    public static boolean isActive(Instant expiresAt, Instant now) {
        return expiresAt != null && now.isBefore(expiresAt);
    }

    public static Instant expiryAfter(Instant now, Duration cooldown) {
        return now.plus(cooldown);
    }
}

package dev.spruceworks.bounty.model;

import java.time.Instant;
import java.util.UUID;

/** An active claim cooldown for one killer against one victim. */
public record CooldownEntry(UUID killer, UUID victim, Instant expiresAt) {
}

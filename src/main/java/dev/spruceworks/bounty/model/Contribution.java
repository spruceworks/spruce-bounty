package dev.spruceworks.bounty.model;

import java.time.Instant;
import java.util.UUID;

/** One placer's running total on a single target's bounty. */
public record Contribution(UUID placer, double amount, Instant placedAt) {
}

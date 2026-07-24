package dev.spruceworks.bounty.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All contributions currently placed on one target player. Mutated only from
 * the main thread by BountyService; persistence happens separately via
 * BountyStorage. The contribution map is concurrent because reads (GUI,
 * PlaceholderAPI) may come from other threads.
 */
public final class Bounty {

    private final UUID target;
    private final Map<UUID, Contribution> contributions = new ConcurrentHashMap<>();
    private final Instant firstPlacedAt;
    private volatile Instant lastUpdatedAt;

    public Bounty(UUID target, Instant firstPlacedAt, Instant lastUpdatedAt) {
        this.target = target;
        this.firstPlacedAt = firstPlacedAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public UUID target() {
        return this.target;
    }

    public Instant firstPlacedAt() {
        return this.firstPlacedAt;
    }

    public Instant lastUpdatedAt() {
        return this.lastUpdatedAt;
    }

    public Collection<Contribution> contributions() {
        return this.contributions.values();
    }

    public Contribution contributionOf(UUID placer) {
        return this.contributions.get(placer);
    }

    public double total() {
        return this.contributions.values().stream().mapToDouble(Contribution::amount).sum();
    }

    public int contributorCount() {
        return this.contributions.size();
    }

    public boolean isEmpty() {
        return this.contributions.isEmpty();
    }

    /** Adds to an existing contribution from this placer, or creates a new one. */
    public void addContribution(UUID placer, double amount, Instant now) {
        this.contributions.merge(placer, new Contribution(placer, amount, now),
                (existing, added) -> new Contribution(placer, existing.amount() + added.amount(), now));
        this.lastUpdatedAt = now;
    }

    /**
     * Rebuilds a contribution exactly as loaded from storage, without the
     * {@link #addContribution} side effect of bumping lastUpdatedAt — the
     * caller already restored the authoritative timestamp from the row.
     */
    public void restoreContribution(UUID placer, double amount, Instant placedAt) {
        this.contributions.put(placer, new Contribution(placer, amount, placedAt));
    }

    /** Removes a placer's contribution entirely (used by cancel and admin remove). */
    public Contribution removeContribution(UUID placer, Instant now) {
        Contribution removed = this.contributions.remove(placer);
        if (removed != null) {
            this.lastUpdatedAt = now;
        }
        return removed;
    }
}

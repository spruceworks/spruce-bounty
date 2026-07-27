package dev.spruceworks.bounty.storage;

import dev.spruceworks.bounty.model.Bounty;
import dev.spruceworks.bounty.model.CooldownEntry;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Persistence for bounties and claim cooldowns. SQLite is the only
 * implementation today (see SqliteBountyStorage); a MySQL implementation is
 * a config-driven swap behind this interface, not a rewrite.
 *
 * <p>{@link #open()} and the load methods run once during plugin startup.
 * Every other method is called off the main thread via the scheduler
 * wrapper — implementations must be safe to call from another thread.
 */
public interface BountyStorage {

    void open();

    void close();

    Collection<Bounty> loadAllBounties();

    Collection<CooldownEntry> loadAllCooldowns();

    /** Upserts the bounty row and the named placer's contribution row. */
    void saveContribution(Bounty bounty, UUID placer);

    void deleteContribution(UUID target, UUID placer);

    /** Deletes the bounty and all of its contributions. */
    void deleteBounty(UUID target);

    void deleteAllBounties();

    void saveCooldown(UUID killer, UUID victim, Instant expiresAt);

    void deleteCooldown(UUID killer, UUID victim);
}

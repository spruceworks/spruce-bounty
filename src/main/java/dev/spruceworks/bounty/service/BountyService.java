package dev.spruceworks.bounty.service;

import dev.spruceworks.bounty.config.ConfigManager;
import dev.spruceworks.bounty.math.BountyMath;
import dev.spruceworks.bounty.math.CooldownGate;
import dev.spruceworks.bounty.model.Bounty;
import dev.spruceworks.bounty.model.Contribution;
import dev.spruceworks.bounty.model.CooldownEntry;
import dev.spruceworks.bounty.service.BountyOutcome.AdminRemoveResult;
import dev.spruceworks.bounty.service.BountyOutcome.AdminRemoveStatus;
import dev.spruceworks.bounty.service.BountyOutcome.CancelResult;
import dev.spruceworks.bounty.service.BountyOutcome.CancelStatus;
import dev.spruceworks.bounty.service.BountyOutcome.ClaimResult;
import dev.spruceworks.bounty.service.BountyOutcome.ClaimStatus;
import dev.spruceworks.bounty.service.BountyOutcome.PlaceResult;
import dev.spruceworks.bounty.service.BountyOutcome.PlaceStatus;
import dev.spruceworks.bounty.storage.BountyStorage;
import dev.spruceworks.bounty.util.SchedulerAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

/**
 * Core bounty business logic: the in-memory cache, Vault economy calls, and
 * persistence orchestration. Economy calls run on the calling thread (always
 * the main thread here — commands and the death listener) because most
 * Economy implementations behind Vault are not thread-safe; storage writes
 * are dispatched off-thread via the scheduler wrapper.
 */
public final class BountyService {

    private final ConfigManager configManager;
    private final BountyStorage storage;
    private final SchedulerAdapter scheduler;
    private final Economy economy;
    private final AntiAbuseService antiAbuse;

    private final Map<UUID, Bounty> bounties = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Instant>> cooldowns = new ConcurrentHashMap<>();

    public BountyService(ConfigManager configManager, BountyStorage storage, SchedulerAdapter scheduler,
                          Economy economy, AntiAbuseService antiAbuse) {
        this.configManager = configManager;
        this.storage = storage;
        this.scheduler = scheduler;
        this.economy = economy;
        this.antiAbuse = antiAbuse;
    }

    /** Loads all bounties and cooldowns from storage. Runs once, synchronously, during onEnable. */
    public void loadFromStorage() {
        this.storage.loadAllBounties().forEach(bounty -> this.bounties.put(bounty.target(), bounty));
        for (CooldownEntry entry : this.storage.loadAllCooldowns()) {
            this.cooldowns.computeIfAbsent(entry.killer(), k -> new ConcurrentHashMap<>())
                    .put(entry.victim(), entry.expiresAt());
        }
    }

    public Optional<Bounty> get(UUID target) {
        return Optional.ofNullable(this.bounties.get(target));
    }

    public List<Bounty> topByAmount(int limit) {
        return this.bounties.values().stream()
                .sorted(Comparator.comparingDouble(Bounty::total).reversed())
                .limit(limit)
                .toList();
    }

    /** All bounties, for GUI paging. */
    public List<Bounty> allSorted(boolean newestFirst) {
        Comparator<Bounty> comparator = newestFirst
                ? Comparator.comparing(Bounty::lastUpdatedAt).reversed()
                : Comparator.comparingDouble(Bounty::total).reversed();
        List<Bounty> all = new ArrayList<>(this.bounties.values());
        all.sort(comparator);
        return all;
    }

    public PlaceResult place(Player placer, Player target, double amount) {
        if (this.antiAbuse.isSelfBounty(placer, target)) {
            return new PlaceResult(PlaceStatus.SELF, 0);
        }
        if (this.antiAbuse.isImmune(target)) {
            return new PlaceResult(PlaceStatus.IMMUNE, 0);
        }
        double min = this.configManager.config().getDouble("economy.min-amount");
        double max = this.configManager.config().getDouble("economy.max-amount");
        if (amount < min) {
            return new PlaceResult(PlaceStatus.BELOW_MIN, min);
        }
        if (amount > max) {
            return new PlaceResult(PlaceStatus.ABOVE_MAX, max);
        }
        if (!this.economy.has(placer, amount)) {
            return new PlaceResult(PlaceStatus.INSUFFICIENT_FUNDS, 0);
        }
        if (!this.economy.withdrawPlayer(placer, amount).transactionSuccess()) {
            return new PlaceResult(PlaceStatus.ECONOMY_UNAVAILABLE, 0);
        }

        double taxPercent = this.configManager.config().getDouble("economy.placement-tax-percent");
        double potAmount = BountyMath.potContribution(amount, taxPercent);
        Instant now = Instant.now();
        Bounty bounty = this.bounties.computeIfAbsent(target.getUniqueId(), id -> new Bounty(id, now, now));
        bounty.addContribution(placer.getUniqueId(), potAmount, now);
        this.scheduler.runAsync(() -> this.storage.saveContribution(bounty, placer.getUniqueId()));
        return new PlaceResult(PlaceStatus.SUCCESS, potAmount);
    }

    public CancelResult cancel(Player sender, UUID target) {
        Bounty bounty = this.bounties.get(target);
        Contribution contribution = bounty == null ? null : bounty.contributionOf(sender.getUniqueId());
        if (contribution == null) {
            return new CancelResult(CancelStatus.NOT_FOUND, 0);
        }
        double refundPercent = this.configManager.config().getDouble("cancel.refund-percent");
        double refund = BountyMath.refundAmount(contribution.amount(), refundPercent);
        if (refund > 0 && !this.economy.depositPlayer(sender, refund).transactionSuccess()) {
            return new CancelResult(CancelStatus.ECONOMY_UNAVAILABLE, 0);
        }

        bounty.removeContribution(sender.getUniqueId(), Instant.now());
        UUID placerId = sender.getUniqueId();
        if (bounty.isEmpty()) {
            this.bounties.remove(target);
            this.scheduler.runAsync(() -> this.storage.deleteBounty(target));
        } else {
            this.scheduler.runAsync(() -> this.storage.deleteContribution(target, placerId));
        }
        return new CancelResult(CancelStatus.SUCCESS, refund);
    }

    public AdminRemoveResult adminRemove(UUID target) {
        Bounty removed = this.bounties.remove(target);
        if (removed == null) {
            return new AdminRemoveResult(AdminRemoveStatus.NOT_FOUND);
        }
        this.scheduler.runAsync(() -> this.storage.deleteBounty(target));
        return new AdminRemoveResult(AdminRemoveStatus.SUCCESS);
    }

    /** @return how many bounties were cleared */
    public int adminClear() {
        int count = this.bounties.size();
        this.bounties.clear();
        this.scheduler.runAsync(this.storage::deleteAllBounties);
        return count;
    }

    public ClaimResult claim(Player killer, Player victim) {
        Bounty bounty = this.bounties.get(victim.getUniqueId());
        if (bounty == null) {
            return new ClaimResult(ClaimStatus.NO_BOUNTY, 0);
        }
        if (this.antiAbuse.isSameAddressKill(killer, victim)) {
            return new ClaimResult(ClaimStatus.SAME_IP_BLOCKED, 0);
        }

        Instant now = Instant.now();
        Instant cooldownExpiry = this.cooldowns.getOrDefault(killer.getUniqueId(), Map.of()).get(victim.getUniqueId());
        if (CooldownGate.isActive(cooldownExpiry, now)) {
            return new ClaimResult(ClaimStatus.COOLDOWN, 0);
        }

        double total = bounty.total();
        if (total > 0 && !this.economy.depositPlayer(killer, total).transactionSuccess()) {
            return new ClaimResult(ClaimStatus.ECONOMY_UNAVAILABLE, 0);
        }

        this.bounties.remove(victim.getUniqueId());
        this.scheduler.runAsync(() -> this.storage.deleteBounty(victim.getUniqueId()));

        int cooldownMinutes = this.configManager.config().getInt("anti-abuse.claim-cooldown-minutes");
        Instant expiry = CooldownGate.expiryAfter(now, Duration.ofMinutes(cooldownMinutes));
        this.cooldowns.computeIfAbsent(killer.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(victim.getUniqueId(), expiry);
        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();
        this.scheduler.runAsync(() -> this.storage.saveCooldown(killerId, victimId, expiry));

        return new ClaimResult(ClaimStatus.PAID, total);
    }

    /** Periodic hygiene: drops expired cooldown entries so the map and table don't grow forever. */
    public void sweepExpiredCooldowns() {
        Instant now = Instant.now();
        for (Map.Entry<UUID, Map<UUID, Instant>> byKiller : this.cooldowns.entrySet()) {
            UUID killerId = byKiller.getKey();
            byKiller.getValue().entrySet().removeIf(byVictim -> {
                if (CooldownGate.isActive(byVictim.getValue(), now)) {
                    return false;
                }
                UUID victimId = byVictim.getKey();
                this.scheduler.runAsync(() -> this.storage.deleteCooldown(killerId, victimId));
                return true;
            });
        }
    }
}

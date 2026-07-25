package dev.spruceworks.bounty.listener;

import dev.spruceworks.bounty.SpruceBountyPlugin;
import dev.spruceworks.bounty.service.BountyOutcome.ClaimResult;
import dev.spruceworks.bounty.service.BountyOutcome.ClaimStatus;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Pays out a bounty when its target is killed by another player. Non-player
 * kills never pay out: {@link PlayerDeathEvent#getKiller()} is only non-null
 * for PvP kills, so there is nothing extra to check here for that rule.
 * MONITOR priority: this only observes the death and reacts, it never
 * modifies the event, so it runs after every other plugin has had its say.
 */
public final class BountyClaimListener implements Listener {

    private final SpruceBountyPlugin plugin;

    public BountyClaimListener(SpruceBountyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }

        ClaimResult result = this.plugin.bountyService().claim(killer, victim);
        if (result.status() != ClaimStatus.PAID) {
            return;
        }

        TagResolver killerTag = Placeholder.unparsed("killer", killer.getName());
        TagResolver victimTag = Placeholder.unparsed("victim", victim.getName());
        TagResolver amountTag = Placeholder.unparsed("amount", this.plugin.economy().format(result.amount()));

        // Players who turned bounty broadcasts off in /settings are skipped.
        // Without SpruceSettings installed this always returns true, so the
        // behaviour is unchanged from a plain SpruceBounty install.
        Bukkit.getOnlinePlayers().stream()
                .filter(viewer -> this.plugin.settingsHook().wantsBroadcasts(viewer.getUniqueId()))
                .forEach(viewer ->
                        this.plugin.messages().send(viewer, "claim-broadcast", killerTag, victimTag, amountTag));

        if (this.plugin.configManager().config().getBoolean("claim.broadcast-title")) {
            Title title = Title.title(
                    this.plugin.messages().get("claim-broadcast-title", killerTag, victimTag, amountTag),
                    this.plugin.messages().get("claim-broadcast-subtitle", killerTag, victimTag, amountTag));
            Bukkit.getOnlinePlayers().stream()
                    .filter(viewer -> this.plugin.settingsHook().wantsBroadcasts(viewer.getUniqueId()))
                    .forEach(viewer -> viewer.showTitle(title));
        }
    }
}

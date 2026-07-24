package dev.spruceworks.bounty.service;

import dev.spruceworks.bounty.config.ConfigManager;
import org.bukkit.entity.Player;

/** Placement- and claim-time abuse checks (self-bounty, immunity, IP sharing). */
public final class AntiAbuseService {

    private final ConfigManager configManager;

    public AntiAbuseService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean isSelfBounty(Player placer, Player target) {
        return placer.getUniqueId().equals(target.getUniqueId());
    }

    public boolean isImmune(Player target) {
        return target.hasPermission("sprucebounty.immune");
    }

    /** True if the kill should be ignored because killer and victim share an IP (config-gated, default on). */
    public boolean isSameAddressKill(Player killer, Player victim) {
        if (!this.configManager.config().getBoolean("anti-abuse.ignore-same-ip-kills", true)) {
            return false;
        }
        return killer.getAddress() != null && victim.getAddress() != null
                && killer.getAddress().getAddress() != null
                && killer.getAddress().getAddress().equals(victim.getAddress().getAddress());
    }
}

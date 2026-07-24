package dev.spruceworks.bounty.placeholder;

import dev.spruceworks.bounty.SpruceBountyPlugin;
import dev.spruceworks.bounty.model.Bounty;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * %sprucebounty_own% / %sprucebounty_top_name% / %sprucebounty_top_amount%.
 * Registered only when PlaceholderAPI is present — see SpruceBountyPlugin.
 */
public final class SprucePlaceholderExpansion extends PlaceholderExpansion {

    private final SpruceBountyPlugin plugin;

    public SprucePlaceholderExpansion(SpruceBountyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "sprucebounty";
    }

    @Override
    public String getAuthor() {
        return "SpruceWorks";
    }

    @Override
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "own" -> player == null ? "" : this.plugin.economy().format(
                    this.plugin.bountyService().get(player.getUniqueId()).map(Bounty::total).orElse(0.0));
            case "top_name" -> topEntry().map(this::targetName).orElse("");
            case "top_amount" -> topEntry().map(bounty -> this.plugin.economy().format(bounty.total())).orElse("");
            default -> null;
        };
    }

    private Optional<Bounty> topEntry() {
        List<Bounty> top = this.plugin.bountyService().topByAmount(1);
        return top.isEmpty() ? Optional.empty() : Optional.of(top.get(0));
    }

    private String targetName(Bounty bounty) {
        String name = Bukkit.getOfflinePlayer(bounty.target()).getName();
        return name != null ? name : bounty.target().toString();
    }
}

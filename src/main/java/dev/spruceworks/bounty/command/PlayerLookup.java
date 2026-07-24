package dev.spruceworks.bounty.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Player name resolution and tab-completion shared by /bounty and
 * /bountyadmin. Name lookups only ever use the UUID-keyed
 * {@link Bukkit#getOfflinePlayer(UUID)}, which is a local, non-blocking
 * lookup — never the String-name overload, which can silently block the
 * calling thread on a Mojang API call for unseen names.
 */
final class PlayerLookup {

    private PlayerLookup() {
    }

    static CompletableFuture<Suggestions> onlinePlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }

    /** Online players plus display names for the given bounty target UUIDs. */
    static CompletableFuture<Suggestions> bountyTargets(SuggestionsBuilder builder, Iterable<UUID> targets) {
        String remaining = builder.getRemainingLowerCase();
        Set<String> suggested = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(remaining) && suggested.add(player.getName())) {
                builder.suggest(player.getName());
            }
        }
        for (UUID target : targets) {
            String name = Bukkit.getOfflinePlayer(target).getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(remaining) && suggested.add(name)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    /** An online player's UUID, else a known bounty target UUID with a matching name; null if neither. */
    static UUID resolveKnownTarget(String name, Iterable<UUID> knownTargets) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (UUID candidate : knownTargets) {
            if (name.equalsIgnoreCase(Bukkit.getOfflinePlayer(candidate).getName())) {
                return candidate;
            }
        }
        return null;
    }

    static String displayName(UUID target, String fallback) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        String name = offline.getName();
        return name != null ? name : (fallback != null ? fallback : target.toString());
    }
}

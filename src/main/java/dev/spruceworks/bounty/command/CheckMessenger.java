package dev.spruceworks.bounty.command;

import dev.spruceworks.bounty.SpruceBountyPlugin;
import dev.spruceworks.bounty.model.Bounty;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

/** Shared "send a player's current bounty total" logic for /bounty check and the GUI click handler. */
public final class CheckMessenger {

    private CheckMessenger() {
    }

    public static void send(SpruceBountyPlugin plugin, CommandSender sender, UUID target, String fallbackName) {
        String displayName = PlayerLookup.displayName(target, fallbackName);
        Optional<Bounty> bounty = plugin.bountyService().get(target);
        if (bounty.isEmpty()) {
            plugin.messages().send(sender, "check-none", Placeholder.unparsed("target", displayName));
        } else {
            plugin.messages().send(sender, "check-result",
                    Placeholder.unparsed("target", displayName),
                    Placeholder.unparsed("amount", plugin.economy().format(bounty.get().total())));
        }
    }
}

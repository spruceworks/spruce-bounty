package dev.spruceworks.bounty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.spruceworks.bounty.SpruceBountyPlugin;
import dev.spruceworks.bounty.gui.BountyListGui;
import dev.spruceworks.bounty.model.Bounty;
import dev.spruceworks.bounty.service.BountyOutcome.CancelResult;
import dev.spruceworks.bounty.service.BountyOutcome.PlaceResult;
import dev.spruceworks.bounty.service.BountyService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /bounty} — set, list, check, top, cancel. */
public final class BountyCommand {

    private final SpruceBountyPlugin plugin;
    private final BountyService bountyService;
    private final Economy economy;

    private BountyCommand(SpruceBountyPlugin plugin) {
        this.plugin = plugin;
        this.bountyService = plugin.bountyService();
        this.economy = plugin.economy();
    }

    public static void register(SpruceBountyPlugin plugin) {
        BountyCommand command = new BountyCommand(plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "SpruceBounty commands"));
    }

    private LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("bounty")
                .requires(source -> source.getSender().hasPermission("sprucebounty.set")
                        || source.getSender().hasPermission("sprucebounty.list")
                        || source.getSender().hasPermission("sprucebounty.check")
                        || source.getSender().hasPermission("sprucebounty.top")
                        || source.getSender().hasPermission("sprucebounty.cancel"))
                .executes(this::usage)
                .then(Commands.literal("set")
                        .requires(source -> source.getSender().hasPermission("sprucebounty.set"))
                        .then(playerArgument("target")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .executes(this::set))))
                .then(Commands.literal("list")
                        .requires(source -> source.getSender().hasPermission("sprucebounty.list"))
                        .executes(this::list))
                .then(Commands.literal("check")
                        .requires(source -> source.getSender().hasPermission("sprucebounty.check"))
                        .executes(this::checkSelf)
                        .then(targetArgument("target").executes(this::checkOther)))
                .then(Commands.literal("top")
                        .requires(source -> source.getSender().hasPermission("sprucebounty.top"))
                        .executes(this::top))
                .then(Commands.literal("cancel")
                        .requires(source -> source.getSender().hasPermission("sprucebounty.cancel"))
                        .then(targetArgument("target").executes(this::cancel)))
                // Implicit "/bounty <player> <amount>" == set. A player literally named "set",
                // "list", "check", "top", or "cancel" needs the explicit "/bounty set <name> <amt>" form.
                .then(playerArgument("target")
                        .requires(source -> source.getSender().hasPermission("sprucebounty.set"))
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(this::set)))
                .build();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> playerArgument(String name) {
        return Commands.argument(name, StringArgumentType.word())
                .suggests(PlayerLookup::onlinePlayers);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> targetArgument(String name) {
        return Commands.argument(name, StringArgumentType.word())
                .suggests((context, builder) -> PlayerLookup.bountyTargets(builder, knownTargets()));
    }

    private Iterable<UUID> knownTargets() {
        return this.bountyService.allSorted(true).stream().map(Bounty::target).toList();
    }

    private int usage(CommandContext<CommandSourceStack> context) {
        this.plugin.messages().send(context.getSource().getSender(), "usage-bounty");
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player placer)) {
            this.plugin.messages().send(sender, "players-only");
            return Command.SINGLE_SUCCESS;
        }
        String name = StringArgumentType.getString(context, "target");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            this.plugin.messages().send(sender, "player-not-found", Placeholder.unparsed("target", name));
            return Command.SINGLE_SUCCESS;
        }

        PlaceResult result = this.bountyService.place(placer, target, amount);
        switch (result.status()) {
            case SUCCESS -> this.plugin.messages().send(sender, "set-success",
                    Placeholder.unparsed("target", target.getName()),
                    Placeholder.unparsed("amount", this.economy.format(result.amount())));
            case SELF -> this.plugin.messages().send(sender, "set-self");
            case IMMUNE -> this.plugin.messages().send(sender, "set-immune",
                    Placeholder.unparsed("target", target.getName()));
            case BELOW_MIN -> this.plugin.messages().send(sender, "set-below-min",
                    Placeholder.unparsed("amount", this.economy.format(result.amount())));
            case ABOVE_MAX -> this.plugin.messages().send(sender, "set-above-max",
                    Placeholder.unparsed("amount", this.economy.format(result.amount())));
            case INSUFFICIENT_FUNDS -> this.plugin.messages().send(sender, "set-insufficient-funds",
                    Placeholder.unparsed("amount", this.economy.format(amount)));
            case ECONOMY_UNAVAILABLE -> this.plugin.messages().send(sender, "economy-error");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "players-only");
            return Command.SINGLE_SUCCESS;
        }
        new BountyListGui(this.plugin, player).open();
        return Command.SINGLE_SUCCESS;
    }

    private int checkSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "players-only");
            return Command.SINGLE_SUCCESS;
        }
        CheckMessenger.send(this.plugin, sender, player.getUniqueId(), player.getName());
        return Command.SINGLE_SUCCESS;
    }

    private int checkOther(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String name = StringArgumentType.getString(context, "target");
        UUID target = PlayerLookup.resolveKnownTarget(name, knownTargets());
        if (target == null) {
            this.plugin.messages().send(sender, "player-not-found", Placeholder.unparsed("target", name));
            return Command.SINGLE_SUCCESS;
        }
        CheckMessenger.send(this.plugin, sender, target, name);
        return Command.SINGLE_SUCCESS;
    }

    private int top(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        var top = this.bountyService.topByAmount(10);
        if (top.isEmpty()) {
            this.plugin.messages().send(sender, "top-empty");
            return Command.SINGLE_SUCCESS;
        }
        this.plugin.messages().send(sender, "top-header");
        int rank = 1;
        for (Bounty bounty : top) {
            this.plugin.messages().send(sender, "top-entry",
                    Placeholder.unparsed("rank", String.valueOf(rank++)),
                    Placeholder.unparsed("target", PlayerLookup.displayName(bounty.target(), null)),
                    Placeholder.unparsed("amount", this.economy.format(bounty.total())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int cancel(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "players-only");
            return Command.SINGLE_SUCCESS;
        }
        String name = StringArgumentType.getString(context, "target");
        UUID target = PlayerLookup.resolveKnownTarget(name, knownTargets());
        if (target == null) {
            this.plugin.messages().send(sender, "player-not-found", Placeholder.unparsed("target", name));
            return Command.SINGLE_SUCCESS;
        }
        CancelResult result = this.bountyService.cancel(player, target);
        String displayName = PlayerLookup.displayName(target, name);
        switch (result.status()) {
            case SUCCESS -> this.plugin.messages().send(sender, "cancel-success",
                    Placeholder.unparsed("target", displayName),
                    Placeholder.unparsed("refund", this.economy.format(result.refunded())));
            case NOT_FOUND -> this.plugin.messages().send(sender, "cancel-not-found",
                    Placeholder.unparsed("target", displayName));
            case ECONOMY_UNAVAILABLE -> this.plugin.messages().send(sender, "economy-error");
        }
        return Command.SINGLE_SUCCESS;
    }
}

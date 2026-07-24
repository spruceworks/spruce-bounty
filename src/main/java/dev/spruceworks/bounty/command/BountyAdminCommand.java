package dev.spruceworks.bounty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.spruceworks.bounty.SpruceBountyPlugin;
import dev.spruceworks.bounty.model.Bounty;
import dev.spruceworks.bounty.service.BountyOutcome.AdminClearResult;
import dev.spruceworks.bounty.service.BountyOutcome.AdminRemoveResult;
import dev.spruceworks.bounty.service.BountyService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

/** {@code /bountyadmin} — remove, clear, reload. */
public final class BountyAdminCommand {

    private final SpruceBountyPlugin plugin;
    private final BountyService bountyService;

    private BountyAdminCommand(SpruceBountyPlugin plugin) {
        this.plugin = plugin;
        this.bountyService = plugin.bountyService();
    }

    public static void register(SpruceBountyPlugin plugin) {
        BountyAdminCommand command = new BountyAdminCommand(plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "SpruceBounty admin commands"));
    }

    private LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("bountyadmin")
                .requires(source -> source.getSender().hasPermission("sprucebounty.admin"))
                .executes(this::usage)
                .then(Commands.literal("remove")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((context, builder) -> PlayerLookup.bountyTargets(builder, knownTargets()))
                                .executes(this::remove)))
                .then(Commands.literal("clear").executes(this::clear))
                .then(Commands.literal("reload").executes(this::reload))
                .build();
    }

    private Iterable<UUID> knownTargets() {
        return this.bountyService.allSorted(true).stream().map(Bounty::target).toList();
    }

    private int usage(CommandContext<CommandSourceStack> context) {
        this.plugin.messages().send(context.getSource().getSender(), "usage-bountyadmin");
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String name = StringArgumentType.getString(context, "target");
        UUID target = PlayerLookup.resolveKnownTarget(name, knownTargets());
        if (target == null) {
            this.plugin.messages().send(sender, "player-not-found", Placeholder.unparsed("target", name));
            return Command.SINGLE_SUCCESS;
        }
        AdminRemoveResult result = this.bountyService.adminRemove(target);
        String displayName = PlayerLookup.displayName(target, name);
        switch (result.status()) {
            case SUCCESS -> {
                this.plugin.messages().send(sender, "admin-remove-success",
                        Placeholder.unparsed("target", displayName),
                        Placeholder.unparsed("refund", this.plugin.economy().format(result.refunded())));
                warnIfRefundsFailed(sender, result.failedRefunds());
            }
            case NOT_FOUND -> this.plugin.messages().send(sender, "admin-remove-not-found",
                    Placeholder.unparsed("target", displayName));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        AdminClearResult result = this.bountyService.adminClear();
        this.plugin.messages().send(sender, "admin-clear-success",
                Placeholder.unparsed("count", String.valueOf(result.bountyCount())),
                Placeholder.unparsed("refund", this.plugin.economy().format(result.refunded())));
        warnIfRefundsFailed(sender, result.failedRefunds());
        return Command.SINGLE_SUCCESS;
    }

    private void warnIfRefundsFailed(CommandSender sender, int failedRefunds) {
        if (failedRefunds > 0) {
            this.plugin.messages().send(sender, "admin-refund-partial-failure",
                    Placeholder.unparsed("count", String.valueOf(failedRefunds)));
        }
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        boolean success = this.plugin.configManager().reload();
        this.plugin.messages().send(sender, success ? "reload-success" : "reload-failed");
        return Command.SINGLE_SUCCESS;
    }
}

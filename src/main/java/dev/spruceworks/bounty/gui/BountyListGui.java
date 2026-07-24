package dev.spruceworks.bounty.gui;

import dev.spruceworks.bounty.SpruceBountyPlugin;
import dev.spruceworks.bounty.model.Bounty;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Paginated player-head list of active bounties. Highest-amount-first by
 * default; the sort-toggle control switches to most-recently-updated first.
 * No search in v1 (see README roadmap).
 */
public final class BountyListGui {

    private static final int PAGE_SIZE = 45;
    static final int SLOT_PREV = 45;
    static final int SLOT_SORT = 49;
    static final int SLOT_NEXT = 53;

    private final SpruceBountyPlugin plugin;
    private final Player viewer;

    public BountyListGui(SpruceBountyPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public void open() {
        open(0, false);
    }

    void open(int page, boolean newestFirst) {
        List<Bounty> all = this.plugin.bountyService().allSorted(newestFirst);
        int pageCount = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        int clampedPage = Math.max(0, Math.min(page, pageCount - 1));
        int from = clampedPage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, all.size());
        List<Bounty> pageItems = all.subList(from, to);

        List<UUID> pageTargets = new ArrayList<>();
        BountyGuiHolder holder = new BountyGuiHolder(this.viewer.getUniqueId(), pageTargets, clampedPage, pageCount, newestFirst);
        Component title = Component.text("Bounties", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                .append(Component.text(" (" + (clampedPage + 1) + "/" + pageCount + ")", NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false));
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inventory);

        Economy economy = this.plugin.economy();
        int slot = 0;
        for (Bounty bounty : pageItems) {
            pageTargets.add(bounty.target());
            inventory.setItem(slot++, headFor(bounty, economy));
        }

        inventory.setItem(SLOT_PREV, navItem(Material.ARROW, "Previous page", clampedPage > 0));
        inventory.setItem(SLOT_SORT, navItem(Material.HOPPER,
                newestFirst ? "Sort: Newest first (click for Amount)" : "Sort: Highest amount first (click for Newest)", true));
        inventory.setItem(SLOT_NEXT, navItem(Material.ARROW, "Next page", clampedPage < pageCount - 1));

        this.viewer.openInventory(inventory);
    }

    private ItemStack headFor(Bounty bounty, Economy economy) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(bounty.target());
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(target);
            String name = target.getName() != null ? target.getName() : bounty.target().toString();
            meta.displayName(Component.text(name, NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Bounty: ", NamedTextColor.GRAY)
                            .append(Component.text(economy.format(bounty.total()), NamedTextColor.GREEN))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Contributors: ", NamedTextColor.GRAY)
                            .append(Component.text(bounty.contributorCount(), NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Placed: ", NamedTextColor.GRAY)
                            .append(Component.text(agoText(bounty.firstPlacedAt()), NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Click to check", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
        });
        return head;
    }

    private ItemStack navItem(Material material, String label, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? material : Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(
                Component.text(label, enabled ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        return item;
    }

    private String agoText(Instant instant) {
        Duration elapsed = Duration.between(instant, Instant.now());
        long minutes = elapsed.toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = elapsed.toHours();
        if (hours < 24) {
            return hours + "h ago";
        }
        return elapsed.toDays() + "d ago";
    }
}

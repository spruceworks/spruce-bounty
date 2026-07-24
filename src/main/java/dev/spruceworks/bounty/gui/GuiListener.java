package dev.spruceworks.bounty.gui;

import dev.spruceworks.bounty.SpruceBountyPlugin;
import dev.spruceworks.bounty.command.CheckMessenger;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Handles paging, sort-toggle, and check-on-click for {@link BountyListGui}. */
public final class GuiListener implements Listener {

    private final SpruceBountyPlugin plugin;

    public GuiListener(SpruceBountyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BountyGuiHolder holder)) {
            return;
        }
        // Cancel unconditionally, even clicks in the viewer's own inventory half, to
        // block shift-click/drag exploits moving real items into this display-only GUI.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) {
            return;
        }

        int slot = event.getSlot();
        if (slot == BountyListGui.SLOT_PREV) {
            if (holder.page() > 0) {
                new BountyListGui(this.plugin, player).open(holder.page() - 1, holder.newestFirst());
            }
        } else if (slot == BountyListGui.SLOT_NEXT) {
            if (holder.page() < holder.pageCount() - 1) {
                new BountyListGui(this.plugin, player).open(holder.page() + 1, holder.newestFirst());
            }
        } else if (slot == BountyListGui.SLOT_SORT) {
            new BountyListGui(this.plugin, player).open(0, !holder.newestFirst());
        } else if (slot >= 0 && slot < holder.pageTargets().size()) {
            UUID target = holder.pageTargets().get(slot);
            player.closeInventory();
            CheckMessenger.send(this.plugin, player, target, null);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BountyGuiHolder) {
            event.setCancelled(true);
        }
    }
}

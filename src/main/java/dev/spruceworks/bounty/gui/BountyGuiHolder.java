package dev.spruceworks.bounty.gui;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks an inventory as ours and carries the paging/sort state needed to handle clicks. */
final class BountyGuiHolder implements InventoryHolder {

    private final UUID viewer;
    private final List<UUID> pageTargets;
    private final int page;
    private final int pageCount;
    private final boolean newestFirst;
    private Inventory inventory;

    BountyGuiHolder(UUID viewer, List<UUID> pageTargets, int page, int pageCount, boolean newestFirst) {
        this.viewer = viewer;
        this.pageTargets = pageTargets;
        this.page = page;
        this.pageCount = pageCount;
        this.newestFirst = newestFirst;
    }

    UUID viewer() {
        return this.viewer;
    }

    List<UUID> pageTargets() {
        return this.pageTargets;
    }

    int page() {
        return this.page;
    }

    int pageCount() {
        return this.pageCount;
    }

    boolean newestFirst() {
        return this.newestFirst;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}

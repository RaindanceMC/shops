package de.raindancer.modules.shops.service;

import de.raindancer.core.platform.util.Inventories;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** A real inventory as a shelf of one exact item. Touch it only on the thread that owns its block or its player. */
public final class InventoryShelf implements Shelf {

    private final Inventory inventory;
    private final ItemStack like;
    private final Player owner;
    private final Location spillAt;

    private InventoryShelf(Inventory inventory, ItemStack like, Player owner, Location spillAt) {
        this.inventory = inventory;
        this.like = like;
        this.owner = owner;
        this.spillAt = spillAt;
    }

    /** A container; whatever cannot be put back is dropped on top of it rather than lost. */
    public static InventoryShelf container(Inventory inventory, ItemStack like, Location block) {
        return new InventoryShelf(inventory, like, null, block.clone().add(0.5, 1.1, 0.5));
    }

    /** A player's storage slots — never their armour or their off hand; overflow lands at their feet. */
    public static InventoryShelf player(Player player, ItemStack like) {
        return new InventoryShelf(player.getInventory(), like, player, null);
    }

    @Override
    public int count() {
        return Inventories.count(inventory.getStorageContents(), like);
    }

    @Override
    public int room() {
        return Inventories.room(inventory.getStorageContents(), like);
    }

    @Override
    public boolean remove(int items) {
        return !Inventories.take(inventory, like, items).isEmpty();
    }

    @Override
    public int add(int items) {
        List<ItemStack> stacks = Inventories.stacksOf(like, items);
        if (owner != null) {
            Inventories.giveOrDrop(owner, stacks);
            return 0;
        }
        for (ItemStack left : inventory.addItem(stacks.toArray(ItemStack[]::new)).values()) {
            spillAt.getWorld().dropItemNaturally(spillAt, left);
        }
        return 0;
    }
}

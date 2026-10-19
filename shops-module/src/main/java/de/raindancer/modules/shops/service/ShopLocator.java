package de.raindancer.modules.shops.service;

import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.store.ShopIndex;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Which shop a block, a sign or an inventory belongs to. A double chest is one shop whichever half is clicked, and
 * a sign on a shop's container is that shop.
 */
public final class ShopLocator {

    private final ShopIndex index;
    private final Supplier<String> server;

    public ShopLocator(ShopIndex index, Supplier<String> server) {
        this.index = index;
        this.server = server;
    }

    public Spot spotOf(Block block) {
        return new Spot(server.get(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public Optional<Location> location(Spot spot) {
        if (!spot.isOn(server.get())) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(spot.world());
        return world == null ? Optional.empty() : Optional.of(new Location(world, spot.x(), spot.y(), spot.z()));
    }

    /** The shop this block is, or is half of, or is a sign on. */
    public Optional<Shop> shopAt(Block block) {
        for (Block part : parts(block)) {
            Optional<Shop> found = index.at(spotOf(part));
            if (found.isPresent()) {
                return found;
            }
        }
        if (block.getBlockData() instanceof WallSign sign) {
            return shopAt(block.getRelative(sign.getFacing().getOppositeFace()));
        }
        return Optional.empty();
    }

    /** Whether this block is a shop's container or half of one — what an explosion or a piston must leave alone. */
    public boolean isShopBlock(Block block) {
        return parts(block).stream().anyMatch(part -> index.at(spotOf(part)).isPresent());
    }

    /** The shop an inventory belongs to — a hopper's source or destination. */
    public Optional<Shop> shopOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof DoubleChest chest) {
            for (InventoryHolder side : new InventoryHolder[]{chest.getLeftSide(false), chest.getRightSide(false)}) {
                if (side instanceof BlockState state) {
                    Optional<Shop> found = index.at(spotOf(state.getBlock()));
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
            return Optional.empty();
        }
        if (holder instanceof BlockState state) {
            return index.at(spotOf(state.getBlock()));
        }
        return Optional.empty();
    }

    /** The shop's whole inventory — both halves of a double chest. On the block's own region thread. */
    public Optional<Inventory> inventory(Shop shop) {
        return location(shop.spot())
                .filter(at -> at.isChunkLoaded())
                .map(Location::getBlock)
                .map(block -> block.getState(false))
                .filter(Container.class::isInstance)
                .map(state -> ((Container) state).getInventory());
    }

    /** The block itself, and the other half when it is half of a double chest. */
    private List<Block> parts(Block block) {
        List<Block> parts = new ArrayList<>(2);
        parts.add(block);
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Chest chest
                && chest.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
            BlockState state = block.getState(false);
            if (state instanceof org.bukkit.block.Chest real
                    && real.getInventory().getHolder(false) instanceof DoubleChest both) {
                for (InventoryHolder side : new InventoryHolder[]{both.getLeftSide(false), both.getRightSide(false)}) {
                    if (side instanceof BlockState half && !half.getBlock().equals(block)) {
                        parts.add(half.getBlock());
                    }
                }
            }
        }
        return parts;
    }
}

package de.raindancer.modules.shops.listener;

import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.util.PermissionNodes;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.List;
import java.util.Optional;

/**
 * Nothing takes a shop's stock but a trade: not a hopper, a creeper, a piston, fire, a wither, or a chest placed
 * against it to make it a double chest somebody else can open. Its sign stays too.
 */
public final class ShopGuardListener implements IShopsListener {

    private static final List<BlockFace> SIDES = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST);

    private final ShopsServices services;

    public ShopGuardListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(java.util.UUID player) {
        // Remembers nobody.
    }

    /**
     * The shop's chest, its sign, or the block holding the sign up: its manager breaking one ends the shop — the chest
     * keeps its stock — and nobody else breaks any of them.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<Shop> shop = services.locator().shopOfSign(block).or(() -> services.locator().shopAt(block))
                .or(() -> services.locator().signHeldBy(block));
        if (shop.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        boolean manager = services.book().managerOf(shop.get(), player.getUniqueId())
                || player.hasPermission(PermissionNodes.ADMIN);
        if (!manager) {
            event.setCancelled(true);
            services.messages().send(player, "shops.guard.not-yours", "shop", shop.get().name());
            return;
        }
        var ended = services.keeper().remove(player, shop.get());
        if (ended.ok()) {
            services.messages().send(player, "shops.ended-broken", "shop", shop.get().name());
        } else {
            event.setCancelled(true);
            services.refuse(player, ended.refusal());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(services.locator()::isShopBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(services.locator()::isShopBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPush(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(services.locator()::isShopBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPull(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(services.locator()::isShopBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (services.locator().isShopBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent event) {
        if (services.locator().isShopBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Hoppers never take out; they put in only if the server allows it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (services.locator().shopOf(event.getSource()).isPresent()) {
            event.setCancelled(true);
            return;
        }
        if (!services.config().hoppersFillShops() && services.locator().shopOf(event.getDestination()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** A chest set beside a shop's chest joins it; only somebody who may stock the shop may do that. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        String type = placed.getType().name();
        if (!type.equals("CHEST") && !type.equals("TRAPPED_CHEST")) {
            return;
        }
        for (BlockFace side : SIDES) {
            Optional<Shop> next = services.locator().shopAt(placed.getRelative(side));
            if (next.isPresent() && !services.book().staffOf(next.get(), event.getPlayer().getUniqueId())
                    && !event.getPlayer().hasPermission(PermissionNodes.ADMIN)) {
                event.setCancelled(true);
                services.messages().send(event.getPlayer(), "shops.guard.not-beside", "shop", next.get().name());
                return;
            }
        }
    }
}

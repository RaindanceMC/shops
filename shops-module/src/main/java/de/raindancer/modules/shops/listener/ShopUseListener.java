package de.raindancer.modules.shops.listener;

import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.util.PermissionNodes;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/**
 * Clicking a shop. A customer gets the counter, never the container; the owner and their staff open the container to
 * stock it, and sneak to get the back room instead. A sign on the shop is the shop.
 */
public final class ShopUseListener implements IShopsListener {

    private final ShopsServices services;

    public ShopUseListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(java.util.UUID player) {
        // Remembers nobody.
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (block.getState(false) instanceof org.bukkit.block.Container) {
            services.recent().clicked(event.getPlayer().getUniqueId(), services.locator().spotOf(block),
                    System.currentTimeMillis());
        }
        Optional<Shop> found = services.locator().shopAt(block);
        if (found.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Shop shop = found.get();
        boolean staff = shop.mayStock(player.getUniqueId()) || player.hasPermission(PermissionNodes.ADMIN);
        boolean sign = !(block.getState(false) instanceof org.bukkit.block.Container);
        if (staff && player.isSneaking()) {
            event.setCancelled(true);
            services.screens().manage(player, shop);
            return;
        }
        if (staff && !sign) {
            // Stocking: the container opens as any container does.
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission(PermissionNodes.USE)) {
            services.messages().send(player, "shops.refused.not-allowed");
            return;
        }
        services.screens().front(player, shop, null);
    }

    /** A container opened some other way — a plugin, a minecart — by somebody who may not: shut again. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        services.locator().shopOf(event.getInventory()).ifPresent(shop -> {
            if (!shop.mayStock(player.getUniqueId()) && !player.hasPermission(PermissionNodes.ADMIN)) {
                event.setCancelled(true);
                services.screens().front(player, shop, null);
            }
        });
    }

    /** The owner stocked up: count again, so the sign and the browser say so. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        services.locator().shopOf(event.getInventory()).ifPresent(shop ->
                services.trades().recount(shop, event.getInventory(), services.displays()::show));
    }
}

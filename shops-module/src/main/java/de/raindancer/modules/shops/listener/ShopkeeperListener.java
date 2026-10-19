package de.raindancer.modules.shops.listener;

import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Store;
import de.raindancer.modules.shops.util.PermissionNodes;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;
import java.util.UUID;

/**
 * Clicking a shopkeeper opens its shop; its owner or a manager sneaking gets the shop's own page. Nobody hurts one,
 * and one that is not its shop's shopkeeper any more is gone the next time its chunk loads.
 */
public final class ShopkeeperListener implements IShopsListener {

    private final ShopsServices services;

    public ShopkeeperListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(UUID player) {
        // Remembers nobody.
    }

    /** At LOWEST, cancelled here, so Core's land protection does not refuse a shopkeeper in a claim first. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Optional<UUID> storeId = services.keepers().storeOf(clicked);
        if (storeId.isEmpty()) {
            return;
        }
        // Both hands cancelled — Core checks both — but only the main hand opens the shop.
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Optional<Store> store = services.book().store(storeId.get());
        if (store.isEmpty() || !services.keepers().isCurrent(clicked)) {
            services.messages().send(player, "shops.keeper.closed");
            return;
        }
        if (player.isSneaking() && (store.get().mayManage(player.getUniqueId())
                || player.hasPermission(PermissionNodes.ADMIN))) {
            services.screens().storeManage(player, store.get());
            return;
        }
        if (!player.hasPermission(PermissionNodes.USE)) {
            services.messages().send(player, "shops.refused.not-allowed");
            return;
        }
        services.screens().store(player, store.get(), clicked.getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (services.keepers().storeOf(event.getRightClicked()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (services.keepers().storeOf(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            services.keepers().loaded(entity);
        }
    }
}

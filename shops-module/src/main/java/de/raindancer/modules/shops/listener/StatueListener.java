package de.raindancer.modules.shops.listener;

import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Store;
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
 * Clicking a statue opens its store; its owner sneaking takes it down. Nobody hurts one, and one whose store has
 * closed is gone the next time its chunk loads.
 */
public final class StatueListener implements IShopsListener {

    private final ShopsServices services;

    public StatueListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(UUID player) {
        // Remembers nobody.
    }

    /** At LOWEST, cancelled here, so Core's land protection does not refuse a statue in a claim first. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Optional<UUID> storeId = services.statues().storeOf(clicked);
        if (storeId.isEmpty()) {
            return;
        }
        // Both hands cancelled — Core checks both — but only the main hand opens the store.
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Optional<Store> store = services.book().store(storeId.get());
        if (store.isEmpty()) {
            services.messages().send(player, "shops.statue.closed");
            return;
        }
        if (player.isSneaking() && (store.get().isOwner(player.getUniqueId())
                || services.keeper().isAdmin(player))) {
            Answer<UUID> removed = services.statues().remove(player, clicked);
            services.messages().send(player, removed.ok() ? "shops.statue.removed" : removed.refusal().key(),
                    "store", store.get().name());
            return;
        }
        services.screens().store(player, store.get(), clicked.getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (services.statues().storeOf(event.getRightClicked()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (services.statues().storeOf(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            services.statues().loaded(entity);
        }
    }
}

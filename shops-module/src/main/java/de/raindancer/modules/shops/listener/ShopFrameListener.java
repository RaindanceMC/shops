package de.raindancer.modules.shops.listener;

import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.rules.ChestClickRule;
import de.raindancer.modules.shops.util.PermissionNodes;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;
import java.util.UUID;

/**
 * A chest shop's item frame: clicking it is clicking the shop, and nothing turns it, empties it or takes it down while
 * the shop lasts — not a player, an arrow, an explosion. Once the shop has ended (its chest broken) it may fall.
 */
public final class ShopFrameListener implements IShopsListener {

    private final ShopsServices services;
    private final ChestClickRule clicks = new ChestClickRule();

    public ShopFrameListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(UUID player) {
        // Remembers nobody.
    }

    private Optional<Shop> shopOf(Entity entity) {
        if (!(entity instanceof ItemFrame frame)) {
            return Optional.empty();
        }
        return services.locator().shopAt(frame.getLocation().getBlock().getRelative(frame.getAttachedFace()))
                .filter(shop -> entity.getUniqueId().equals(shop.frame()));
    }

    /** At LOWEST, cancelled here, so Core's land protection does not refuse a customer first. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent event) {
        Optional<Shop> shop = shopOf(event.getRightClicked());
        if (shop.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        boolean staff = services.book().staffOf(shop.get(), player.getUniqueId())
                || player.hasPermission(PermissionNodes.ADMIN);
        boolean manager = services.book().managerOf(shop.get(), player.getUniqueId())
                || player.hasPermission(PermissionNodes.ADMIN);
        ShopUseListener.open(services, player, shop.get(), clicks.fixture(staff, manager));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChange(PlayerItemFrameChangeEvent event) {
        if (shopOf(event.getItemFrame()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (shopOf(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(HangingBreakEvent event) {
        if (shopOf(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }
}

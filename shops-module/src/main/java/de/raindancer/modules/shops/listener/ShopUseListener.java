package de.raindancer.modules.shops.listener;

import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.rules.ChestClickRule;
import de.raindancer.modules.shops.util.PermissionNodes;
import org.bukkit.Location;
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
import java.util.UUID;

/**
 * Clicking a chest shop's chest or its sign. A customer gets the counter. The shop's staff open the chest to stock it;
 * a manager sneaking with an empty hand — or clicking the sign — gets the shop's back room.
 */
public final class ShopUseListener implements IShopsListener {

    private final ShopsServices services;
    private final ChestClickRule clicks = new ChestClickRule();

    public ShopUseListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(UUID player) {
        // Remembers nobody.
    }

    /**
     * At LOWEST and cancelled here: Core's land protection, at LOW, refuses visitors clicking containers in a claim.
     * It skips cancelled events.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Optional<Shop> bySign = services.locator().shopOfSign(block);
        Optional<Shop> found = bySign.or(() -> services.locator().shopAt(block));
        if (found.isEmpty()) {
            return;
        }
        Shop shop = found.get();
        Player player = event.getPlayer();
        boolean staff = services.book().staffOf(shop, player.getUniqueId())
                || player.hasPermission(PermissionNodes.ADMIN);
        boolean manager = services.book().managerOf(shop, player.getUniqueId())
                || player.hasPermission(PermissionNodes.ADMIN);
        ChestClickRule.Outcome outcome = bySign.isPresent() ? clicks.fixture(staff, manager)
                : clicks.decide(staff, manager, player.isSneaking(),
                player.getInventory().getItemInMainHand().getType().isAir());
        if (outcome == ChestClickRule.Outcome.AS_THE_GAME_DOES) {
            // Stocking it, or putting a frame or a sign on it: the chest is the staff's to use as any chest.
            return;
        }
        // Both hands cancelled — Core checks both, and a waxed sign must not open for editing — the main hand acts.
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        open(services, player, shop, outcome);
    }

    /** What a click on a shop — its chest, sign or frame — opens. */
    static void open(ShopsServices services, Player player, Shop shop, ChestClickRule.Outcome outcome) {
        switch (outcome) {
            case BACK_ROOM -> services.screens().manage(player, shop);
            case OWN_SHOP -> services.messages().send(player, "shops.own-shop", "shop", shop.name());
            case COUNTER -> {
                if (!player.hasPermission(PermissionNodes.USE)) {
                    services.messages().send(player, "shops.refused.not-allowed");
                    return;
                }
                Location chest = services.locator().location(shop.spot()).map(at -> at.add(0.5, 0.5, 0.5))
                        .orElse(player.getLocation());
                services.screens().front(player, shop, chest);
            }
            default -> {
            }
        }
    }

    /** A shop's chest opened some other way — a plugin, a minecart — by somebody who may not: shut again. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        services.locator().shopOf(event.getInventory()).ifPresent(shop -> {
            if (!services.book().staffOf(shop, player.getUniqueId()) && !player.hasPermission(PermissionNodes.ADMIN)) {
                event.setCancelled(true);
            }
        });
    }

    /** Somebody stocked up: count the shop again, so /market and what floats over it say so. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        services.locator().shopOf(event.getInventory()).ifPresent(shop ->
                services.trades().recount(shop, services.displays()::show));
    }
}

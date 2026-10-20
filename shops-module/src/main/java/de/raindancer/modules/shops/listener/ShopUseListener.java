package de.raindancer.modules.shops.listener;

import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.rules.ChestClickRule;
import de.raindancer.modules.shops.rules.LinkRule;
import de.raindancer.modules.shops.util.PermissionNodes;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
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
 * Clicking a container. While linking, a click links the chest to the offer or unlinks it. Otherwise a stock chest is
 * the shop's back room: its staff open it to stock it (managers sneaking with an empty hand get the offer's settings;
 * with something in hand it is placed as usual), and a customer is sent to the shopkeeper.
 */
public final class ShopUseListener implements IShopsListener {

    private final ShopsServices services;
    private final ChestClickRule clicks = new ChestClickRule();

    public ShopUseListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(UUID player) {
        services.linking().stop(player);
    }

    /**
     * At LOWEST and cancelled here: Core's land protection, at LOW, refuses visitors clicking containers in a claim
     * and would tell an owner linking their own chest that they may not. It skips cancelled events.
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
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Optional<UUID> linking = services.linking().offerOf(player.getUniqueId(), now);
        if (linking.isPresent() && block.getState(false) instanceof Container) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                link(player, linking.get(), block, now);
            }
            return;
        }
        Optional<Shop> found = services.locator().shopAt(block);
        if (found.isEmpty()) {
            return;
        }
        Shop shop = found.get();
        boolean staff = services.book().staffOf(shop, player.getUniqueId())
                || player.hasPermission(PermissionNodes.ADMIN);
        if (event.getHand() != EquipmentSlot.HAND) {
            // The off hand's half of the same click: quiet for a customer, so Core does not refuse it out loud.
            if (!staff) {
                event.setCancelled(true);
            }
            return;
        }
        boolean manager = services.book().managerOf(shop, player.getUniqueId())
                || player.hasPermission(PermissionNodes.ADMIN);
        switch (clicks.decide(staff, manager, player.isSneaking(),
                player.getInventory().getItemInMainHand().getType().isAir())) {
            case BACK_ROOM -> {
                event.setCancelled(true);
                services.screens().manage(player, shop);
                return;
            }
            case AS_THE_GAME_DOES -> {
                // Stocking it, or putting a frame or a sign on it: the chest is the staff's to use as any chest.
                return;
            }
            case TO_THE_SHOPKEEPER -> event.setCancelled(true);
        }
        String store = services.book().store(shop.store()).map(de.raindancer.modules.shops.model.Store::name)
                .orElse(shop.ownerName());
        services.messages().send(player, "shops.stock-chest", "shop", store, "owner", shop.ownerName());
    }

    private void link(Player player, UUID offerId, Block block, long now) {
        services.linking().touched(player.getUniqueId(), now);
        Optional<Shop> offer = services.book().index().byId(offerId);
        if (offer.isEmpty()) {
            services.linking().stop(player.getUniqueId());
            services.messages().send(player, "shops.refused.gone");
            return;
        }
        Answer<LinkRule.Change> done = services.keeper().link(player, offer.get(), block);
        if (!done.ok()) {
            services.refuse(player, done.refusal());
            return;
        }
        Shop linked = services.book().index().byId(offerId).orElse(offer.get());
        services.messages().send(player, done.value() == LinkRule.Change.LINK ? "shops.link.linked"
                        : "shops.link.unlinked", "shop", linked.name(), "chests", Integer.toString(linked.chests().size()),
                "most", Integer.toString(services.config().chestLimit()));
        services.trades().recount(linked, changed -> { });
    }

    /** A stock chest opened some other way — a plugin, a minecart — by somebody who may not: shut again. */
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

    /** Somebody stocked up: count the offer again, so the bazaar says so. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        services.locator().shopOf(event.getInventory()).ifPresent(shop ->
                services.trades().recount(shop, changed -> { }));
    }
}

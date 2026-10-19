package de.raindancer.modules.shops.listener;

import de.raindancer.core.social.economy.Economies;
import de.raindancer.core.social.economy.Economy;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.rules.SignRule;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Optional;

/**
 * A sign written {@code [shop]} on a container opens a shop there — prices on the second line, the bundle on the
 * third, a name on the fourth — and is rewritten to say what the shop is.
 */
public final class ShopSignListener implements IShopsListener {

    private final ShopsServices services;
    private final SignRule rule = new SignRule();

    public ShopSignListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(java.util.UUID player) {
        // Remembers nobody.
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWrite(SignChangeEvent event) {
        String[] lines = new String[4];
        for (int at = 0; at < 4; at++) {
            lines[at] = event.line(at) == null ? "" : PlainTextComponentSerializer.plainText()
                    .serialize(event.line(at));
        }
        if (!rule.isShopSign(lines) || !services.config().signShops()) {
            return;
        }
        Block sign = event.getBlock();
        Block container = sign.getBlockData() instanceof WallSign wall
                ? sign.getRelative(wall.getFacing().getOppositeFace()) : sign.getRelative(0, -1, 0);
        if (!(container.getState(false) instanceof Container stock)) {
            services.messages().send(event.getPlayer(), "shops.sign.not-on-container");
            return;
        }
        Optional<SignRule.Setup> setup = rule.read(lines, typed -> Economies.current().map(Economy::currency)
                .orElse(de.raindancer.core.social.economy.Currency.DEFAULT).parse(typed));
        if (setup.isEmpty()) {
            services.messages().send(event.getPlayer(), "shops.sign.unreadable");
            return;
        }
        ItemStack ware = event.getPlayer().getInventory().getItemInMainHand();
        if (ware.getType().isAir() || ware.getType().name().endsWith("_SIGN")) {
            ware = Arrays.stream(stock.getInventory().getStorageContents())
                    .filter(item -> item != null && !item.getType().isAir()).findFirst().orElse(null);
        }
        SignRule.Setup read = setup.get();
        Answer<Shop> opened = services.keeper().open(event.getPlayer(), container, ware,
                new Prices(read.buy(), read.sell(), read.bundle()), read.name(), null);
        if (!opened.ok()) {
            services.refuse(event.getPlayer(), opened.refusal());
            return;
        }
        Shop shop = opened.value();
        event.line(0, services.messages().get("shops.sign.line-1"));
        event.line(1, services.messages().get("shops.sign.line-2",
                "buy", shop.prices().sells() ? Fees.format(shop.prices().buy()) : "—",
                "sell", shop.prices().buys() ? Fees.format(shop.prices().sell()) : "—"));
        event.line(2, services.messages().get("shops.sign.line-3", "bundle",
                Integer.toString(shop.prices().bundle()), "ware", shop.ware().label()));
        event.line(3, services.messages().get("shops.sign.line-4", "owner", shop.ownerName()));
        services.messages().send(event.getPlayer(), "shops.opened", "shop", shop.name(), "ware", shop.ware().label());
    }
}

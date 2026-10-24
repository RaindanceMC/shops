package de.raindancer.modules.shops.visual;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.service.IShopsService;
import de.raindancer.modules.shops.service.ShopLocator;
import de.raindancer.modules.shops.service.Wares;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A chest shop's sign and item frame. The sign is rewritten to say what the shop trades and waxed, so nobody edits it
 * by accident; the frame shows the shop's item and is fixed, so nobody turns it or takes the item. When the shop ends
 * both are let go.
 */
public final class ShopFixtures implements IShopsService {

    private final Plugin plugin;
    private final ShopLocator locator;
    private final Wares wares;
    private final Messages messages;

    public ShopFixtures(Plugin plugin, ShopLocator locator, Wares wares, Messages messages) {
        this.plugin = plugin;
        this.locator = locator;
        this.wares = wares;
        this.messages = messages;
    }

    @Override
    public void settings(ShopsSettings settings) {
        // Nothing read from the settings.
    }

    /** The item frame hanging on this chest — either half of a double chest — one showing an item first. */
    public Optional<ItemFrame> frameOn(Block chest) {
        List<Block> parts = locator.parts(chest);
        Location centre = chest.getLocation().add(0.5, 0.5, 0.5);
        return centre.getWorld().getNearbyEntitiesByType(ItemFrame.class, centre, 2.0).stream()
                .filter(frame -> parts.contains(frame.getLocation().getBlock().getRelative(frame.getAttachedFace())))
                .min(Comparator.comparing((ItemFrame frame) -> frame.getItem().getType().isAir()));
    }

    /** Sign and frame as the shop is now. On the chest's region. */
    public void dress(Shop shop) {
        locator.location(shop.spot()).ifPresent(at -> Scheduling.region(plugin, at, () -> {
            if (!at.isChunkLoaded()) {
                return;
            }
            sign(shop).ifPresent(sign -> {
                SignSide front = sign.getSide(Side.FRONT);
                List<Component> lines = lines(shop);
                for (int line = 0; line < 4; line++) {
                    front.line(line, lines.get(line));
                }
                sign.setWaxed(true);
                sign.update(true, false);
            });
            frame(shop).ifPresent(frame -> {
                wares.item(shop.ware()).ifPresent(item -> {
                    if (!item.isSimilar(frame.getItem())) {
                        frame.setItem(item, false);
                    }
                });
                frame.setFixed(true);
                frame.setInvulnerable(true);
            });
        }));
    }

    /** The shop ended: the frame may be turned, emptied and taken down again, the sign edited. */
    public void release(Shop shop) {
        locator.location(shop.spot()).ifPresent(at -> Scheduling.region(plugin, at, () -> {
            if (!at.isChunkLoaded()) {
                return;
            }
            sign(shop).ifPresent(sign -> {
                SignSide front = sign.getSide(Side.FRONT);
                front.line(0, messages.get("shops.sign.ended"));
                for (int line = 1; line < 4; line++) {
                    front.line(line, Component.empty());
                }
                sign.setWaxed(false);
                sign.update(true, false);
            });
            frame(shop).ifPresent(frame -> {
                frame.setFixed(false);
                frame.setInvulnerable(false);
            });
        }));
    }

    private Optional<Sign> sign(Shop shop) {
        if (shop.sign() == null) {
            return Optional.empty();
        }
        return locator.location(shop.sign()).map(Location::getBlock).map(block -> block.getState(false))
                .filter(Sign.class::isInstance).map(Sign.class::cast);
    }

    private Optional<ItemFrame> frame(Shop shop) {
        if (shop.frame() == null) {
            return Optional.empty();
        }
        Entity entity = plugin.getServer().getEntity(shop.frame());
        return entity instanceof ItemFrame frame ? Optional.of(frame) : Optional.empty();
    }

    /** What the sign says: the market's mark, the prices, the bundle and the item, the owner. */
    public List<Component> lines(Shop shop) {
        String bundle = Integer.toString(shop.prices().bundle());
        Component prices;
        if (shop.prices().sells() && shop.prices().buys()) {
            prices = messages.get("shops.sign.both", "buy", Fees.format(shop.prices().buy()),
                    "sell", Fees.format(shop.prices().sell()));
        } else if (shop.prices().sells()) {
            prices = messages.get("shops.sign.buy", "buy", Fees.format(shop.prices().buy()));
        } else {
            prices = messages.get("shops.sign.sell", "sell", Fees.format(shop.prices().sell()));
        }
        return List.of(messages.get(shop.status().trades() ? "shops.sign.mark" : "shops.sign.mark-closed"), prices,
                messages.get("shops.sign.ware", "bundle", bundle, "ware", shop.ware().label()),
                messages.get("shops.sign.owner", "owner", shop.ownerName()));
    }

    /** One of the shop's item, for a frame that shows nothing yet. */
    public Optional<ItemStack> item(Shop shop) {
        return wares.item(shop.ware());
    }
}

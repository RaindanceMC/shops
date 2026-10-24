package de.raindancer.modules.shops.listener;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.rules.SignPlacementRule;
import de.raindancer.modules.shops.rules.SignRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A sign written {@code [market]} at a chest sets up a chest shop there — prices on the second line, the bundle on the
 * third, a name on the fourth — trading in what the chest's item frame shows (or, without one, the first thing in the
 * chest). The sign is rewritten to say what the shop is. Written again at a shop, it prices the shop anew.
 */
public final class ShopSignListener implements IShopsListener {

    private final ShopsServices services;
    private final SignRule rule = new SignRule();
    private final SignPlacementRule placement = new SignPlacementRule();

    public ShopSignListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(UUID player) {
        // Remembers nobody.
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWrite(SignChangeEvent event) {
        String[] lines = new String[4];
        for (int at = 0; at < 4; at++) {
            Component line = event.line(at);
            lines[at] = line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line);
        }
        if (!rule.isShopSign(lines)) {
            return;
        }
        Block sign = event.getBlock();
        Optional<Block> chest = holder(sign);
        if (chest.isEmpty()) {
            services.messages().send(event.getPlayer(), "shops.sign.not-on-chest");
            return;
        }
        Optional<SignRule.Setup> setup = rule.read(lines, typed -> Fees.currency().parse(typed));
        if (setup.isEmpty()) {
            services.messages().send(event.getPlayer(), "shops.sign.unreadable");
            return;
        }
        Optional<ItemFrame> frame = services.fixtures().frameOn(chest.get());
        ItemStack ware = frame.map(ItemFrame::getItem).filter(item -> !item.getType().isAir())
                .orElseGet(() -> firstIn(chest.get()));
        if (ware == null && services.locator().shopAt(chest.get()).isEmpty()) {
            services.messages().send(event.getPlayer(), "shops.sign.no-ware");
            return;
        }
        boolean existed = services.locator().shopAt(chest.get()).isPresent();
        Answer<Shop> opened = services.keeper().openChestShop(event.getPlayer(), chest.get(),
                services.locator().spotOf(sign), frame.map(ItemFrame::getUniqueId).orElse(null), ware, setup.get());
        if (!opened.ok()) {
            services.log().info("{} could not set up a chest shop at {}: {}", event.getPlayer().getName(),
                    chest.get().getLocation().toVector(), opened.refusal());
            services.refuse(event.getPlayer(), opened.refusal());
            return;
        }
        Shop shop = opened.value();
        // Counted now, so what floats over it says what is in the chest from the first moment, not "sold out".
        services.trades().recount(shop, services.displays()::show);
        List<Component> written = services.fixtures().lines(shop);
        for (int at = 0; at < 4; at++) {
            event.line(at, written.get(at));
        }
        services.messages().send(event.getPlayer(), existed ? "shops.repriced" : "shops.opened",
                "shop", shop.name(), "ware", shop.ware().label());
        if (frame.isEmpty()) {
            services.messages().send(event.getPlayer(), "shops.sign.frame-hint");
        }
    }

    /** The chest this sign belongs to: the one it hangs on, the one above what it hangs on, or the one it stands on. */
    private Optional<Block> holder(Block sign) {
        if (sign.getBlockData() instanceof WallSign wall) {
            Block behind = sign.getRelative(wall.getFacing().getOppositeFace());
            Block aboveBehind = behind.getRelative(BlockFace.UP);
            return switch (placement.holder(true, isContainer(behind), isContainer(aboveBehind), false)) {
                case BEHIND -> Optional.of(behind);
                case ABOVE_BEHIND -> Optional.of(aboveBehind);
                default -> Optional.empty();
            };
        }
        if (sign.getBlockData() instanceof org.bukkit.block.data.type.Sign) {
            Block below = sign.getRelative(BlockFace.DOWN);
            return placement.holder(false, false, false, isContainer(below)) == SignPlacementRule.Holder.BELOW
                    ? Optional.of(below) : Optional.empty();
        }
        return Optional.empty();
    }

    private static boolean isContainer(Block block) {
        return block.getState(false) instanceof Container;
    }

    private static ItemStack firstIn(Block chest) {
        if (!(chest.getState(false) instanceof Container container)) {
            return null;
        }
        return Arrays.stream(container.getInventory().getStorageContents())
                .filter(item -> item != null && !item.getType().isAir()).findFirst().orElse(null);
    }
}

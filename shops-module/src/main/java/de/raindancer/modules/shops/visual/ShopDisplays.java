package de.raindancer.modules.shops.visual;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.service.IShopsService;
import de.raindancer.modules.shops.service.ShopBook;
import de.raindancer.modules.shops.service.ShopLocator;
import de.raindancer.modules.shops.service.Wares;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The item and its price floating above every shop. Never saved with the chunk: a display that outlived its shop
 * after a crash would advertise a shop that is gone, so they are drawn again whenever a shop's chunk loads.
 */
public final class ShopDisplays implements IShopsService {

    public static final String MARK = "shop-display";

    private final Plugin plugin;
    private final ShopBook book;
    private final ShopLocator locator;
    private final Wares wares;
    private final Messages messages;
    private final NamespacedKey mark;
    private final Map<UUID, List<UUID>> drawn = new ConcurrentHashMap<>();
    private volatile ShopsSettings settings;

    public ShopDisplays(Plugin plugin, ShopBook book, ShopLocator locator, Wares wares, Messages messages,
                        ShopsSettings settings) {
        this.plugin = plugin;
        this.book = book;
        this.locator = locator;
        this.wares = wares;
        this.messages = messages;
        this.settings = settings;
        this.mark = new NamespacedKey(plugin, MARK);
    }

    @Override
    public void settings(ShopsSettings fresh) {
        boolean was = settings.displays();
        settings = fresh;
        if (was != fresh.displays()) {
            showAll();
        }
    }

    public void showAll() {
        for (Shop shop : book.index().all()) {
            show(shop);
        }
    }

    /** Draws the shop again as it is now, or takes it away when it should not be drawn. */
    public void show(Shop asked) {
        Shop shop = book.index().byId(asked.id()).orElse(asked);
        locator.location(shop.spot()).ifPresent(block -> Scheduling.region(plugin, block, () -> {
            hideHere(shop.id());
            if (!settings.displays() || !shop.display() || !block.isChunkLoaded()
                    || book.index().byId(shop.id()).isEmpty()) {
                return;
            }
            drawn.put(shop.id(), List.of(item(shop, block), text(shop, block)));
        }));
    }

    public void hide(Shop shop) {
        locator.location(shop.spot()).ifPresentOrElse(
                block -> Scheduling.region(plugin, block, () -> hideHere(shop.id())),
                () -> drawn.remove(shop.id()));
    }

    /** A chunk came in: its shops are drawn again. */
    public void loaded(Chunk chunk) {
        String world = chunk.getWorld().getName();
        for (Shop shop : book.index().all()) {
            if (shop.spot().world().equals(world) && shop.spot().x() >> 4 == chunk.getX()
                    && shop.spot().z() >> 4 == chunk.getZ()) {
                show(shop);
            }
        }
    }

    /** Whether an entity is one of these — so nothing else treats it as a thing to hit or to name. */
    public boolean isDisplay(Entity entity) {
        return entity.getPersistentDataContainer().has(mark, PersistentDataType.STRING);
    }

    /** At stop: everything drawn goes, on its own region. */
    public void clear() {
        for (Shop shop : book.index().all()) {
            hide(shop);
        }
    }

    private void hideHere(UUID shop) {
        List<UUID> old = drawn.remove(shop);
        if (old == null) {
            return;
        }
        for (UUID id : old) {
            Entity entity = plugin.getServer().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private UUID item(Shop shop, Location block) {
        Location at = block.clone().add(0.5, 1.35, 0.5);
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            wares.item(shop.ware()).ifPresent(entity::setItemStack);
            entity.setBillboard(Display.Billboard.VERTICAL);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            prepare(entity, shop);
        });
        return display.getUniqueId();
    }

    private UUID text(Shop shop, Location block) {
        Location at = block.clone().add(0.5, 1.85, 0.5);
        TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, entity -> {
            entity.text(lines(shop));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setShadowed(true);
            prepare(entity, shop);
        });
        return display.getUniqueId();
    }

    private void prepare(Display entity, Shop shop) {
        entity.setPersistent(false);
        entity.setViewRange(0.3f);
        entity.getPersistentDataContainer().set(mark, PersistentDataType.STRING, shop.id().toString());
    }

    private Component lines(Shop shop) {
        int stock = book.knownOf(shop.id()).stock();
        int bundle = shop.prices().bundle();
        String state;
        if (!shop.status().trades()) {
            state = "shops.display.closed";
        } else if (shop.prices().sells() && stock < bundle) {
            state = "shops.display.sold-out";
        } else {
            state = "shops.display.in-stock";
        }
        Component text = messages.get("shops.display.name", "shop", shop.name(), "owner", shop.ownerName());
        if (shop.prices().sells()) {
            text = text.appendNewline().append(messages.get("shops.display.buy", "bundle", Integer.toString(bundle),
                    "price", Fees.format(shop.prices().buy())));
        }
        if (shop.prices().buys()) {
            text = text.appendNewline().append(messages.get("shops.display.sell", "bundle", Integer.toString(bundle),
                    "price", Fees.format(shop.prices().sell())));
        }
        return text.appendNewline().append(messages.get(state, "stock", Integer.toString(stock)));
    }
}

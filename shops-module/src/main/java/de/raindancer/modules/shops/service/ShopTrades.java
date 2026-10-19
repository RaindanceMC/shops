package de.raindancer.modules.shops.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Notice;
import de.raindancer.modules.shops.model.Quote;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.rules.TradeRule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A trade as a player makes it: at the shop's shopkeeper, or from the bazaar. Finds the offer's real chests and the
 * real item, makes sure this thread may touch all of them, and hands them to the {@link TradeDesk} as one shelf.
 *
 * <p>A trade from the bazaar may find the chests' chunks unloaded. On Paper it loads them and holds them a minute with
 * a plugin ticket, so the counter it opened keeps working; on Folia another region's chunk is not this thread's to
 * touch, and the trade is refused as too far.
 */
public final class ShopTrades implements IShopsService {

    /** How far from the shopkeeper a customer may stand and still trade in person. */
    public static final double REACH = 8;

    private static final long HOLD_MILLIS = 60_000;

    private final org.bukkit.plugin.Plugin plugin;
    private final ShopBook book;
    private final ShopLocator locator;
    private final Wares wares;
    private final TradeDesk desk;
    private final NoticeService notices;
    private final LongSupplier clock;
    private record Hold(org.bukkit.Chunk chunk, long until) {
    }

    private final java.util.Map<String, Hold> held = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile ShopsSettings settings;

    public ShopTrades(org.bukkit.plugin.Plugin plugin, ShopBook book, ShopLocator locator, Wares wares,
                      TradeDesk desk, NoticeService notices, ShopsSettings settings, LongSupplier clock) {
        this.plugin = plugin;
        this.book = book;
        this.notices = notices;
        this.clock = clock;
        this.locator = locator;
        this.wares = wares;
        this.desk = desk;
        this.settings = settings;
    }

    @Override
    public void settings(ShopsSettings fresh) {
        settings = fresh;
        desk.settings(fresh);
    }

    public TradeDesk desk() {
        return desk;
    }

    /** Whether trading from the bazaar is on. */
    public boolean remote() {
        return settings.remoteTrades();
    }

    /** What both sides of a trade are, when they can be had here and now. */
    private record Counter(Shop shop, Shelf customer, Shelf stock) {
    }

    /**
     * @param via where the shopkeeper the customer is trading at stands; null when trading from the bazaar
     */
    public TradeDesk.Outcome trade(Player customer, Shop asked, TradeSide side, int bundles, Location via) {
        Counter counter = counter(customer, asked, via);
        if (counter.shop() == null) {
            return new TradeDesk.Outcome(refusalOf(customer, asked, via), Quote.NOTHING, false, null);
        }
        TradeDesk.Outcome outcome = desk.trade(counter.shop(), customer.getUniqueId(), customer.getName(), side,
                bundles, counter.customer(), counter.stock());
        if (outcome.refusal() == Refusal.OWNER_CANNOT_AFFORD) {
            Shop shop = counter.shop();
            notices.tell(new Notice(shop.owner(), clock.getAsLong(), Notice.Kind.CANNOT_AFFORD, shop.id(),
                    shop.name(), shop.prices().sell(), 0, customer.getName()));
        }
        return outcome;
    }

    /** What a trade of so many bundles would do now — for greying a button and saying why. */
    public TradeRule.Verdict preview(Player customer, Shop asked, TradeSide side, int bundles, Location via) {
        Counter counter = counter(customer, asked, via);
        if (counter.shop() == null) {
            Refusal why = refusalOf(customer, asked, via);
            return new TradeRule.Verdict(0, why, false, why);
        }
        return desk.preview(counter.shop(), customer.getUniqueId(), side, bundles, counter.customer(),
                counter.stock());
    }

    public int most(Player customer, Shop asked, TradeSide side, Location via) {
        Counter counter = counter(customer, asked, via);
        return counter.shop() == null ? 0
                : desk.most(counter.shop(), customer.getUniqueId(), side, counter.customer(), counter.stock());
    }

    /** How many of the ware the customer carries, for the sell buttons. */
    public int carried(Player customer, Shop shop) {
        return wares.item(shop.ware()).map(item -> InventoryShelf.player(customer, item).count()).orElse(0);
    }

    /** Counts the offer's chests again — after somebody stocked one — and tells everybody who shows the count. */
    public void recount(Shop shop, Consumer<Shop> show) {
        wares.item(shop.ware()).ifPresent(item -> {
            List<Shelf> shelves = shelves(shop, item, false);
            if (shelves == null) {
                return;
            }
            PooledShelf pooled = new PooledShelf(shelves);
            book.counted(shop.id(), pooled.count(), pooled.room(), book.knownOf(shop.id()).sales());
            show.accept(shop);
        });
    }

    /** Chunks held for bazaar trades whose minute is up are let go. On the global thread, now and then. */
    public void releaseHeld() {
        long now = clock.getAsLong();
        held.values().removeIf(hold -> {
            if (hold.until() > now) {
                return false;
            }
            hold.chunk().removePluginChunkTicket(plugin);
            return true;
        });
    }

    /** Every chunk still held, let go — when the module stops. */
    public void releaseAll() {
        held.values().forEach(hold -> hold.chunk().removePluginChunkTicket(plugin));
        held.clear();
    }

    private Counter counter(Player customer, Shop asked, Location via) {
        if (refusalOf(customer, asked, via) != null) {
            return new Counter(null, null, null);
        }
        Shop shop = book.index().byId(asked.id()).orElseThrow();
        ItemStack item = wares.item(shop.ware()).orElseThrow();
        List<Shelf> shelves = shelves(shop, item, via == null);
        return new Counter(shop, InventoryShelf.player(customer, item), new PooledShelf(shelves));
    }

    /**
     * The offer's chests as shelves — a double chest once, whichever halves are linked — or null when one cannot be
     * touched from this thread.
     *
     * @param load whether an unloaded chest's chunk is loaded and held (a bazaar trade) rather than refused
     */
    private List<Shelf> shelves(Shop shop, ItemStack item, boolean load) {
        List<Shelf> shelves = new ArrayList<>();
        java.util.Set<Inventory> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Set<String> doubles = new java.util.HashSet<>();
        for (Spot chest : shop.chests()) {
            Optional<Location> at = locator.location(chest);
            if (at.isEmpty()) {
                return null;
            }
            Location block = at.get();
            if (!block.isChunkLoaded()) {
                if (!load || Scheduling.isFolia()) {
                    return null;
                }
                hold(block.getChunk());
            } else if (load) {
                hold(block.getChunk());
            }
            if (!Bukkit.isOwnedByCurrentRegion(block)) {
                return null;
            }
            Optional<Inventory> inventory = locator.inventory(chest);
            if (inventory.isEmpty()) {
                continue;
            }
            Inventory found = inventory.get();
            String key = doubleKey(found);
            if (!seen.add(found) || (key != null && !doubles.add(key))) {
                continue;
            }
            shelves.add(InventoryShelf.container(found, item, block));
        }
        return shelves;
    }

    /** The same double chest under one name whichever half its inventory was asked through; null for a single one. */
    private static String doubleKey(Inventory inventory) {
        if (inventory.getHolder(false) instanceof org.bukkit.block.DoubleChest both
                && both.getLeftSide(false) instanceof org.bukkit.block.BlockState left) {
            return left.getWorld().getName() + "|" + left.getX() + "|" + left.getY() + "|" + left.getZ();
        }
        return null;
    }

    private void hold(org.bukkit.Chunk chunk) {
        String key = chunk.getWorld().getName() + "|" + chunk.getX() + "|" + chunk.getZ();
        if (held.put(key, new Hold(chunk, clock.getAsLong() + HOLD_MILLIS)) == null) {
            chunk.addPluginChunkTicket(plugin);
        }
    }

    /** Why this trade cannot even be looked at from here; null when it can. */
    private Refusal refusalOf(Player customer, Shop asked, Location via) {
        Optional<Shop> shop = book.index().byId(asked.id());
        if (shop.isEmpty()) {
            return book.anyShop(asked.id()).isPresent() ? Refusal.NOT_HERE : Refusal.GONE;
        }
        if (via == null) {
            if (!settings.remoteTrades()) {
                return Refusal.NO_REMOTE;
            }
        } else {
            Location from = customer.getLocation();
            if (!from.getWorld().equals(via.getWorld()) || from.distanceSquared(via) > REACH * REACH) {
                return Refusal.TOO_FAR;
            }
        }
        if (shop.get().chests().isEmpty()) {
            return Refusal.NO_CHESTS;
        }
        if (wares.item(shop.get().ware()).isEmpty()) {
            return Refusal.NOTHING_TO_SELL;
        }
        ItemStack item = wares.item(shop.get().ware()).get();
        if (shelves(shop.get(), item, via == null) == null) {
            return Refusal.TOO_FAR;
        }
        return null;
    }

    /** After every trade: the log, the count, the owner's notices. Runs inside the desk's lock, so it only queues. */
    public static Consumer<TradeDesk.Done> afterwards(ShopBook book, NoticeService notices, Consumer<Shop> show) {
        return done -> {
            Shop shop = done.shop();
            book.writer().write(() -> book.trades().add(done.trade()));
            long sales = book.knownOf(shop.id()).sales() + 1;
            book.counted(shop.id(), done.stockAfter(), done.roomAfter(), sales);
            boolean sale = done.trade().side() == TradeSide.BUY;
            notices.tell(new Notice(shop.owner(), done.trade().at(), sale ? Notice.Kind.SOLD : Notice.Kind.BOUGHT,
                    shop.id(), shop.name(), sale ? done.trade().net() : done.trade().gross(), done.trade().items(),
                    done.trade().customerName()));
            int bundle = shop.prices().bundle();
            if (sale && done.stockAfter() < bundle) {
                notices.tell(new Notice(shop.owner(), done.trade().at(), Notice.Kind.OUT_OF_STOCK, shop.id(),
                        shop.name(), done.trade().net(), 0, ""));
            } else if (sale && shop.lowStockAt() > 0 && done.stockAfter() <= shop.lowStockAt()) {
                notices.tell(new Notice(shop.owner(), done.trade().at(), Notice.Kind.LOW_STOCK, shop.id(),
                        shop.name(), done.trade().net(), done.stockAfter(), ""));
            } else if (!sale && done.roomAfter() < bundle) {
                notices.tell(new Notice(shop.owner(), done.trade().at(), Notice.Kind.FULL, shop.id(), shop.name(),
                        done.trade().gross(), 0, ""));
            }
            show.accept(shop);
        };
    }
}

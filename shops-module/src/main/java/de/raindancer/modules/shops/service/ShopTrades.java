package de.raindancer.modules.shops.service;

import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.core.world.chunk.ChunkAt;
import de.raindancer.core.world.chunk.ChunkHolds;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Notice;
import de.raindancer.modules.shops.model.Quote;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.rules.RemoteRule;
import de.raindancer.modules.shops.rules.TradeRule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A trade as a player makes it: at the shop's chest, or from /market. Finds the shop's real chest and the
 * real item, makes sure this thread may touch all of them, and hands them to the {@link TradeDesk} as one shelf.
 *
 * <p>A trade from /market may find the chests' chunks unloaded: {@link #prepare} brings them in through Core's
 * {@code ChunkHolds} first, and the trade runs once they are. A chest that is still not this thread's to touch — on
 * Folia, another region's — refuses the trade as too far.
 */
public final class ShopTrades implements IShopsService {

    /** How far from the shop's chest a customer may stand and still trade in person. */
    public static final double REACH = 8;

    private final ChunkHolds chunks;
    private final ShopBook book;
    private final ShopLocator locator;
    private final Wares wares;
    private final TradeDesk desk;
    private final NoticeService notices;
    private final LongSupplier clock;
    private final RemoteRule remoteRule = new RemoteRule();
    private volatile ShopsSettings settings;

    public ShopTrades(ChunkHolds chunks, ShopBook book, ShopLocator locator, Wares wares,
                      TradeDesk desk, NoticeService notices, ShopsSettings settings, LongSupplier clock) {
        this.chunks = chunks;
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

    /** Whether customers may trade this way from /market, without walking to the shop. */
    public boolean remote(TradeSide side) {
        return remoteRule.fromAfar(side, settings.remoteBuying(), settings.remoteSelling());
    }

    /** Whether customers may trade from /market at all, either way. */
    public boolean remoteAtAll() {
        return remoteRule.anyFromAfar(settings.remoteBuying(), settings.remoteSelling());
    }

    /** What both sides of a trade are, when they can be had here and now. */
    private record Counter(Shop shop, Shelf customer, Shelf stock) {
    }

    /**
     * @param via where the customer opened the shop in person; null when trading from /market. Only whether it is
     *            there counts: in person means within reach of the shop's own chest, wherever it was opened
     */
    public TradeDesk.Outcome trade(Player customer, Shop asked, TradeSide side, int bundles, Location via) {
        Counter counter = counter(customer, asked, side, via);
        if (counter.shop() == null) {
            return new TradeDesk.Outcome(refusalOf(customer, asked, side, via), Quote.NOTHING, false, null);
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
        Counter counter = counter(customer, asked, side, via);
        if (counter.shop() == null) {
            Refusal why = refusalOf(customer, asked, side, via);
            return new TradeRule.Verdict(0, why, false, why);
        }
        return desk.preview(counter.shop(), customer.getUniqueId(), side, bundles, counter.customer(),
                counter.stock());
    }

    public int most(Player customer, Shop asked, TradeSide side, Location via) {
        Counter counter = counter(customer, asked, side, via);
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
            List<Shelf> shelves = shelves(shop, item);
            if (shelves == null) {
                return;
            }
            PooledShelf pooled = new PooledShelf(shelves);
            book.counted(shop.id(), pooled.count(), pooled.room(), book.knownOf(shop.id()).sales());
            show.accept(shop);
        });
    }

    /** Brings the offer's stock chests in for a trade from afar; done when they all are. */
    public CompletableFuture<Void> prepare(Shop shop) {
        List<ChunkAt> wanted = new ArrayList<>();
        for (Spot chest : shop.chests()) {
            if (chest.isOn(book.server())) {
                ChunkAt at = ChunkAt.ofBlock(chest.world(), chest.x(), chest.z());
                if (!wanted.contains(at)) {
                    wanted.add(at);
                }
            }
        }
        return chunks.forAMoment(wanted);
    }

    private Counter counter(Player customer, Shop asked, TradeSide side, Location via) {
        if (refusalOf(customer, asked, side, via) != null) {
            return new Counter(null, null, null);
        }
        Shop shop = book.index().byId(asked.id()).orElseThrow();
        ItemStack item = wares.item(shop.ware()).orElseThrow();
        List<Shelf> shelves = shelves(shop, item);
        return new Counter(shop, InventoryShelf.player(customer, item), new PooledShelf(shelves));
    }

    /**
     * The offer's chests as shelves — a double chest once, whichever halves are linked — or null when one is not
     * loaded or not this thread's to touch.
     */
    private List<Shelf> shelves(Shop shop, ItemStack item) {
        List<Shelf> shelves = new ArrayList<>();
        java.util.Set<Inventory> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Set<String> doubles = new java.util.HashSet<>();
        for (Spot chest : shop.chests()) {
            Optional<Location> at = locator.location(chest);
            if (at.isEmpty()) {
                return null;
            }
            Location block = at.get();
            if (!block.isChunkLoaded() || !Bukkit.isOwnedByCurrentRegion(block)) {
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

    /** Why this trade cannot even be looked at from here; null when it can. */
    private Refusal refusalOf(Player customer, Shop asked, TradeSide side, Location via) {
        Optional<Shop> shop = book.index().byId(asked.id());
        if (shop.isEmpty()) {
            return book.anyShop(asked.id()).isPresent() ? Refusal.NOT_HERE : Refusal.GONE;
        }
        boolean fromAfar = true;
        if (via != null) {
            Location from = customer.getLocation();
            Optional<Location> chest = locator.location(shop.get().spot()).map(at -> at.add(0.5, 0.5, 0.5));
            fromAfar = chest.isEmpty() || !from.getWorld().equals(chest.get().getWorld())
                    || from.distanceSquared(chest.get()) > REACH * REACH;
        }
        Refusal remote = remoteRule.refusal(fromAfar, side, settings.remoteBuying(), settings.remoteSelling());
        if (remote != null) {
            return remote;
        }
        if (shop.get().chests().isEmpty()) {
            return Refusal.NO_CHESTS;
        }
        if (wares.item(shop.get().ware()).isEmpty()) {
            return Refusal.NOTHING_TO_SELL;
        }
        ItemStack item = wares.item(shop.get().ware()).get();
        if (shelves(shop.get(), item) == null) {
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

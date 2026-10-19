package de.raindancer.modules.shops.service;

import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Notice;
import de.raindancer.modules.shops.model.Quote;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.rules.TradeRule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A trade as a player makes it: at the container itself, or through a statue of the store. Finds the real container
 * and the real item, makes sure this thread may touch both, and hands them to the {@link TradeDesk}.
 */
public final class ShopTrades implements IShopsService {

    /** How far from a shop's container a customer may stand and still trade at it directly. */
    public static final double REACH = 8;

    /** How far from its statue a shop may be when statues do not reach everywhere. */
    public static final double STATUE_REACH = 64;

    private final ShopBook book;
    private final ShopLocator locator;
    private final Wares wares;
    private final TradeDesk desk;
    private final NoticeService notices;
    private final LongSupplier clock;
    private volatile ShopsSettings settings;

    public ShopTrades(ShopBook book, ShopLocator locator, Wares wares, TradeDesk desk, NoticeService notices,
                      ShopsSettings settings, LongSupplier clock) {
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

    /** What both sides of a trade are, when they can be had here and now. */
    private record Counter(Shop shop, Shelf customer, Shelf stock) {
    }

    /**
     * @param via the statue the customer is trading through; null when they stand at the shop
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

    /** Counts the container again — after its owner closed it — and tells everybody who shows the count. */
    public void recount(Shop shop, Inventory inventory, Consumer<Shop> show) {
        wares.item(shop.ware()).ifPresent(item -> {
            Location at = locator.location(shop.spot()).orElse(null);
            if (at == null) {
                return;
            }
            InventoryShelf shelf = InventoryShelf.container(inventory, item, at);
            book.counted(shop.id(), shelf.count(), shelf.room(), book.knownOf(shop.id()).sales());
            show.accept(shop);
        });
    }

    private Counter counter(Player customer, Shop asked, Location via) {
        if (refusalOf(customer, asked, via) != null) {
            return new Counter(null, null, null);
        }
        Shop shop = book.index().byId(asked.id()).orElseThrow();
        Location at = locator.location(shop.spot()).orElseThrow();
        Inventory inventory = locator.inventory(shop).orElseThrow();
        ItemStack item = wares.item(shop.ware()).orElseThrow();
        return new Counter(shop, InventoryShelf.player(customer, item), InventoryShelf.container(inventory, item, at));
    }

    /** Why this trade cannot even be looked at from here; null when it can. */
    private Refusal refusalOf(Player customer, Shop asked, Location via) {
        Optional<Shop> shop = book.index().byId(asked.id());
        if (shop.isEmpty()) {
            return book.anyShop(asked.id()).isPresent() ? Refusal.NOT_HERE : Refusal.GONE;
        }
        Optional<Location> at = locator.location(shop.get().spot());
        if (at.isEmpty()) {
            return Refusal.NOT_HERE;
        }
        Location block = at.get();
        if (!block.isChunkLoaded() || !Bukkit.isOwnedByCurrentRegion(block)) {
            return Refusal.TOO_FAR;
        }
        Location from = via == null ? customer.getLocation() : via;
        double reach = via == null ? REACH : settings.statuesReachEverywhere() ? Double.MAX_VALUE : STATUE_REACH;
        if (!from.getWorld().equals(block.getWorld())
                || (reach < Double.MAX_VALUE && from.distanceSquared(block.clone().add(0.5, 0.5, 0.5)) > reach * reach)) {
            return Refusal.TOO_FAR;
        }
        if (locator.inventory(shop.get()).isEmpty()) {
            return Refusal.GONE;
        }
        if (wares.item(shop.get().ware()).isEmpty()) {
            return Refusal.NOTHING_TO_SELL;
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

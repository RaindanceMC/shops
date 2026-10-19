package de.raindancer.modules.shops.service;

import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Listing;
import de.raindancer.modules.shops.model.Rating;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Statue;
import de.raindancer.modules.shops.model.Store;
import de.raindancer.modules.shops.store.NoticeStore;
import de.raindancer.modules.shops.store.ReviewStore;
import de.raindancer.modules.shops.store.ShopIndex;
import de.raindancer.modules.shops.store.ShopStore;
import de.raindancer.modules.shops.store.ShopWriter;
import de.raindancer.modules.shops.store.StoreStore;
import de.raindancer.modules.shops.store.TradeStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * What this module knows, in memory, and the one way anything is written back.
 *
 * <p>This server's shops live in the {@link ShopIndex}, because every click at every container asks about them. Stores
 * and statues are few and network-wide. Other servers' shops are a copy refreshed now and then, for the browser
 * only: nothing here ever writes one of them back, so a stale copy cannot overwrite a newer row.
 */
public final class ShopBook implements IShopsService {

    private final ShopIndex index;
    private final ShopStore shops;
    private final StoreStore stores;
    private final TradeStore trades;
    private final ReviewStore reviews;
    private final NoticeStore notices;
    private final ShopWriter writer;
    private final Supplier<String> server;

    private final Map<UUID, Store> storesById = new ConcurrentHashMap<>();
    private final Map<UUID, Statue> statues = new ConcurrentHashMap<>();
    private final Map<UUID, ShopStore.Known> known = new ConcurrentHashMap<>();
    private final Map<UUID, Rating> ratings = new ConcurrentHashMap<>();
    private volatile List<Shop> elsewhere = List.of();

    public ShopBook(ShopIndex index, ShopStore shops, StoreStore stores, TradeStore trades, ReviewStore reviews,
                    NoticeStore notices, ShopWriter writer, Supplier<String> server) {
        this.index = index;
        this.shops = shops;
        this.stores = stores;
        this.trades = trades;
        this.reviews = reviews;
        this.notices = notices;
        this.writer = writer;
        this.server = server;
    }

    @Override
    public void settings(ShopsSettings settings) {
        // Nothing read from the settings: what is known does not depend on them.
    }

    /** Everything from the database. Blocking; at start, off the server thread. */
    public void load() {
        String here = server.get();
        List<Shop> all = shops.all();
        index.replaceAll(all.stream().filter(shop -> shop.spot().isOn(here)).toList());
        elsewhere = all.stream().filter(shop -> !shop.spot().isOn(here)).toList();
        storesById.clear();
        stores.all().forEach(store -> storesById.put(store.id(), store));
        statues.clear();
        stores.statues().forEach(statue -> statues.put(statue.entity(), statue));
        known.clear();
        known.putAll(shops.known());
        ratings.clear();
        ratings.putAll(reviews.ratings());
    }

    /** Other servers' shops, stores and every rating, again. Blocking; on a timer, off the server thread. */
    public void refreshNetwork() {
        String here = server.get();
        elsewhere = shops.all().stream().filter(shop -> !shop.spot().isOn(here)).toList();
        for (Store store : stores.all()) {
            storesById.put(store.id(), store);
        }
        Map<UUID, ShopStore.Known> counted = shops.known();
        for (Shop shop : elsewhere) {
            ShopStore.Known count = counted.get(shop.id());
            if (count != null) {
                known.put(shop.id(), count);
            }
        }
        ratings.putAll(reviews.ratings());
    }

    // ------------------------------------------------------------------ shops

    public ShopIndex index() {
        return index;
    }

    public void saveShop(Shop shop) {
        index.put(shop);
        writer.write(() -> shops.save(shop));
    }

    public void removeShop(UUID id) {
        index.remove(id);
        known.remove(id);
        writer.write(() -> shops.delete(id));
    }

    public void counted(UUID shop, int stock, int room, long sales) {
        known.put(shop, new ShopStore.Known(stock, room, sales));
        writer.write(() -> shops.stock(shop, stock, room, sales));
    }

    public ShopStore.Known knownOf(UUID shop) {
        return known.getOrDefault(shop, new ShopStore.Known(0, 0, 0));
    }

    /** A shop by id here or elsewhere. */
    public Optional<Shop> anyShop(UUID id) {
        return index.byId(id).or(() -> elsewhere.stream().filter(shop -> shop.id().equals(id)).findFirst());
    }

    /** The shops of this server and, if asked, of the others — as the browser lists them. */
    public List<Listing> listings(boolean network) {
        List<Listing> all = new ArrayList<>();
        for (Shop shop : index.all()) {
            all.add(listing(shop, true));
        }
        if (network) {
            for (Shop shop : elsewhere) {
                all.add(listing(shop, false));
            }
        }
        return all;
    }

    public Listing listing(Shop shop, boolean here) {
        ShopStore.Known count = knownOf(shop.id());
        return new Listing(shop, count.stock(), count.room(), ratings.getOrDefault(shop.id(), Rating.NONE),
                count.sales(), here);
    }

    /** Every shop this player owns, here and elsewhere. */
    public List<Shop> ownedBy(UUID owner) {
        List<Shop> owned = new ArrayList<>(index.ownedBy(owner));
        elsewhere.stream().filter(shop -> shop.isOwner(owner)).forEach(owned::add);
        owned.sort(Comparator.comparingLong(Shop::createdAt));
        return owned;
    }

    public List<Shop> inStore(UUID store) {
        List<Shop> in = new ArrayList<>();
        for (Shop shop : index.all()) {
            if (shop.store().equals(store)) {
                in.add(shop);
            }
        }
        for (Shop shop : elsewhere) {
            if (shop.store().equals(store)) {
                in.add(shop);
            }
        }
        in.sort(Comparator.comparing((Shop shop) -> shop.ware().label()).thenComparing(Shop::name));
        return in;
    }

    // ------------------------------------------------------------------ stores and statues

    public Optional<Store> store(UUID id) {
        return Optional.ofNullable(storesById.get(id));
    }

    public List<Store> storesOf(UUID owner) {
        return storesById.values().stream().filter(store -> store.isOwner(owner))
                .sorted(Comparator.comparingLong(Store::createdAt)).toList();
    }

    public List<Store> allStores() {
        return storesById.values().stream().sorted(Comparator.comparing(Store::name)).toList();
    }

    public void saveStore(Store store) {
        storesById.put(store.id(), store);
        writer.write(() -> stores.save(store));
    }

    public void removeStore(UUID id) {
        storesById.remove(id);
        statues.values().removeIf(statue -> statue.store().equals(id));
        writer.write(() -> stores.delete(id));
    }

    public List<Statue> statuesOf(UUID store) {
        return statues.values().stream().filter(statue -> statue.store().equals(store))
                .sorted(Comparator.comparingLong(Statue::placedAt)).toList();
    }

    public Optional<Statue> statue(UUID entity) {
        return Optional.ofNullable(statues.get(entity));
    }

    public void placed(Statue statue) {
        statues.put(statue.entity(), statue);
        writer.write(() -> stores.place(statue));
    }

    public void unplaced(UUID entity) {
        statues.remove(entity);
        writer.write(() -> stores.unplace(entity));
    }

    // ------------------------------------------------------------------ ratings and the rest

    public Rating rating(UUID shop) {
        return ratings.getOrDefault(shop, Rating.NONE);
    }

    public void rated(UUID shop, Rating rating) {
        ratings.put(shop, rating);
    }

    public ShopWriter writer() {
        return writer;
    }

    public TradeStore trades() {
        return trades;
    }

    public ReviewStore reviews() {
        return reviews;
    }

    public NoticeStore notices() {
        return notices;
    }

    public String server() {
        return server.get();
    }
}

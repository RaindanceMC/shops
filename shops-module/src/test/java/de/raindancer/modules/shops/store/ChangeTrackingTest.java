package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.core.testkit.TestDatabases;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.model.Trade;
import de.raindancer.modules.shops.model.Ware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("database")
@DisplayName("A trade is one transaction; other servers' shops are re-read only when they changed")
class ChangeTrackingTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());
    private static final UUID STORE = UUID.nameUUIDFromBytes("alices-store".getBytes());

    @TempDir
    Path folder;

    private Database database;
    private final AtomicInteger writes = new AtomicInteger();

    @BeforeEach
    void open() {
        Database real = TestDatabases.open(folder.resolve("shops.db"), ShopsDatabase.SCHEMA);
        database = (Database) Proxy.newProxyInstance(Database.class.getClassLoader(), new Class<?>[]{Database.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("write")) {
                        writes.incrementAndGet();
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static Shop shop(String name, String server) {
        return Shop.open(UUID.nameUUIDFromBytes(name.getBytes()), STORE, ALICE, "Alice", name,
                new Spot(server, "world", 1, 64, 0), new Ware("DIAMOND", "encoded-diamond", "Diamond"),
                new Prices(Money.of(1000), Money.of(400), 4), 1_000, 0);
    }

    @Test
    @DisplayName("the log line and the new count of a trade reach the database in a single write")
    void oneWritePerTrade() {
        ShopStore shops = new ShopStore(database);
        TradeStore trades = new TradeStore(database);
        Shop depot = shop("Depot", "survival");
        shops.save(depot);
        writes.set(0);
        Trade trade = new Trade(0, depot.id(), ALICE, 2_000, BOB, "Bob", TradeSide.BUY, 4, Money.of(1000),
                Money.of(50), Money.of(950), "Diamond");
        assertThat(trades.record(trade, 60, 200, 7)).isTrue();
        assertThat(writes.get()).isEqualTo(1);
        assertThat(trades.recent(depot.id(), 5)).hasSize(1);
        assertThat(shops.known().get(depot.id())).isEqualTo(new ShopStore.Known(60, 200, 7));
    }

    @Test
    @DisplayName("what changed on other servers shows in the fingerprint; this server's own shops and no change do not")
    void fingerprint() {
        ShopStore shops = new ShopStore(database);
        shops.save(shop("Here", "survival"));
        Shop there = shop("There", "creative");
        shops.save(there);
        var first = shops.fingerprintOutside("survival").orElseThrow();
        assertThat(shops.fingerprintOutside("survival")).contains(first);

        shops.save(shop("Here2", "survival"));
        shops.stock(shop("Here", "survival").id(), 5, 5, 5);
        assertThat(shops.fingerprintOutside("survival")).as("own shops are not asked about").contains(first);

        shops.stock(there.id(), 5, 5, 1);
        var afterStock = shops.fingerprintOutside("survival").orElseThrow();
        assertThat(afterStock).isNotEqualTo(first);

        shops.save(there.withName("Renamed"));
        var afterSave = shops.fingerprintOutside("survival").orElseThrow();
        assertThat(afterSave).isNotEqualTo(afterStock);

        shops.delete(there.id());
        assertThat(shops.fingerprintOutside("survival").orElseThrow()).isNotEqualTo(afterSave);
    }
}

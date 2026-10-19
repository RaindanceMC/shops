package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.core.testkit.TestDatabases;
import de.raindancer.modules.shops.model.Notice;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Rating;
import de.raindancer.modules.shops.model.Review;
import de.raindancer.modules.shops.model.Role;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.model.Statement;
import de.raindancer.modules.shops.model.Status;
import de.raindancer.modules.shops.model.Trade;
import de.raindancer.modules.shops.model.Ware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("database")
@DisplayName("Shops, trades, reviews and notices on disk")
class StoresTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());
    private static final UUID CAROL = UUID.nameUUIDFromBytes("carol".getBytes());

    @TempDir
    Path folder;

    private Database database;

    @BeforeEach
    void open() {
        database = TestDatabases.open(folder.resolve("shops.db"), ShopsDatabase.SCHEMA);
    }

    private static Shop shop(String name, String server, int x) {
        return Shop.open(UUID.nameUUIDFromBytes(name.getBytes()), ALICE, "Alice", name,
                new Spot(server, "world", x, 64, 0), new Ware("DIAMOND", "encoded-diamond", "Diamond"),
                new Prices(Money.of(1000), Money.of(400), 4), 1_000, 0);
    }

    @Test
    @DisplayName("a shop comes back exactly as saved, staff and all; saving again replaces it")
    void shops() {
        ShopStore store = new ShopStore(database);
        Shop saved = shop("Depot", "survival", 1).withStaff(BOB, Role.MANAGER).withStaff(CAROL, Role.CLERK)
                .withBuyLimit(256).withLowStockAt(8).withStatus(Status.PAUSED).withDisplay(false)
                .withRentPaidTo(99_000);
        assertThat(store.save(saved)).isTrue();
        assertThat(store.all()).containsExactly(saved);

        Shop renamed = saved.withName("Diamond Depot").withStaff(CAROL, null);
        store.save(renamed);
        assertThat(store.all()).containsExactly(renamed);
        assertThat(store.byId(saved.id())).contains(renamed);
    }

    @Test
    @DisplayName("a server loads only its own shops to guard; the browser reads everybody's")
    void servers() {
        ShopStore store = new ShopStore(database);
        store.save(shop("Here", "survival", 1));
        store.save(shop("There", "creative", 2));
        assertThat(store.onServer("survival")).extracting(Shop::name).containsExactly("Here");
        assertThat(store.all()).hasSize(2);
    }

    @Test
    @DisplayName("removing a shop takes its staff rows with it; its trades stay for the statement")
    void remove() {
        ShopStore store = new ShopStore(database);
        TradeStore trades = new TradeStore(database);
        Shop depot = shop("Depot", "survival", 1).withStaff(BOB, Role.CLERK);
        store.save(depot);
        trades.add(trade(depot, BOB, TradeSide.BUY, 4, 1000, 50, 2_000));
        assertThat(store.delete(depot.id())).isTrue();
        assertThat(store.all()).isEmpty();
        assertThat(trades.recent(depot.id(), 10)).hasSize(1);
    }

    @Test
    @DisplayName("stock as last counted rides along for shops on other servers")
    void stock() {
        ShopStore store = new ShopStore(database);
        Shop depot = shop("Depot", "survival", 1);
        store.save(depot);
        store.stock(depot.id(), 77, 300, 5);
        ShopStore.Known known = store.known().get(depot.id());
        assertThat(known.stock()).isEqualTo(77);
        assertThat(known.room()).isEqualTo(300);
        assertThat(known.sales()).isEqualTo(5);
    }

    private static Trade trade(Shop shop, UUID customer, TradeSide side, int items, long gross, long cut, long at) {
        return new Trade(0, shop.id(), shop.owner(), at, customer, customer.equals(BOB) ? "Bob" : "Carol", side,
                items, Money.of(gross), Money.of(cut), Money.of(gross - cut), shop.ware().label());
    }

    @Test
    @DisplayName("trades: newest first, numbered, and summed into an owner's statement since a date")
    void trades() {
        TradeStore trades = new TradeStore(database);
        Shop depot = shop("Depot", "survival", 1);
        trades.add(trade(depot, BOB, TradeSide.BUY, 4, 1000, 50, 2_000));
        trades.add(trade(depot, BOB, TradeSide.BUY, 8, 2000, 100, 3_000));
        trades.add(trade(depot, CAROL, TradeSide.SELL, 4, 400, 0, 4_000));
        trades.add(trade(depot, CAROL, TradeSide.BUY, 4, 1000, 50, 500));

        List<Trade> recent = trades.recent(depot.id(), 2);
        assertThat(recent).extracting(Trade::at).containsExactly(4_000L, 3_000L);
        assertThat(recent.get(0).id()).isPositive();

        Statement statement = trades.statement(ALICE, 1_000, 5);
        assertThat(statement.sales()).isEqualTo(2);
        assertThat(statement.itemsSold()).isEqualTo(12);
        assertThat(statement.earned()).isEqualTo(Money.of(2850));
        assertThat(statement.cut()).isEqualTo(Money.of(150));
        assertThat(statement.purchases()).isEqualTo(1);
        assertThat(statement.spent()).isEqualTo(Money.of(400));
        assertThat(statement.profit()).isEqualTo(Money.of(2450));
        assertThat(statement.regulars()).extracting(Statement.Customer::name).containsExactly("Bob");
        assertThat(statement.regulars().get(0).spent()).isEqualTo(Money.of(3000));

        assertThat(trades.tradedSince(BOB, depot.id(), 2_500)).isTrue();
        assertThat(trades.tradedSince(BOB, depot.id(), 3_500)).isFalse();
        assertThat(trades.count(depot.id())).isEqualTo(4);
    }

    @Test
    @DisplayName("old trades are forgotten after the server's keeping time")
    void forgetting() {
        TradeStore trades = new TradeStore(database);
        Shop depot = shop("Depot", "survival", 1);
        trades.add(trade(depot, BOB, TradeSide.BUY, 4, 1000, 0, 100));
        trades.add(trade(depot, BOB, TradeSide.BUY, 4, 1000, 0, 900));
        assertThat(trades.forgetBefore(500)).isEqualTo(1);
        assertThat(trades.recent(depot.id(), 10)).extracting(Trade::at).containsExactly(900L);
    }

    @Test
    @DisplayName("one review per customer and shop — a second one replaces the first; the rating averages them")
    void reviews() {
        ReviewStore reviews = new ReviewStore(database);
        UUID shop = UUID.randomUUID();
        reviews.save(new Review(shop, BOB, "Bob", 2, "slow", 10));
        reviews.save(new Review(shop, BOB, "Bob", 4, "better now", 20));
        reviews.save(new Review(shop, CAROL, "Carol", 5, "", 30));
        assertThat(reviews.of(shop, 10)).extracting(Review::text).containsExactly("", "better now");
        Rating rating = reviews.rating(shop);
        assertThat(rating.count()).isEqualTo(2);
        assertThat(rating.average()).isEqualTo(4.5);
        assertThat(reviews.ratings()).containsEntry(shop, rating);
        assertThat(reviews.remove(shop, BOB)).isTrue();
        assertThat(reviews.rating(shop).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("notices wait for their owner and are handed over once — taking them empties the queue")
    void notices() {
        NoticeStore notices = new NoticeStore(database);
        UUID shop = UUID.randomUUID();
        notices.add(new Notice(ALICE, 1, Notice.Kind.SOLD, shop, "Depot", Money.of(500), 4, "Bob"));
        notices.add(new Notice(ALICE, 2, Notice.Kind.OUT_OF_STOCK, shop, "Depot", Money.ZERO, 0, ""));
        notices.add(new Notice(BOB, 3, Notice.Kind.REVIEWED, shop, "Depot", Money.ZERO, 0, "Carol"));
        List<Notice> alices = notices.take(ALICE);
        assertThat(alices).extracting(Notice::kind).containsExactly(Notice.Kind.SOLD, Notice.Kind.OUT_OF_STOCK);
        assertThat(alices.get(0).amount()).isEqualTo(Money.of(500));
        assertThat(notices.take(ALICE)).isEmpty();
        assertThat(notices.take(BOB)).hasSize(1);
        notices.add(new Notice(CAROL, 5, Notice.Kind.SOLD, shop, "Depot", Money.ZERO, 1, ""));
        assertThat(notices.forgetBefore(10)).isEqualTo(1);
    }
}

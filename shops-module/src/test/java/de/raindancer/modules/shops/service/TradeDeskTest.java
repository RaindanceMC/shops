package de.raindancer.modules.shops.service;

import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.model.Status;
import de.raindancer.modules.shops.model.Ware;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("A trade: all of it, or none of it")
class TradeDeskTest {

    private static final UUID OWNER = UUID.nameUUIDFromBytes("owner".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());

    /** Money in memory, with switches for every way a bank can say no. */
    static final class FakeTill implements Till {
        final Map<UUID, Long> balances = new ConcurrentHashMap<>();
        volatile boolean refuseTaking;
        volatile boolean refuseGiving;
        volatile boolean there = true;
        final List<String> sources = java.util.Collections.synchronizedList(new ArrayList<>());

        long of(UUID who) {
            return balances.getOrDefault(who, 0L);
        }

        @Override
        public boolean available() {
            return there;
        }

        @Override
        public Money balance(UUID who) {
            return Money.of(of(who));
        }

        @Override
        public synchronized EconomyResult take(UUID who, Money amount, String reason, String source) {
            sources.add(source);
            if (refuseTaking || of(who) < amount.minor()) {
                return EconomyResult.failed(EconomyResult.Outcome.NOT_ENOUGH, amount, balance(who));
            }
            balances.put(who, of(who) - amount.minor());
            return EconomyResult.done(amount, balance(who));
        }

        @Override
        public synchronized EconomyResult give(UUID who, Money amount, String reason, String source) {
            sources.add(source);
            if (refuseGiving) {
                return EconomyResult.failed(EconomyResult.Outcome.TOO_MUCH, amount, balance(who));
            }
            balances.put(who, of(who) + amount.minor());
            return EconomyResult.done(amount, balance(who));
        }

        @Override
        public synchronized EconomyResult refund(UUID who, Money amount, String reason, String source) {
            sources.add("refund:" + source);
            balances.put(who, of(who) + amount.minor());
            return EconomyResult.done(amount, balance(who));
        }
    }

    /** A container as a number of items and a capacity. */
    static final class FakeShelf implements Shelf {
        int items;
        final int capacity;
        boolean lies;

        FakeShelf(int items, int capacity) {
            this.items = items;
            this.capacity = capacity;
        }

        @Override
        public synchronized int count() {
            return items;
        }

        @Override
        public synchronized int room() {
            return capacity - items;
        }

        @Override
        public synchronized boolean remove(int amount) {
            if (lies || amount > items) {
                return false;
            }
            items -= amount;
            return true;
        }

        @Override
        public synchronized int add(int amount) {
            int fits = Math.min(amount, capacity - items);
            items += fits;
            return amount - fits;
        }
    }

    private final FakeTill till = new FakeTill();
    private final List<TradeDesk.Done> done = java.util.Collections.synchronizedList(new ArrayList<>());
    private final TradeDesk desk = new TradeDesk(till, new ShopLocks(), ShopsSettings.DEFAULTS.withServerCutPercent(10),
            done::add, () -> 5_000L);

    private static Shop shop(Prices prices) {
        return Shop.open(UUID.nameUUIDFromBytes("depot".getBytes()), OWNER, OWNER, "Owner", "Depot",
                new Spot("s", "world", 0, 64, 0), new Ware("DIAMOND", "", null), prices, 0, 0);
    }

    private final Shop depot = shop(new Prices(Money.of(1000), Money.of(500), 2));

    @Test
    @DisplayName("buying: the customer pays, the owner gets it less the cut, the items move")
    void buy() {
        till.balances.put(BOB, 5_000L);
        FakeShelf stock = new FakeShelf(10, 54);
        FakeShelf bag = new FakeShelf(0, 36);

        TradeDesk.Outcome outcome = desk.trade(depot, BOB, "Bob", TradeSide.BUY, 3, bag, stock);

        assertThat(outcome.refusal()).isNull();
        assertThat(outcome.quote().items()).isEqualTo(6);
        assertThat(till.of(BOB)).isEqualTo(2_000);
        assertThat(till.of(OWNER)).isEqualTo(2_700);
        assertThat(stock.items).isEqualTo(4);
        assertThat(bag.items).isEqualTo(6);
        assertThat(done).singleElement().satisfies(trade -> {
            assertThat(trade.trade().gross()).isEqualTo(Money.of(3000));
            assertThat(trade.trade().cut()).isEqualTo(Money.of(300));
            assertThat(trade.trade().at()).isEqualTo(5_000L);
            assertThat(trade.stockAfter()).isEqualTo(4);
        });
        assertThat(till.sources).containsExactly("shops.buy", "shops.sale");
    }

    @Test
    @DisplayName("selling: the owner pays, the customer gets it less the cut, the items go into the shop")
    void sell() {
        till.balances.put(OWNER, 5_000L);
        FakeShelf stock = new FakeShelf(0, 54);
        FakeShelf bag = new FakeShelf(5, 36);

        TradeDesk.Outcome outcome = desk.trade(depot, BOB, "Bob", TradeSide.SELL, 2, bag, stock);

        assertThat(outcome.refusal()).isNull();
        assertThat(till.of(OWNER)).isEqualTo(4_000);
        assertThat(till.of(BOB)).isEqualTo(900);
        assertThat(stock.items).isEqualTo(4);
        assertThat(bag.items).isEqualTo(1);
    }

    @Test
    @DisplayName("a payment the bank refuses puts the items back where they were")
    void paymentRefused() {
        till.balances.put(BOB, 5_000L);
        till.refuseTaking = true;
        FakeShelf stock = new FakeShelf(10, 54);
        FakeShelf bag = new FakeShelf(0, 36);

        TradeDesk.Outcome outcome = desk.trade(depot, BOB, "Bob", TradeSide.BUY, 1, bag, stock);

        assertThat(outcome.refusal()).isEqualTo(Refusal.YOU_CANNOT_AFFORD);
        assertThat(stock.items).isEqualTo(10);
        assertThat(bag.items).isZero();
        assertThat(till.of(BOB)).isEqualTo(5_000);
        assertThat(done).isEmpty();
    }

    @Test
    @DisplayName("an owner who cannot be paid: the customer gets their money back, the shop its items")
    void ownerNotPaid() {
        till.balances.put(BOB, 5_000L);
        till.refuseGiving = true;
        FakeShelf stock = new FakeShelf(10, 54);
        FakeShelf bag = new FakeShelf(0, 36);

        TradeDesk.Outcome outcome = desk.trade(depot, BOB, "Bob", TradeSide.BUY, 1, bag, stock);

        assertThat(outcome.refusal()).isEqualTo(Refusal.MONEY_REFUSED);
        assertThat(till.of(BOB)).isEqualTo(5_000);
        assertThat(till.of(OWNER)).isZero();
        assertThat(stock.items).isEqualTo(10);
        assertThat(bag.items).isZero();
        assertThat(till.sources).contains("refund:shops.buy");
    }

    @Test
    @DisplayName("selling, a customer who cannot be paid: the owner is refunded and the customer keeps the items")
    void customerNotPaid() {
        till.balances.put(OWNER, 5_000L);
        till.refuseGiving = true;
        FakeShelf stock = new FakeShelf(0, 54);
        FakeShelf bag = new FakeShelf(4, 36);

        assertThat(desk.trade(depot, BOB, "Bob", TradeSide.SELL, 2, bag, stock).refusal())
                .isEqualTo(Refusal.MONEY_REFUSED);
        assertThat(till.of(OWNER)).isEqualTo(5_000);
        assertThat(bag.items).isEqualTo(4);
        assertThat(stock.items).isZero();
    }

    @Test
    @DisplayName("stock that vanished between the count and the taking refuses, and nothing is paid")
    void vanished() {
        till.balances.put(BOB, 5_000L);
        FakeShelf stock = new FakeShelf(10, 54);
        stock.lies = true;
        assertThat(desk.trade(depot, BOB, "Bob", TradeSide.BUY, 1, new FakeShelf(0, 36), stock).refusal())
                .isEqualTo(Refusal.OUT_OF_STOCK);
        assertThat(till.of(BOB)).isEqualTo(5_000);
    }

    @Test
    @DisplayName("too little of something trims the trade to what is possible, and says by what")
    void trimmed() {
        till.balances.put(BOB, 2_500L);
        TradeDesk.Outcome outcome = desk.trade(depot, BOB, "Bob", TradeSide.BUY, 10, new FakeShelf(0, 36),
                new FakeShelf(40, 54));
        assertThat(outcome.quote().bundles()).isEqualTo(2);
        assertThat(outcome.trimmed()).isTrue();
        assertThat(outcome.limitedBy()).isEqualTo(Refusal.YOU_CANNOT_AFFORD);
    }

    @Test
    @DisplayName("a paused shop, no economy, or a shop busy with somebody else refuses at once")
    void refusals() {
        till.balances.put(BOB, 5_000L);
        assertThat(desk.trade(depot.withStatus(Status.PAUSED), BOB, "Bob", TradeSide.BUY, 1, new FakeShelf(0, 36),
                new FakeShelf(10, 54)).refusal()).isEqualTo(Refusal.PAUSED);
        till.there = false;
        assertThat(desk.trade(depot, BOB, "Bob", TradeSide.BUY, 1, new FakeShelf(0, 36), new FakeShelf(10, 54))
                .refusal()).isEqualTo(Refusal.NO_ECONOMY);
        till.there = true;
        ShopLocks locks = new ShopLocks();
        TradeDesk held = new TradeDesk(till, locks, ShopsSettings.DEFAULTS, done::add, () -> 0L);
        assertThat(locks.tryHold(depot.id())).isTrue();
        assertThat(held.trade(depot, BOB, "Bob", TradeSide.BUY, 1, new FakeShelf(0, 36), new FakeShelf(10, 54))
                .refusal()).isEqualTo(Refusal.BUSY);
        locks.release(depot.id());
        assertThat(held.trade(depot, BOB, "Bob", TradeSide.BUY, 1, new FakeShelf(0, 36), new FakeShelf(10, 54))
                .refusal()).isNull();
    }

    @Test
    @DisplayName("eight customers at once never buy more than there is, and no coin appears or vanishes")
    void crowd() throws Exception {
        int customers = 8;
        List<UUID> people = new ArrayList<>();
        for (int at = 0; at < customers; at++) {
            UUID person = UUID.nameUUIDFromBytes(("c" + at).getBytes());
            people.add(person);
            till.balances.put(person, 1_000_000L);
        }
        FakeShelf stock = new FakeShelf(100, 1000);
        Shop cheap = shop(new Prices(Money.of(10), Money.ZERO, 1));
        TradeDesk patient = new TradeDesk(till, new ShopLocks(), ShopsSettings.DEFAULTS, done::add, () -> 0L);
        ExecutorService pool = Executors.newFixedThreadPool(customers);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger bought = new AtomicInteger();
        List<Future<?>> work = new ArrayList<>();
        for (UUID person : people) {
            FakeShelf bag = new FakeShelf(0, 10_000);
            work.add(pool.submit(() -> {
                go.await();
                for (int round = 0; round < 60; round++) {
                    TradeDesk.Outcome outcome = patient.trade(cheap, person, "c", TradeSide.BUY, 1, bag, stock);
                    if (outcome.refusal() == null) {
                        bought.addAndGet(outcome.quote().items());
                    }
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> each : work) {
            each.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(bought.get()).isEqualTo(100 - stock.items);
        assertThat(stock.items).isNotNegative();
        long total = till.balances.values().stream().mapToLong(Long::longValue).sum();
        assertThat(total).isEqualTo(customers * 1_000_000L);
    }
}

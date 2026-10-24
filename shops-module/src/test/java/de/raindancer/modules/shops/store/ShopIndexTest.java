package de.raindancer.modules.shops.store;

import de.raindancer.core.social.economy.Money;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.model.Ware;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The shops of this server, in memory")
class ShopIndexTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());

    private static Shop shop(int x) {
        return Shop.open(UUID.nameUUIDFromBytes(("shop" + x).getBytes()), ALICE, ALICE, "Alice", "Shop " + x,
                new Spot("s", "world", 0, 70, 0), new Ware("DIAMOND", "", null),
                Prices.selling(Money.of(100), 1), 0, 0).linked(chest(x));
    }

    private static Spot chest(int x) {
        return new Spot("s", "world", x, 64, 0);
    }

    @Test
    @DisplayName("a chest shop is found by its chest and by its sign; moving or removing the sign forgets the old spot")
    void signs() {
        ShopIndex index = new ShopIndex();
        Spot sign = chest(1).offset(0, 0, 1);
        Shop shop = Shop.chest(UUID.randomUUID(), ALICE, ALICE, "Alice", "Rocks", chest(1), sign, null,
                new Ware("STONE", "", null), Prices.selling(Money.of(100), 1), 0, 0);
        index.put(shop);
        assertThat(index.at(chest(1))).contains(shop);
        assertThat(index.bySign(sign)).contains(shop);
        assertThat(index.at(sign)).as("the sign is no stock").isEmpty();

        Spot moved = chest(1).offset(0, 1, 0);
        Shop resigned = shop.withSign(moved);
        index.put(resigned);
        assertThat(index.bySign(sign)).isEmpty();
        assertThat(index.bySign(moved)).contains(resigned);

        index.remove(shop.id());
        assertThat(index.bySign(moved)).isEmpty();
    }

    @Test
    @DisplayName("found by id, by any of its chests and by owner; replacing keeps one entry; removing forgets the chests")
    void lookups() {
        ShopIndex index = new ShopIndex();
        Shop one = shop(1).linked(chest(2));
        index.put(one);
        assertThat(index.at(chest(1))).contains(one);
        assertThat(index.at(chest(2))).contains(one);
        assertThat(index.at(one.spot())).as("the shopkeeper's block is no chest").isEmpty();
        assertThat(index.byId(one.id())).contains(one);
        assertThat(index.ownedBy(ALICE)).containsExactly(one);

        Shop renamed = one.withName("Renamed");
        index.put(renamed);
        assertThat(index.all()).containsExactly(renamed);

        index.remove(one.id());
        assertThat(index.at(chest(1))).isEmpty();
        assertThat(index.at(chest(2))).isEmpty();
        assertThat(index.ownedBy(ALICE)).isEmpty();
    }

    @Test
    @DisplayName("a chest unlinked from an offer is no longer found as its stock")
    void unlinked() {
        ShopIndex index = new ShopIndex();
        Shop one = shop(1).linked(chest(2));
        index.put(one);
        index.put(one.unlinked(chest(1)));
        assertThat(index.at(chest(1))).isEmpty();
        assertThat(index.at(chest(2))).isPresent();
    }

    @Test
    @DisplayName("readers never see a shop by block that is not the one by id, while writers churn")
    void concurrent() throws Exception {
        ShopIndex index = new ShopIndex();
        int shops = 50;
        for (int x = 0; x < shops; x++) {
            index.put(shop(x));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<String> broken = new AtomicReference<>();
        List<Future<?>> work = new ArrayList<>();
        for (int writer = 0; writer < 4; writer++) {
            int seed = writer;
            work.add(pool.submit(() -> {
                go.await();
                for (int round = 0; round < 2_000; round++) {
                    Shop shop = shop((round + seed) % shops);
                    if (round % 7 == 0) {
                        index.remove(shop.id());
                    } else {
                        index.put(shop.withName("Round " + round));
                    }
                }
                return null;
            }));
        }
        for (int reader = 0; reader < 4; reader++) {
            work.add(pool.submit(() -> {
                go.await();
                for (int round = 0; round < 4_000; round++) {
                    Spot spot = new Spot("s", "world", round % shops, 64, 0);
                    index.at(spot).ifPresent(found -> {
                        if (!found.isLinked(spot)) {
                            broken.set("found " + found.chests() + " at " + spot);
                        }
                    });
                    index.ownedBy(ALICE);
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> each : work) {
            each.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(broken.get()).isNull();
        for (Shop shop : index.all()) {
            for (Spot chest : shop.chests()) {
                assertThat(index.at(chest)).contains(shop);
            }
        }
    }
}

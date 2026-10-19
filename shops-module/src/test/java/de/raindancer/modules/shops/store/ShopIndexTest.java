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
                new Spot("s", "world", x, 64, 0), new Ware("DIAMOND", "", null),
                Prices.selling(Money.of(100), 1), 0, 0);
    }

    @Test
    @DisplayName("found by id, by block and by owner; replacing keeps one entry; removing forgets the block")
    void lookups() {
        ShopIndex index = new ShopIndex();
        Shop one = shop(1);
        index.put(one);
        assertThat(index.at(one.spot())).contains(one);
        assertThat(index.byId(one.id())).contains(one);
        assertThat(index.ownedBy(ALICE)).containsExactly(one);

        Shop renamed = one.withName("Renamed");
        index.put(renamed);
        assertThat(index.all()).containsExactly(renamed);

        index.remove(one.id());
        assertThat(index.at(one.spot())).isEmpty();
        assertThat(index.ownedBy(ALICE)).isEmpty();
    }

    @Test
    @DisplayName("a shop moved to another block is no longer found at the old one")
    void moved() {
        ShopIndex index = new ShopIndex();
        Shop one = shop(1);
        index.put(one);
        Shop elsewhere = new Shop(one.id(), one.store(), one.owner(), one.ownerName(), one.name(), one.spot().offset(5, 0, 0),
                one.ware(), one.prices(), one.status(), one.staff(), one.createdAt(), one.rentPaidTo(),
                one.buyLimit(), one.lowStockAt(), one.display());
        index.put(elsewhere);
        assertThat(index.at(one.spot())).isEmpty();
        assertThat(index.at(elsewhere.spot())).contains(elsewhere);
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
                        if (!found.spot().equals(spot)) {
                            broken.set("found " + found.spot() + " at " + spot);
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
            assertThat(index.at(shop.spot())).contains(shop);
        }
    }
}

package de.raindancer.modules.shops.model;

import de.raindancer.core.social.economy.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("An offer's stock chests and a shop's staff")
class OfferAndShopTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());
    private static final UUID CAROL = UUID.nameUUIDFromBytes("carol".getBytes());
    private static final Spot KEEPER = new Spot("s", "world", 0, 64, 0);

    private static Shop offer() {
        return Shop.open(UUID.randomUUID(), UUID.randomUUID(), ALICE, "Alice", "Diamonds", KEEPER,
                new Ware("DIAMOND", "", null), Prices.selling(Money.of(100), 1), 0, 0);
    }

    @Test
    @DisplayName("an offer starts without chests; linking adds a chest once, unlinking takes it away")
    void links() {
        Shop offer = offer();
        assertThat(offer.chests()).isEmpty();
        Spot chest = KEEPER.offset(2, 0, 0);
        Shop linked = offer.linked(chest).linked(chest);
        assertThat(linked.chests()).containsExactly(chest);
        assertThat(linked.isLinked(chest)).isTrue();
        assertThat(linked.unlinked(chest).chests()).isEmpty();
    }

    @Test
    @DisplayName("moving the shopkeeper moves where the offer is sold, never its chests")
    void keeperMoves() {
        Spot chest = KEEPER.offset(2, 0, 0);
        Shop moved = offer().linked(chest).withSpot(KEEPER.offset(0, 0, 5));
        assertThat(moved.spot()).isEqualTo(KEEPER.offset(0, 0, 5));
        assertThat(moved.chests()).containsExactly(chest);
    }

    @Test
    @DisplayName("staff belong to the shop: clerks stock, managers also price; the owner is never staff")
    void staff() {
        Store shop = new Store(UUID.randomUUID(), ALICE, "Alice", "Alice's", "", 0, Map.of())
                .withStaff(BOB, Role.CLERK).withStaff(CAROL, Role.MANAGER).withStaff(ALICE, Role.CLERK);
        assertThat(shop.staff()).containsOnlyKeys(BOB, CAROL);
        assertThat(shop.mayStock(BOB)).isTrue();
        assertThat(shop.mayManage(BOB)).isFalse();
        assertThat(shop.mayManage(CAROL)).isTrue();
        assertThat(shop.mayManage(ALICE)).isTrue();
        assertThat(shop.mayStock(UUID.randomUUID())).isFalse();
        assertThat(shop.withStaff(BOB, null).mayStock(BOB)).isFalse();
    }
}

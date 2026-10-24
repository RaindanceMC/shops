package de.raindancer.modules.shops.model;

import de.raindancer.core.social.economy.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What floats over a chest shop")
class FloatingTest {

    @Test
    @DisplayName("every line on by default; each can be switched on its own")
    void toggles() {
        assertThat(Floating.ALL.item()).isTrue();
        assertThat(Floating.ALL.price()).isTrue();
        assertThat(Floating.ALL.name()).isTrue();
        Floating noPrice = Floating.ALL.toggled(Floating.Line.PRICE);
        assertThat(noPrice).isEqualTo(new Floating(true, false, true));
        assertThat(noPrice.toggled(Floating.Line.PRICE)).isEqualTo(Floating.ALL);
        assertThat(Floating.ALL.toggled(Floating.Line.ITEM).item()).isFalse();
        assertThat(Floating.ALL.toggled(Floating.Line.NAME).name()).isFalse();
        assertThat(Floating.NONE.anything()).isFalse();
        assertThat(Floating.ALL.anything()).isTrue();
        assertThat(Floating.ALL.shows(Floating.Line.NAME)).isTrue();
        assertThat(noPrice.shows(Floating.Line.PRICE)).isFalse();
        assertThat(noPrice.hasText()).isTrue();
        assertThat(new Floating(true, false, false).hasText()).isFalse();
    }

    @Test
    @DisplayName("written as one number and read back exactly; nonsense reads as everything on")
    void encoding() {
        for (boolean item : new boolean[]{true, false}) {
            for (boolean price : new boolean[]{true, false}) {
                for (boolean name : new boolean[]{true, false}) {
                    Floating lines = new Floating(item, price, name);
                    assertThat(Floating.parse(lines.encode())).isEqualTo(lines);
                }
            }
        }
        assertThat(Floating.parse(-5)).isEqualTo(Floating.ALL);
        assertThat(Floating.parse(99)).isEqualTo(Floating.ALL);
    }

    @Test
    @DisplayName("a chest shop sells from exactly its own chest, carries its sign and frame, and floats every line")
    void chestShop() {
        Spot chest = new Spot("s", "world", 1, 64, 1);
        Spot sign = chest.offset(1, 0, 0);
        UUID frame = UUID.randomUUID();
        Shop shop = Shop.chest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Alice", "Rocks", chest,
                sign, frame, new Ware("STONE", "", null), Prices.selling(Money.of(100), 1), 5, 0);
        assertThat(shop.spot()).isEqualTo(chest);
        assertThat(shop.chests()).containsExactly(chest);
        assertThat(shop.sign()).isEqualTo(sign);
        assertThat(shop.frame()).isEqualTo(frame);
        assertThat(shop.floating()).isEqualTo(Floating.ALL);
        assertThat(shop.status()).isEqualTo(Status.OPEN);
        Shop quiet = shop.withFloating(Floating.NONE).withPrices(Prices.selling(Money.of(5), 2)).withName("Stones");
        assertThat(quiet.floating()).isEqualTo(Floating.NONE);
        assertThat(quiet.sign()).isEqualTo(sign);
        assertThat(quiet.frame()).isEqualTo(frame);
        assertThat(quiet.withFrame(null).frame()).isNull();
        assertThat(quiet.withSign(chest.offset(0, 1, 0)).sign()).isEqualTo(chest.offset(0, 1, 0));
    }
}

package de.raindancer.modules.shops;

import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Players' shops' settings")
class ShopsSettingsTest {

    private final ShopsSettings defaults = ShopsSettings.DEFAULTS;

    @Test
    @DisplayName("every number's default lies inside its own range")
    void defaultsInRange() throws ReflectiveOperationException {
        List<String> outside = new ArrayList<>();
        for (RecordComponent component : ShopsSettings.class.getRecordComponents()) {
            Range range = component.getAnnotation(Range.class);
            if (range == null) {
                continue;
            }
            double number = ((Number) component.getAccessor().invoke(defaults)).doubleValue();
            if (number < range.min() || number > range.max()) {
                outside.add(component.getName() + " = " + number);
            }
        }
        assertThat(outside).isEmpty();
    }

    @Test
    @DisplayName("every key is there, unique and spelled the file's way")
    void keys() {
        Set<String> seen = new HashSet<>();
        for (RecordComponent component : ShopsSettings.class.getRecordComponents()) {
            Key key = component.getAnnotation(Key.class);
            assertThat(key).as(component.getName()).isNotNull();
            assertThat(key.value()).matches("[a-z0-9-]+");
            assertThat(seen.add(key.value())).as("duplicate " + key.value()).isTrue();
        }
    }

    @Test
    @DisplayName("the defaults: free to open, no rent, no cut, 27 chest shops each, every line floating")
    void spelledOut() {
        assertThat(defaults.openingPrice()).isEqualTo("0");
        assertThat(defaults.rentPerWeek()).isEqualTo("0");
        assertThat(defaults.cutPercent()).isZero();
        assertThat(defaults.shopLimit()).isEqualTo(27);
        assertThat(defaults.floatingLines()).isTrue();
        assertThat(defaults.containers()).contains("CHEST", "BARREL").noneMatch(name -> name.contains("SHULKER"));
        assertThat(defaults.closedWorlds()).isEmpty();
        assertThat(defaults.styledNames()).isTrue();
        assertThat(defaults.partialTrades()).isTrue();
        assertThat(defaults.hoppersFillShops()).isTrue();
        assertThat(defaults.remoteBuying()).as("customers go to the shop to buy").isFalse();
        assertThat(defaults.remoteSelling()).isFalse();
        assertThat(defaults.withRemoteSelling(true).remoteBuying()).as("each on its own").isFalse();
        assertThat(defaults.withRemoteBuying(true).remoteSelling()).isFalse();
    }

    @Test
    @DisplayName("what a file says out of range is clamped when read back")
    void clamped() {
        assertThat(defaults.withServerCutPercent(250).cutPercent()).isEqualTo(100);
        assertThat(defaults.withServerCutPercent(-3).cutPercent()).isZero();
        assertThat(defaults.withMostChestShops(-1).shopLimit()).isZero();
        assertThat(defaults.withMostChestShops(9_000).shopLimit()).isEqualTo(500);
        assertThat(defaults.withFloatingLines(false).floatingLines()).isFalse();
        assertThat(defaults.withItemSpinSeconds(-4).spinTicks()).as("still").isZero();
        assertThat(defaults.withItemSpinSeconds(999).spinTicks()).isEqualTo(60 * 20);
    }

    @Test
    @DisplayName("the floating item turns round once every six seconds")
    void spins() {
        assertThat(defaults.spinTicks()).isEqualTo(120);
        assertThat(defaults.withItemSpinSeconds(0).spinTicks()).isZero();
    }

    @Test
    @DisplayName("and bobs like a dropped item, switched off on its own")
    void bobs() {
        assertThat(defaults.itemBobs()).isTrue();
        assertThat(defaults.withItemBobs(false).itemBobs()).isFalse();
        assertThat(defaults.withItemBobs(false).spinTicks()).as("the turn stays").isEqualTo(120);
        assertThat(defaults.withItemSpinSeconds(0).itemBobs()).isTrue();
    }
}

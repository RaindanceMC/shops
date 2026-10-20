package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;
import de.raindancer.modules.shops.model.Refusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Names, rent, reviews, limits and which blocks may be shops")
class SmallRulesTest {

    @Nested
    @DisplayName("a shop's name")
    class Names {

        private final ShopNameRule rule = new ShopNameRule();

        @Test
        @DisplayName("words of a sensible length pass; markup, control characters and blanks do not")
        void names() {
            assertThat(rule.check("Diamond Depot", 24)).isEmpty();
            assertThat(rule.check("Bob's Ores & Co.", 24)).isEmpty();
            assertThat(rule.check("", 24)).contains(Refusal.BAD_NAME);
            assertThat(rule.check("   ", 24)).contains(Refusal.BAD_NAME);
            assertThat(rule.check("<red>Loud", 24)).as("would paint every line it appears in").contains(Refusal.BAD_NAME);
            assertThat(rule.check("Tab\there", 24)).contains(Refusal.BAD_NAME);
            assertThat(rule.check("x".repeat(25), 24)).contains(Refusal.BAD_NAME);
        }
    }

    @Nested
    @DisplayName("rent")
    class Rent {

        private final RentRule rule = new RentRule();
        private final long week = Duration.ofDays(7).toMillis();

        @Test
        @DisplayName("due once the paid time is over; a shop without rent is never due")
        void due() {
            assertThat(rule.isDue(1000, 999)).isFalse();
            assertThat(rule.isDue(1000, 1000)).isTrue();
            assertThat(rule.isDue(0, 5000)).as("0 is a shop without rent").isFalse();
        }

        @Test
        @DisplayName("paying adds whole weeks from the later of today and the old date — late pay is not back pay")
        void extend() {
            assertThat(rule.extended(10 * week, 3 * week, 1)).isEqualTo(11 * week);
            assertThat(rule.extended(2 * week, 5 * week, 2)).isEqualTo(7 * week);
        }

        @Test
        @DisplayName("how far ahead a shop may be paid is capped, so nobody prepays a decade")
        void ahead() {
            assertThat(rule.weeksPayable(0, 0, 8)).isEqualTo(8);
            assertThat(rule.weeksPayable(5 * week, 0, 8)).isEqualTo(3);
            assertThat(rule.weeksPayable(9 * week, 0, 8)).isZero();
        }
    }

    @Nested
    @DisplayName("reviews")
    class Reviews {

        private final ReviewRule rule = new ReviewRule();

        @Test
        @DisplayName("customers review; owners and strangers do not; a review is short")
        void reviews() {
            assertThat(rule.check(false, true, 4, "Great diamonds", 120)).isEmpty();
            assertThat(rule.check(true, true, 5, "Best shop", 120)).contains(Refusal.OWN_SHOP);
            assertThat(rule.check(false, false, 1, "Never been", 120)).contains(Refusal.NOT_A_CUSTOMER);
            assertThat(rule.check(false, true, 3, "x".repeat(121), 120)).contains(Refusal.REVIEW_TOO_LONG);
            assertThat(rule.check(false, true, 3, "<bold>shout", 120)).contains(Refusal.REVIEW_TOO_LONG);
            assertThat(rule.check(false, true, 6, "", 120)).as("stars out of range are refused, not clamped")
                    .contains(Refusal.BAD_STARS);
        }
    }

    @Nested
    @DisplayName("how many shops")
    class Limits {

        private final ShopLimitRule rule = new ShopLimitRule();

        @Test
        @DisplayName("below the limit, or unlimited, there is room for one more")
        void limits() {
            assertThat(rule.mayOpen(2, 3, false)).isTrue();
            assertThat(rule.mayOpen(3, 3, false)).isFalse();
            assertThat(rule.mayOpen(300, 3, true)).isTrue();
            assertThat(rule.mayOpen(0, 0, false)).as("zero means only staff and permission holders").isFalse();
        }
    }

    @Nested
    @DisplayName("which blocks")
    class Blocks {

        private final PlacementRule rule = new PlacementRule();

        @Test
        @DisplayName("only the containers the server lists, named in any case")
        void containers() {
            Set<String> allowed = rule.names(java.util.List.of("chest", " Barrel ", "TRAPPED_CHEST", "", "nonsense"));
            assertThat(allowed).containsExactlyInAnyOrder("CHEST", "BARREL", "TRAPPED_CHEST", "NONSENSE");
            assertThat(rule.mayBeShop("BARREL", allowed)).isTrue();
            assertThat(rule.mayBeShop("SHULKER_BOX", allowed))
                    .as("a box can be picked up and carried off, shop and all").isFalse();
            assertThat(rule.mayBeShop("ENDER_CHEST", Set.of("ENDER_CHEST")))
                    .as("everybody's own inventory, never a shop").isFalse();
        }

        @Test
        @DisplayName("a world the server shut shops out of is refused")
        void worlds() {
            assertThat(rule.worldAllowed("world", Set.of())).isTrue();
            assertThat(rule.worldAllowed("arena", rule.names(java.util.List.of("arena", "lobby")))).isFalse();
            assertThat(rule.worldAllowed("ARENA", rule.names(java.util.List.of("arena")))).isFalse();
        }
    }
}

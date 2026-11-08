package de.raindancer.modules.shops.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What /market completes")
class MarketTabRuleTest {

    private final MarketTabRule rule = new MarketTabRule();
    private final List<String> wares = List.of("Diamond", "Diamond Pickaxe", "Dirt", "Oak Log");

    @Test
    @DisplayName("the first word: the verbs that start with it, plot words only with a market world, admin only for staff")
    void verbs() {
        assertThat(rule.suggest(new String[]{""}, false, false, wares)).containsExactly("browse", "sell", "mine",
                "help", "statement");
        assertThat(rule.suggest(new String[]{"p"}, false, true, wares)).containsExactly("plot");
        assertThat(rule.suggest(new String[]{"a"}, true, false, wares)).containsExactly("admin");
        assertThat(rule.suggest(new String[]{"a"}, false, false, wares)).isEmpty();
    }

    @Test
    @DisplayName("what is on sale completes a bare search too, by its first word")
    void bareSearch() {
        assertThat(rule.suggest(new String[]{"di"}, false, false, wares)).containsExactly("diamond", "dirt");
    }

    @Test
    @DisplayName("after browse, find and sell: the wares, word by word")
    void wares() {
        assertThat(rule.suggest(new String[]{"browse", ""}, false, false, wares))
                .containsExactly("diamond", "dirt", "oak");
        assertThat(rule.suggest(new String[]{"sell", "diamond", "p"}, false, false, wares))
                .containsExactly("pickaxe");
        assertThat(rule.suggest(new String[]{"find", "oak", "l"}, false, false, wares)).containsExactly("log");
        assertThat(rule.suggest(new String[]{"browse", "zzz"}, false, false, wares)).isEmpty();
    }

    @Test
    @DisplayName("admin takes rotate, and only with a market world and staff rights")
    void admin() {
        assertThat(rule.suggest(new String[]{"admin", "r"}, true, true, wares)).containsExactly("rotate");
        assertThat(rule.suggest(new String[]{"admin", ""}, true, false, wares)).isEmpty();
        assertThat(rule.suggest(new String[]{"admin", ""}, false, true, wares)).isEmpty();
    }

    @Test
    @DisplayName("words the verbs take nothing after complete nothing")
    void nothingMore() {
        assertThat(rule.suggest(new String[]{"mine", ""}, false, true, wares)).isEmpty();
        assertThat(rule.suggest(new String[]{"plot", ""}, false, true, wares)).isEmpty();
    }
}

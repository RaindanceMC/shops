package de.raindancer.modules.shops;

import de.raindancer.core.ui.help.HelpLint;
import de.raindancer.modules.api.ModuleCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuideTest {

    @Test
    @DisplayName("the guide pages read, quote only real settings and cover every command")
    void theGuideIsComplete() {
        assertThat(HelpLint.of(Path.of("src/main/resources/rains-help"))
                .module("shops")
                .settings(ShopsSettings.class, ShopsSettings.DEFAULTS)
                .settings(MarketSettings.class, MarketSettings.DEFAULTS)
                .otherTopics("shop")
                .commands(ShopsCommands.declared().stream().map(ModuleCommand::name).toList())
                .problems()).isEmpty();
    }
}

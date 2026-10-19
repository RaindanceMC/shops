package de.raindancer.modules.shops;

import de.raindancer.modules.api.WordingContract;

import java.nio.file.Path;

/** The wording rules, kept by this module. See {@code WordingContract} for what is checked. */
class WordingTest implements WordingContract {

    @Override
    public Path moduleSource() {
        return Path.of("src/main/java/de/raindancer/modules/shops");
    }

    @Override
    public Path messagesFile() {
        return Path.of("src/main/resources/de/raindancer/modules/shops/messages.yml");
    }

    @Override
    public int fewestWordingLines() {
        return 110;
    }

    @Override
    public int fewestSourceFiles() {
        return 60;
    }
}

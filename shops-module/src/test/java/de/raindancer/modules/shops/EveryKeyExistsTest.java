package de.raindancer.modules.shops;

import de.raindancer.modules.shops.model.Notice;
import de.raindancer.modules.shops.model.Refusal;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Every key the code names is in messages.yml, read the way the server reads it — the made-up ones too. */
class EveryKeyExistsTest {

    /** Strings that look like keys and are not: the economy sources money is booked under. */
    private static final Set<String> SOURCES = Set.of("shops.buy", "shops.sale", "shops.purchase", "shops.payout",
            "shops.opening", "shops.rent", "shops.travel", "shops.statue");

    private static YamlConfiguration wording(String file) {
        return YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/de/raindancer/modules/shops/" + file).toFile());
    }

    @Test
    @DisplayName("every shops.* key in the source, every refusal and every notice has a line")
    void defined() throws IOException {
        YamlConfiguration yaml = wording("messages.yml");
        Pattern key = Pattern.compile("\"(shops\\.[a-z0-9-]+(?:\\.[a-z0-9-]+)*)\"");
        List<String> used = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path path : sources.filter(file -> file.toString().endsWith(".java")).toList()) {
                key.matcher(Files.readString(path)).results().map(found -> found.group(1))
                        .filter(found -> !SOURCES.contains(found)).forEach(used::add);
            }
        }
        for (Refusal refusal : Refusal.values()) {
            used.add(refusal.key());
        }
        for (Notice.Kind kind : Notice.Kind.values()) {
            used.add(kind.key());
        }
        assertThat(used).as("the scan found no keys, so it checks nothing").hasSizeGreaterThan(100);
        assertThat(used.stream().distinct()).allSatisfy(name -> assertThat(yaml.isString(name)).as(name).isTrue());
    }

    @Test
    @DisplayName("the serious lines are lines the playful file has too, with the same placeholders")
    void seriousMatches() {
        YamlConfiguration playful = wording("messages.yml");
        YamlConfiguration serious = wording("messages-serious.yml");
        Pattern placeholder = Pattern.compile("<([a-z]+)>");
        for (String name : serious.getKeys(true)) {
            if (!serious.isString(name)) {
                continue;
            }
            assertThat(playful.isString(name)).as(name + " has no playful line").isTrue();
            List<String> said = placeholder.matcher(playful.getString(name)).results().map(found -> found.group(1))
                    .filter(tag -> !Set.of("red", "green", "yellow", "gray", "white", "gold", "aqua", "bold",
                            "newline", "dark_gray", "dark_red").contains(tag)).distinct().sorted().toList();
            List<String> plain = placeholder.matcher(serious.getString(name)).results().map(found -> found.group(1))
                    .filter(tag -> !Set.of("red", "green", "yellow", "gray", "white", "gold", "aqua", "bold",
                            "newline", "dark_gray", "dark_red").contains(tag)).distinct().sorted().toList();
            assertThat(plain).as(name).isSubsetOf(said);
        }
    }
}

package de.raindancer.modules.shops.store;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.world.build.Schematic;
import de.raindancer.modules.shops.market.Prefabs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The owner's own market builds, read from {@code market/stalls} and {@code market/houses} in the plugin's folder —
 * Sponge schematics ({@code .schem}) as WorldEdit writes them. The folders are made, with a note on how a build must
 * lie, the first time. Read at start: new ones are used for streets generated after the next start.
 */
public final class PrefabFiles {

    private static final String NOTE = """
            Your own builds for the market world, as WorldEdit schematics (.schem, Sponge format 2 or 3).
            They are used for parts of the market generated after the next server start; what is built already stays.

            stalls/  — a stall for every plot nobody has built on yet (one picked per plot).
                       At most 12 x 12. Copy it with its front facing NORTH; the lowest layer is the ground it stands on.
            houses/  — house fronts along the cross streets, one after another while they fit; the rest of a row
                       is built the usual way. At most 7 deep (x) and 25 long (z); front facing WEST.
            Builds at most 23 high. Blocks only: chest contents, signs' text and entities are not copied.
            """;

    private final Path folder;
    private final LogChannel log;

    /** @param folder the plugin's market folder, {@code plugins/<plugin>/market} */
    public PrefabFiles(Path folder, LogChannel log) {
        this.folder = folder;
        this.log = log;
    }

    public Prefabs read() {
        try {
            Files.createDirectories(folder.resolve("stalls"));
            Files.createDirectories(folder.resolve("houses"));
            Path note = folder.resolve("README.txt");
            if (!Files.exists(note)) {
                Files.writeString(note, NOTE);
            }
        } catch (IOException failed) {
            log.warn(failed, "Could not make the market's build folders: {}", failed.getMessage());
            return Prefabs.NONE;
        }
        List<Schematic> stalls = schematics(folder.resolve("stalls"), Prefabs::whyNotAStall, "stall");
        List<Schematic> houses = schematics(folder.resolve("houses"), Prefabs::whyNotAHouse, "house front");
        if (!stalls.isEmpty() || !houses.isEmpty()) {
            log.info("The market builds with {} stall(s) and {} house front(s) of your own.", stalls.size(),
                    houses.size());
        }
        return new Prefabs(stalls, houses);
    }

    private List<Schematic> schematics(Path in, Function<Schematic, String> whyNot, String what) {
        List<Schematic> read = new ArrayList<>();
        try (Stream<Path> files = Files.list(in)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".schem")).sorted().toList()) {
                try {
                    Schematic schematic = Schematic.read(file);
                    String why = whyNot.apply(schematic);
                    if (why.isEmpty()) {
                        read.add(schematic);
                    } else {
                        log.warn("Left out the market {} {}: it {}.", what, file.getFileName(), why);
                    }
                } catch (IOException | RuntimeException broken) {
                    log.warn("Could not read the market {} {}: {}", what, file.getFileName(), broken.getMessage());
                }
            }
        } catch (IOException failed) {
            log.warn(failed, "Could not list {}: {}", in, failed.getMessage());
        }
        return read;
    }
}

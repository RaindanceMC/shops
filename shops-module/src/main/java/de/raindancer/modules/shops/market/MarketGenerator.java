package de.raindancer.modules.shops.market;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The market world's generator: every block from {@link MarketDesigns}, nothing of vanilla's — no noise, caves,
 * ores, trees, structures or mobs. Endless, so new streets and plots appear wherever somebody walks.
 */
public final class MarketGenerator extends ChunkGenerator {

    private final MarketDesigns designs;
    private final Map<String, BlockData> parsed = new ConcurrentHashMap<>();

    public MarketGenerator(MarketDesigns designs) {
        this.designs = designs;
    }

    @Override
    public void generateNoise(@NotNull WorldInfo info, @NotNull Random random, int chunkX, int chunkZ,
                              @NotNull ChunkData data) {
        int bottom = data.getMinHeight();
        int stoneTo = MarketDesigns.GROUND - 3;
        data.setRegion(0, bottom, 0, 16, bottom + 1, 16, Material.BEDROCK);
        data.setRegion(0, bottom + 1, 0, 16, stoneTo, 16, Material.STONE);
        int top = Math.min(MarketDesigns.highest(), data.getMaxHeight() - 1);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = (chunkX << 4) + x;
                int wz = (chunkZ << 4) + z;
                for (int y = stoneTo; y <= top; y++) {
                    String state = designs.stateAt(wx, y, wz);
                    if (state != null) {
                        data.setBlock(x, y, z, block(state));
                    }
                }
            }
        }
    }

    private BlockData block(String state) {
        return parsed.computeIfAbsent(state, Bukkit::createBlockData);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public @NotNull Biome getBiome(@NotNull WorldInfo world, int x, int y, int z) {
                return plains();
            }

            @Override
            public @NotNull List<Biome> getBiomes(@NotNull WorldInfo world) {
                return List.of(plains());
            }
        };
    }

    private static Biome plains() {
        Biome found = Registry.BIOME.get(NamespacedKey.minecraft("plains"));
        return found == null ? Biome.PLAINS : found;
    }

    /** South of the fountain, looking at it. */
    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        MarketLayout layout = designs.layout();
        return new Location(world, layout.spawnX() + 0.5, MarketDesigns.GROUND + 1, layout.spawnZ() + 5.5, 180, 0);
    }
}

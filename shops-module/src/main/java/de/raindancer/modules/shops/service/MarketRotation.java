package de.raindancer.modules.shops.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.world.chunk.ChunkAt;
import de.raindancer.core.world.chunk.ChunkHolds;
import de.raindancer.modules.shops.MarketSettings;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.market.MarketDesigns;
import de.raindancer.modules.shops.market.MarketLayout;
import de.raindancer.modules.shops.model.MarketPlot;
import de.raindancer.modules.shops.model.Notice;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.rules.PlotBuildRule;
import de.raindancer.modules.shops.rules.RotationRule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Rotating the market's stalls: every built plot one place nearer the square, the nearest to the back.
 *
 * <p>In this order, and never otherwise: (1) the world is saved and its region files are copied to a backup;
 * (2) every plot taking part is read whole — blocks with their contents (chests, signs, barrels) and the entities on
 * it (item frames, armour stands) — and only once all of them are read (3) is anything written: each build is put on
 * its new plot, turned round when that plot faces the other street; then (4) the chest shops on them, the claims and
 * the owners hear of it. A plot that cannot be read stops the whole rotation before anything is written, and the
 * entities already taken up are put back where they were.
 */
public final class MarketRotation implements IShopsService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private final Plugin plugin;
    private final MarketPlots plots;
    private final ShopBook book;
    private final ChunkHolds chunks;
    private final NoticeService notices;
    private final Supplier<MarketSettings> market;
    private final Consumer<Shop> moved;
    private final Path backups;
    private final LongSupplier clock;
    private final LogChannel log;
    private final RotationRule rule = new RotationRule();
    private final AtomicBoolean running = new AtomicBoolean();

    /** A plot as it was read: its blocks (snapshots, contents and all) and its entities, serialised. */
    private record Taken(MarketLayout.Plot plot, List<BlockState> blocks, List<TakenEntity> entities) {
    }

    private record TakenEntity(byte[] data, double x, double y, double z, float yaw, float pitch, BlockFace facing) {
    }

    /**
     * @param moved   told of every chest shop that moved with its plot, at its new place
     * @param backups where the world's region files are copied before a rotation
     */
    public MarketRotation(Plugin plugin, MarketPlots plots, ShopBook book, ChunkHolds chunks, NoticeService notices,
                          Supplier<MarketSettings> market, Consumer<Shop> moved, Path backups, LongSupplier clock,
                          LogChannel log) {
        this.plugin = plugin;
        this.plots = plots;
        this.book = book;
        this.chunks = chunks;
        this.notices = notices;
        this.market = market;
        this.moved = moved;
        this.backups = backups;
        this.clock = clock;
        this.log = log;
    }

    @Override
    public void settings(ShopsSettings settings) {
        // Read through the market's own settings.
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Whether a rotation is due. Blocking — asks the database; off the server thread. */
    public boolean due() {
        MarketSettings now = market.get();
        if (!now.enabled() || now.rotationEveryDays() <= 0) {
            return false;
        }
        long last = plots.lastRotation();
        if (last == 0) {
            plots.startClock(clock.getAsLong());
            return false;
        }
        return clock.getAsLong() - last >= now.rotationEveryDays() * 86_400_000L;
    }

    /** Rotates now. Done with how many plots moved, or exceptionally when it could not. */
    public CompletableFuture<Integer> rotate(World world) {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("a rotation is already running"));
        }
        List<MarketLayout.Plot> built = new ArrayList<>();
        for (MarketPlot claim : plots.all()) {
            plots.layout().plot(claim.plot()).ifPresent(built::add);
        }
        Map<MarketLayout.Plot, MarketLayout.Plot> moves = rule.moves(built, plots.layout().order());
        if (moves.isEmpty()) {
            running.set(false);
            plots.rotated(Map.of(), clock.getAsLong());
            return CompletableFuture.completedFuture(0);
        }
        return backup(world)
                .thenCompose(ignored -> chunks.forAMoment(chunksOf(world, moves.keySet())))
                .thenCompose(ignored -> takeAll(world, moves.keySet()))
                .thenCompose(taken -> putAll(world, moves, taken).thenApply(ignored -> taken))
                .thenApply(taken -> {
                    finish(world, moves);
                    return moves.size();
                })
                .whenComplete((count, failed) -> {
                    running.set(false);
                    if (failed != null) {
                        log.error(failed, "The market's stalls could not rotate: {}", failed.getMessage());
                    } else {
                        log.info("The market's stalls rotated: {} plot(s) moved.", count);
                    }
                });
    }

    // ------------------------------------------------------------------ 1. the backup

    private CompletableFuture<Void> backup(World world) {
        CompletableFuture<Void> saved = new CompletableFuture<>();
        Scheduling.global(plugin, () -> {
            try {
                world.save();
                saved.complete(null);
            } catch (RuntimeException failed) {
                saved.completeExceptionally(failed);
            }
        });
        return saved.thenRunAsync(() -> {
            Path folder = world.getWorldFolder().toPath();
            Path target = backups.resolve(world.getName() + "-" + STAMP.format(Instant.ofEpochMilli(clock.getAsLong())));
            for (String part : List.of("region", "entities", "poi")) {
                copy(folder.resolve(part), target.resolve(part));
            }
            log.info("Backed up the market world to {} before rotating.", target);
            prune(world.getName());
        });
    }

    private static void copy(Path from, Path to) {
        if (!Files.isDirectory(from)) {
            return;
        }
        try (Stream<Path> files = Files.walk(from)) {
            for (Path file : files.toList()) {
                Path into = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(into);
                } else {
                    Files.copy(file, into, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException failed) {
            throw new UncheckedIOException("backing up " + from + " failed", failed);
        }
    }

    /** Only the newest backups are kept, as many as the settings say; only this plugin's own backups are touched. */
    private void prune(String worldName) {
        if (!Files.isDirectory(backups)) {
            return;
        }
        try (Stream<Path> folders = Files.list(backups)) {
            List<Path> mine = folders.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(worldName + "-"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed()).toList();
            for (Path old : mine.subList(Math.min(mine.size(), market.get().backups()), mine.size())) {
                try (Stream<Path> files = Files.walk(old)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(file);
                    }
                }
                log.info("Removed the old market backup {}.", old.getFileName());
            }
        } catch (IOException failed) {
            log.warn(failed, "Could not tidy the market backups: {}", failed.getMessage());
        }
    }

    /** Every chunk a moving plot touches — each one both gives a build and takes one. */
    private static List<ChunkAt> chunksOf(World world, java.util.Collection<MarketLayout.Plot> moving) {
        List<ChunkAt> wanted = new ArrayList<>();
        for (MarketLayout.Plot plot : moving) {
            for (int x = plot.minX() >> 4; x <= plot.maxX() >> 4; x++) {
                for (int z = plot.minZ() >> 4; z <= plot.maxZ() >> 4; z++) {
                    ChunkAt at = ChunkAt.ofBlock(world.getName(), x << 4, z << 4);
                    if (!wanted.contains(at)) {
                        wanted.add(at);
                    }
                }
            }
        }
        return wanted;
    }

    // ------------------------------------------------------------------ 2. reading every plot

    private CompletableFuture<Map<MarketLayout.Plot, Taken>> takeAll(World world,
                                                                     java.util.Collection<MarketLayout.Plot> moving) {
        Map<MarketLayout.Plot, Taken> taken = new java.util.concurrent.ConcurrentHashMap<>();
        List<CompletableFuture<Void>> reads = new ArrayList<>();
        for (MarketLayout.Plot plot : moving) {
            CompletableFuture<Void> read = new CompletableFuture<>();
            plots.onPlot(world, plot, () -> {
                try {
                    taken.put(plot, take(world, plot));
                    read.complete(null);
                } catch (RuntimeException failed) {
                    read.completeExceptionally(failed);
                }
            });
            reads.add(read);
        }
        return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new))
                .handle((ignored, failed) -> {
                    if (failed != null) {
                        // Nothing written yet: put back what was taken up, and stop.
                        taken.values().forEach(plot -> plots.onPlot(world, plot.plot(), () ->
                                spawn(world, plot, plot.plot())));
                        throw new IllegalStateException("a plot could not be read; nothing was moved", failed);
                    }
                    return Map.copyOf(taken);
                });
    }

    /** On the plot's thread: its blocks as snapshots; its entities serialised and taken up; players moved off it. */
    private Taken take(World world, MarketLayout.Plot plot) {
        if (!world.isChunkLoaded(plot.minX() >> 4, plot.minZ() >> 4)
                || !world.isChunkLoaded(plot.maxX() >> 4, plot.maxZ() >> 4)) {
            throw new IllegalStateException("plot " + plot.key() + " is not loaded");
        }
        List<BlockState> blocks = new ArrayList<>();
        int top = MarketDesigns.GROUND + market.get().height();
        for (int x = plot.minX(); x <= plot.maxX(); x++) {
            for (int z = plot.minZ(); z <= plot.maxZ(); z++) {
                for (int y = MarketDesigns.GROUND - PlotBuildRule.DEPTH; y <= top; y++) {
                    blocks.add(world.getBlockAt(x, y, z).getState());
                }
            }
        }
        List<TakenEntity> entities = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(plots.box(plot))) {
            if (entity instanceof Player player) {
                player.teleportAsync(plots.front(world, plot));
                continue;
            }
            if (!entity.isPersistent()) {
                continue; // what floats over a shop is drawn again for its new place
            }
            Location at = entity.getLocation();
            BlockFace facing = entity instanceof Hanging hanging ? hanging.getFacing() : null;
            entities.add(new TakenEntity(Bukkit.getUnsafe().serializeEntity(entity), at.getX(), at.getY(), at.getZ(),
                    at.getYaw(), at.getPitch(), facing));
            entity.remove();
        }
        return new Taken(plot, blocks, entities);
    }

    // ------------------------------------------------------------------ 3. writing every plot

    private CompletableFuture<Void> putAll(World world, Map<MarketLayout.Plot, MarketLayout.Plot> moves,
                                           Map<MarketLayout.Plot, Taken> taken) {
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (Map.Entry<MarketLayout.Plot, MarketLayout.Plot> move : moves.entrySet()) {
            Taken from = taken.get(move.getKey());
            MarketLayout.Plot to = move.getValue();
            CompletableFuture<Void> write = new CompletableFuture<>();
            plots.onPlot(world, to, () -> {
                try {
                    put(world, from, to);
                    write.complete(null);
                } catch (RuntimeException failed) {
                    log.error(failed, "Writing plot {} onto {} failed part way; the backup has both as they were",
                            from.plot().key(), to.key());
                    write.completeExceptionally(failed);
                }
            });
            writes.add(write);
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    /** On the target plot's thread: every block of the build, turned when it must be, without physics; then entities. */
    private void put(World world, Taken from, MarketLayout.Plot to) {
        boolean turn = RotationRule.turns(from.plot(), to);
        for (BlockState state : from.blocks()) {
            int[] at = RotationRule.place(from.plot(), to, state.getX(), state.getZ());
            BlockState placed = state.copy(new Location(world, at[0], state.getY(), at[1]));
            if (turn) {
                org.bukkit.block.data.BlockData data = placed.getBlockData();
                data.rotate(StructureRotation.CLOCKWISE_180);
                placed.setBlockData(data);
            }
            placed.update(true, false);
        }
        spawn(world, from, to);
    }

    private void spawn(World world, Taken from, MarketLayout.Plot to) {
        boolean turn = RotationRule.turns(from.plot(), to);
        for (TakenEntity taken : from.entities()) {
            Entity entity = Bukkit.getUnsafe().deserializeEntity(taken.data(), world, true);
            double x = taken.x();
            double z = taken.z();
            float yaw = taken.yaw();
            if (turn) {
                // Through the plot's middle: a point at offset d from minX lands at offset 12 − d from the new minX.
                x = to.minX() + (MarketLayout.PLOT - (taken.x() - from.plot().minX()));
                z = to.minZ() + (MarketLayout.PLOT - (taken.z() - from.plot().minZ()));
                yaw = yaw + 180;
            } else {
                x = to.minX() + (taken.x() - from.plot().minX());
                z = to.minZ() + (taken.z() - from.plot().minZ());
            }
            if (entity instanceof Hanging hanging && taken.facing() != null) {
                hanging.setFacingDirection(turn ? turned(taken.facing()) : taken.facing(), true);
            }
            entity.spawnAt(new Location(world, x, taken.y(), z, yaw, taken.pitch()));
        }
    }

    /** A face turned half round about the vertical: north and south swap, east and west; up and down stay. */
    private static BlockFace turned(BlockFace face) {
        return face == BlockFace.UP || face == BlockFace.DOWN ? face : face.getOppositeFace();
    }

    // ------------------------------------------------------------------ 4. shops, claims, owners

    private void finish(World world, Map<MarketLayout.Plot, MarketLayout.Plot> moves) {
        Map<String, String> keys = new HashMap<>();
        moves.forEach((from, to) -> keys.put(from.key(), to.key()));
        Map<String, MarketPlot> owners = new HashMap<>();
        for (MarketPlot claim : plots.all()) {
            owners.put(claim.plot(), claim);
        }
        for (Shop shop : book.index().all()) {
            if (!shop.spot().world().equals(world.getName())) {
                continue;
            }
            for (Map.Entry<MarketLayout.Plot, MarketLayout.Plot> move : moves.entrySet()) {
                if (move.getKey().contains(shop.spot().x(), shop.spot().z())) {
                    Shop there = movedShop(shop, move.getKey(), move.getValue());
                    book.saveShop(there);
                    moved.accept(there);
                    break;
                }
            }
        }
        long now = clock.getAsLong();
        plots.rotated(keys, now);
        moves.forEach((from, to) -> {
            MarketPlot claim = owners.get(from.key());
            if (claim != null) {
                notices.tell(new Notice(claim.owner(), now, Notice.Kind.PLOT_MOVED, java.util.UUID.nameUUIDFromBytes(
                        to.key().getBytes(java.nio.charset.StandardCharsets.UTF_8)), to.key(),
                        de.raindancer.core.social.economy.Money.ZERO, (int) Math.round(plots.layout()
                        .distanceToSpawn(to)), ""));
            }
        });
    }

    private static Shop movedShop(Shop shop, MarketLayout.Plot from, MarketLayout.Plot to) {
        List<Spot> chests = new ArrayList<>();
        for (Spot chest : shop.chests()) {
            chests.add(moveSpot(chest, from, to));
        }
        Shop there = shop.withSpot(moveSpot(shop.spot(), from, to)).withChests(chests);
        return shop.sign() == null ? there : there.withSign(moveSpot(shop.sign(), from, to));
    }

    private static Spot moveSpot(Spot spot, MarketLayout.Plot from, MarketLayout.Plot to) {
        int[] at = RotationRule.place(from, to, spot.x(), spot.z());
        return new Spot(spot.server(), spot.world(), at[0], spot.y(), at[1]);
    }
}

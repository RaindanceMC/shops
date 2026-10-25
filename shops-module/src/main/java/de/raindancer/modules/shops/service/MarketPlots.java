package de.raindancer.modules.shops.service;

import de.raindancer.core.moderation.audit.Audit;
import de.raindancer.core.moderation.audit.AuditEntry;
import de.raindancer.core.platform.permission.NumberedLimit;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.modules.shops.MarketSettings;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.market.MarketDesigns;
import de.raindancer.modules.shops.market.MarketLayout;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.MarketPlot;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.rules.PlotBuildRule;
import de.raindancer.modules.shops.rules.PlotClaimRule;
import de.raindancer.modules.shops.store.PlotStore;
import de.raindancer.modules.shops.store.ShopWriter;
import de.raindancer.modules.shops.util.PermissionNodes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The market world's plots: who has which, claiming one (the first ones free, the next ones bought), giving one up,
 * and whether somebody may change a block there.
 */
public final class MarketPlots implements IShopsService {

    public static final String SOURCE_PLOT = "shops.plot";

    private final Plugin plugin;
    private final MarketLayout layout;
    private final MarketDesigns designs;
    private final PlotStore store;
    private final ShopWriter writer;
    private final ShopBook book;
    private final Audit audit;
    private final Supplier<String> server;
    private final LongSupplier clock;
    private final PlotClaimRule claims = new PlotClaimRule();
    private final PlotBuildRule building = new PlotBuildRule();
    private final Map<String, MarketPlot> byKey = new ConcurrentHashMap<>();
    private volatile MarketSettings market;

    public MarketPlots(Plugin plugin, MarketLayout layout, MarketDesigns designs, PlotStore store, ShopWriter writer,
                       ShopBook book, Audit audit, Supplier<String> server, MarketSettings market, LongSupplier clock) {
        this.plugin = plugin;
        this.layout = layout;
        this.designs = designs;
        this.store = store;
        this.writer = writer;
        this.book = book;
        this.audit = audit;
        this.server = server;
        this.market = market;
        this.clock = clock;
    }

    @Override
    public void settings(ShopsSettings settings) {
        // The market's own settings come through market(…).
    }

    public void market(MarketSettings fresh) {
        market = fresh;
    }

    public MarketSettings market() {
        return market;
    }

    public MarketLayout layout() {
        return layout;
    }

    /** Every claim on this server, from the database. Blocking; at start, off the server thread. */
    public void load() {
        byKey.clear();
        store.on(server.get()).forEach(plot -> byKey.put(plot.plot(), plot));
    }

    public boolean isMarket(World world) {
        return world != null && market.enabled() && world.getName().equalsIgnoreCase(market.world());
    }

    public Optional<MarketLayout.Plot> plotAt(Location at) {
        if (!isMarket(at.getWorld())) {
            return Optional.empty();
        }
        return Optional.ofNullable(layout.at(at.getBlockX(), at.getBlockZ()).plot());
    }

    public Optional<MarketPlot> claim(MarketLayout.Plot plot) {
        return Optional.ofNullable(byKey.get(plot.key()));
    }

    public List<MarketPlot> claimsOf(UUID owner) {
        return byKey.values().stream().filter(plot -> plot.owner().equals(owner))
                .sorted(java.util.Comparator.comparingLong(MarketPlot::claimedAt)).toList();
    }

    public List<MarketPlot> all() {
        return List.copyOf(byKey.values());
    }

    /** Whether somebody may own this plot's blocks: its owner, or staff of the owner's shop. */
    public boolean worksOn(MarketLayout.Plot plot, UUID who) {
        return claim(plot).map(found -> found.owner().equals(who) || book.storesOf(found.owner()).stream()
                .anyMatch(store -> store.mayStock(who))).orElse(false);
    }

    /** Whether this player may change the block here. */
    public boolean mayBuild(Player player, Block block) {
        if (!isMarket(block.getWorld())) {
            return true;
        }
        MarketLayout.Plot plot = layout.at(block.getX(), block.getZ()).plot();
        boolean admin = player.hasPermission(PermissionNodes.ADMIN);
        return building.mayBuild(plot != null, plot != null && worksOn(plot, player.getUniqueId()), admin,
                block.getY(), MarketDesigns.GROUND, market.height());
    }

    /** What claiming the plot the player stands on would come to — free, the price, or why not. */
    public PlotClaimRule.Verdict wouldClaim(Player player) {
        Optional<MarketLayout.Plot> plot = plotAt(player.getLocation());
        NumberedLimit limit = NumberedLimit.of(player, PermissionNodes.PLOTS_PREFIX, PermissionNodes.PLOTS_UNLIMITED);
        return claims.judge(new PlotClaimRule.Ask(plot.isPresent(), plot.flatMap(this::claim).isPresent(),
                claimsOf(player.getUniqueId()).size(), market.free(), limit.highestOf(market.plotLimit()),
                limit.isUnlimited()));
    }

    public Money price() {
        return Fees.amount(market.plotPrice());
    }

    /** The plot the player stands on becomes theirs; paid for when it is not one of their free ones. */
    public Answer<MarketPlot> claimHere(Player player) {
        if (!market.enabled()) {
            return Answer.refused(Refusal.NO_MARKET);
        }
        PlotClaimRule.Verdict verdict = wouldClaim(player);
        if (verdict.refusal() != null) {
            return Answer.refused(verdict.refusal());
        }
        MarketLayout.Plot plot = plotAt(player.getLocation()).orElseThrow();
        Money paid = Money.ZERO;
        if (verdict == PlotClaimRule.Verdict.PAID && price().isPositive()
                && !player.hasPermission(PermissionNodes.NO_FEES)) {
            EconomyResult charged = Fees.charge(player.getUniqueId(), price(), "Market plot " + plot.key(),
                    SOURCE_PLOT);
            if (!charged.succeeded()) {
                return Answer.refused(Refusal.CANNOT_PAY_FOR_PLOT);
            }
            paid = charged.amount();
        }
        MarketPlot claimed = new MarketPlot(server.get(), plot.key(), player.getUniqueId(), player.getName(),
                clock.getAsLong(), paid);
        byKey.put(plot.key(), claimed);
        writer.write(() -> store.save(claimed));
        audit.record(AuditEntry.of("shops", "plot-claimed").by(player.getUniqueId(), player.getName())
                .saying(plot.key()).with("paid", Long.toString(paid.minor())));
        return Answer.done(claimed);
    }

    /**
     * Gives a plot up: only its owner (or staff), only with no chest shop on it and nothing left in its containers,
     * frames or stands — then the plot gets its empty stall back. On the plot's own thread.
     */
    public Answer<MarketPlot> release(Player by, MarketLayout.Plot plot) {
        Optional<MarketPlot> claimed = claim(plot);
        if (claimed.isEmpty()) {
            return Answer.refused(Refusal.NOT_YOUR_PLOT);
        }
        boolean admin = by.hasPermission(PermissionNodes.ADMIN);
        if (!claimed.get().owner().equals(by.getUniqueId()) && !admin) {
            return Answer.refused(Refusal.NOT_YOUR_PLOT);
        }
        World world = Bukkit.getWorld(market.world());
        if (world == null || !isEmpty(world, plot)) {
            return Answer.refused(Refusal.PLOT_NOT_EMPTY);
        }
        byKey.remove(plot.key());
        writer.write(() -> store.release(server.get(), plot.key()));
        repaint(world, plot);
        audit.record(AuditEntry.of("shops", "plot-released").by(by.getUniqueId(), by.getName())
                .to(claimed.get().owner(), claimed.get().ownerName()).saying(plot.key()));
        return Answer.done(claimed.get());
    }

    /** No chest shop, no stocked container, no framed item, no dressed stand on it. */
    public boolean isEmpty(World world, MarketLayout.Plot plot) {
        boolean shops = book.index().all().stream().anyMatch(shop -> shop.spot().world().equals(world.getName())
                && plot.contains(shop.spot().x(), shop.spot().z()));
        if (shops) {
            return false;
        }
        int top = MarketDesigns.GROUND + market.height();
        for (int x = plot.minX(); x <= plot.maxX(); x++) {
            for (int z = plot.minZ(); z <= plot.maxZ(); z++) {
                for (int y = MarketDesigns.GROUND - PlotBuildRule.DEPTH; y <= top; y++) {
                    if (world.getBlockAt(x, y, z).getState(false) instanceof Container container
                            && Arrays.stream(container.getInventory().getContents())
                            .anyMatch(item -> item != null && !item.getType().isAir())) {
                        return false;
                    }
                }
            }
        }
        for (Entity entity : world.getNearbyEntities(box(plot))) {
            if (entity instanceof ItemFrame frame && !frame.getItem().getType().isAir()) {
                return false;
            }
            if (entity instanceof ArmorStand stand && dressed(stand.getEquipment())) {
                return false;
            }
        }
        return true;
    }

    private static boolean dressed(EntityEquipment equipment) {
        return equipment != null && Arrays.stream(equipment.getArmorContents())
                .anyMatch(item -> item != null && !item.getType().isAir())
                || equipment != null && !isNothing(equipment.getItemInMainHand())
                || equipment != null && !isNothing(equipment.getItemInOffHand());
    }

    private static boolean isNothing(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    /** The plot as the market world first made it: its empty stall. Frames and stands on it go. */
    public void repaint(World world, MarketLayout.Plot plot) {
        int top = MarketDesigns.GROUND + market.height();
        for (Entity entity : world.getNearbyEntities(box(plot))) {
            if (entity instanceof ItemFrame || entity instanceof ArmorStand) {
                entity.remove();
            }
        }
        for (int x = plot.minX(); x <= plot.maxX(); x++) {
            for (int z = plot.minZ(); z <= plot.maxZ(); z++) {
                for (int y = MarketDesigns.GROUND - PlotBuildRule.DEPTH; y <= top; y++) {
                    String state = designs.stateAt(x, y, z);
                    world.getBlockAt(x, y, z).setBlockData(state == null ? Material.AIR.createBlockData()
                            : Bukkit.createBlockData(state), false);
                }
            }
        }
    }

    public BoundingBox box(MarketLayout.Plot plot) {
        return new BoundingBox(plot.minX(), MarketDesigns.GROUND - PlotBuildRule.DEPTH, plot.minZ(), plot.maxX() + 1,
                MarketDesigns.GROUND + market.height() + 1, plot.maxZ() + 1);
    }

    /** Where to stand in front of a plot: on the sidewalk at its middle, looking at it. */
    public Location front(World world, MarketLayout.Plot plot) {
        boolean north = plot.facing() == MarketLayout.Facing.NORTH;
        return new Location(world, plot.centreX(), MarketDesigns.GROUND + 1, plot.frontZ(), north ? 0 : 180, 10);
    }

    /** The claims after a rotation moved them: in memory at once, on disk through the writer. */
    public void rotated(Map<String, String> fromTo, long at) {
        Map<String, MarketPlot> moved = new java.util.HashMap<>();
        for (Map.Entry<String, String> move : fromTo.entrySet()) {
            MarketPlot plot = byKey.remove(move.getKey());
            if (plot != null) {
                moved.put(move.getValue(), plot.movedTo(move.getValue()));
            }
        }
        byKey.putAll(moved);
        writer.write(() -> store.rotate(server.get(), fromTo, at));
    }

    public long lastRotation() {
        return store.lastRotation(server.get());
    }

    public void startClock(long at) {
        writer.write(() -> store.startClock(server.get(), at));
    }

    /** Runs {@code job} on the thread that owns the plot's region. */
    public void onPlot(World world, MarketLayout.Plot plot, Runnable job) {
        Scheduling.region(plugin, new Location(world, plot.centreX(), MarketDesigns.GROUND, plot.centreZ()), job);
    }
}

package de.raindancer.modules.shops.listener;

import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.market.MarketLayout;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.List;
import java.util.UUID;

/**
 * The market world's rules: a player builds only on their own plot, up to its height; the streets, the houses, the
 * square and other people's plots are nobody's to change. Nothing spreads, burns, explodes or flows from one plot onto
 * another. It is always a sunny noon there, and no mob comes into being but one staff summon on purpose. Staff may do
 * anything else too.
 */
public final class MarketGuardListener implements IShopsListener {

    private final ShopsServices services;
    private final de.raindancer.modules.shops.rules.MarketSkyRule sky = new de.raindancer.modules.shops.rules.MarketSkyRule();

    public MarketGuardListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(UUID player) {
        // Remembers nobody.
    }

    private boolean inMarket(Block block) {
        return services.plots().isMarket(block.getWorld());
    }

    private void refuse(Player player, Block block, Cancellable event) {
        if (!services.plots().mayBuild(player, block)) {
            event.setCancelled(true);
            services.messages().send(player, "shops.market.not-yours");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        refuse(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        refuse(event.getPlayer(), event.getBlockPlaced(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        refuse(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        refuse(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHang(HangingPlaceEvent event) {
        if (event.getPlayer() != null) {
            refuse(event.getPlayer(), event.getEntity().getLocation().getBlock(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getPlayer() != null) {
            refuse(event.getPlayer(), event.getBlock().getRelative(event.getBlockFace()), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUnhang(HangingBreakByEntityEvent event) {
        Player player = event.getRemover() instanceof Player remover ? remover : null;
        if (!inMarket(event.getEntity().getLocation().getBlock())) {
            return;
        }
        if (player == null || !services.plots().mayBuild(player, event.getEntity().getLocation().getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Entity hurt = event.getEntity();
        if (hurt instanceof Player || !(event.getDamager() instanceof Player player)
                || !inMarket(hurt.getLocation().getBlock())) {
            return;
        }
        refuse(player, hurt.getLocation().getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStand(PlayerArmorStandManipulateEvent event) {
        refuse(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (clicked instanceof org.bukkit.entity.ItemFrame && inMarket(clicked.getLocation().getBlock())
                && !services.plots().mayBuild(event.getPlayer(), clicked.getLocation().getBlock())
                && services.locator().shopAt(((org.bukkit.entity.ItemFrame) clicked).getLocation().getBlock()
                .getRelative(((org.bukkit.entity.ItemFrame) clicked).getAttachedFace())).isEmpty()) {
            event.setCancelled(true);
        }
    }

    /**
     * Somebody else's containers stay shut, and nobody uses items on blocks that are not theirs (bone meal, flint and
     * steel, a hoe). Chest shops answer their own clicks first; doors, buttons and the like open for anybody.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !inMarket(block) || services.plots().mayBuild(event.getPlayer(), block)) {
            return;
        }
        if (services.locator().shopAt(block).isPresent() || services.locator().shopOfSign(block).isPresent()) {
            return;
        }
        boolean container = block.getState(false) instanceof Container;
        boolean usingAnItem = event.getItem() != null && !event.getItem().getType().isAir()
                && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && !block.getType().isInteractable();
        if (container || usingAnItem || event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (inMarket(event.getBlock()) && !samePlot(event.getBlock(), event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPush(BlockPistonExtendEvent event) {
        if (inMarket(event.getBlock()) && movesOff(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPull(BlockPistonRetractEvent event) {
        if (inMarket(event.getBlock()) && movesOff(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean movesOff(Block piston, List<Block> moved, org.bukkit.block.BlockFace direction) {
        for (Block block : moved) {
            if (!samePlot(piston, block) || !samePlot(piston, block.getRelative(direction))) {
                return true;
            }
        }
        return false;
    }

    private boolean samePlot(Block a, Block b) {
        MarketLayout.Plot one = services.plots().layout().at(a.getX(), a.getZ()).plot();
        MarketLayout.Plot two = services.plots().layout().at(b.getX(), b.getZ()).plot();
        return one != null && one.equals(two);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        if (!services.plots().isMarket(event.getWorld())) {
            return;
        }
        Block origin = event.getLocation().getBlock();
        event.getBlocks().removeIf(state -> !samePlot(origin, state.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (inMarket(event.getBlock()) && !samePlot(event.getSource(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (inMarket(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (inMarket(event.getBlock()) && event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (inMarket(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (services.plots().isMarket(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (inMarket(event.getBlock())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent event) {
        if (inMarket(event.getBlock()) && !(event.getEntity() instanceof org.bukkit.entity.FallingBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (services.plots().isMarket(event.getLocation().getWorld())
                && !sky.maySpawn(event.getEntity() instanceof org.bukkit.entity.Mob, event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    /** Never rain, never thunder — by the weather or by a command. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeather(org.bukkit.event.weather.WeatherChangeEvent event) {
        if (services.plots().isMarket(event.getWorld()) && !sky.mayChangeWeather(event.toWeatherState())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThunder(org.bukkit.event.weather.ThunderChangeEvent event) {
        if (services.plots().isMarket(event.getWorld()) && !sky.mayChangeWeather(event.toThunderState())) {
            event.setCancelled(true);
        }
    }
}

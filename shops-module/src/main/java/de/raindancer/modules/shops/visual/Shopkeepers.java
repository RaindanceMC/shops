package de.raindancer.modules.shops.visual;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.world.npc.Attendants;
import de.raindancer.core.world.protection.Land;
import de.raindancer.core.world.protection.LandAction;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Statue;
import de.raindancer.modules.shops.model.Store;
import de.raindancer.modules.shops.rules.PlacementRule;
import de.raindancer.modules.shops.service.IShopsService;
import de.raindancer.modules.shops.service.ShopBook;
import de.raindancer.modules.shops.service.ShopLocator;
import de.raindancer.modules.shops.util.PermissionNodes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Shopkeepers: one per shop, with its owner's own skin, the shop's name over their head and its motto under it.
 * Clicking one opens the shop. A shopkeeper is Core's attendant — player-shaped, still, unhurtable, saved with its
 * chunk — and carries its shop's id; one that is not its shop's shopkeeper any more (the shop closed, the shopkeeper
 * moved, or a statue from before 0.2.0) takes itself away the next time it is loaded.
 */
public final class Shopkeepers implements IShopsService {

    public static final String MARK = "store";

    private final Plugin plugin;
    private final ShopBook book;
    private final ShopLocator locator;
    private final Land land;
    private final Messages messages;
    private final LongSupplier clock;
    private final NamespacedKey mark;
    private final PlacementRule placement = new PlacementRule();
    private volatile ShopsSettings settings;

    public Shopkeepers(Plugin plugin, ShopBook book, ShopLocator locator, Land land, Messages messages,
                       ShopsSettings settings, LongSupplier clock) {
        this.plugin = plugin;
        this.book = book;
        this.locator = locator;
        this.land = land;
        this.messages = messages;
        this.settings = settings;
        this.clock = clock;
        this.mark = new NamespacedKey(plugin, MARK);
    }

    @Override
    public void settings(ShopsSettings fresh) {
        settings = fresh;
    }

    /** Whether a shopkeeper may stand where the player stands. Places nothing. */
    public Optional<Refusal> mayStandHere(Player by) {
        if (!placement.worldAllowed(by.getWorld().getName(), placement.names(settings.closedWorlds()))) {
            return Optional.of(Refusal.WORLD_NOT_ALLOWED);
        }
        if (!by.hasPermission(PermissionNodes.ADMIN) && !land.can(by, by.getLocation(), LandAction.BUILD)) {
            return Optional.of(Refusal.NOT_YOUR_LAND);
        }
        return Optional.empty();
    }

    /**
     * The shop's shopkeeper, standing where the player stands and facing their way. The one it had before, if any,
     * is taken away. On the player's own thread.
     */
    public Statue place(Player by, Store store) {
        Optional<Statue> before = book.keeperOf(store.id());
        Mannequin placed = Attendants.place(by, name(store), motto(store), look(store),
                entity -> entity.getPersistentDataContainer().set(mark, PersistentDataType.STRING,
                        store.id().toString()));
        Location at = placed.getLocation();
        Statue keeper = new Statue(placed.getUniqueId(), store.id(), locator.spotOf(at.getBlock()), at.getYaw(),
                clock.getAsLong());
        book.placed(keeper);
        before.ifPresent(this::takeAway);
        return keeper;
    }

    /** The shop a shopkeeper stands for. */
    public Optional<UUID> storeOf(Entity entity) {
        if (!(entity instanceof Mannequin)) {
            return Optional.empty();
        }
        String written = entity.getPersistentDataContainer().get(mark, PersistentDataType.STRING);
        if (written == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(written));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
    }

    /** Whether this entity is the shopkeeper its shop has now. */
    public boolean isCurrent(Entity entity) {
        return storeOf(entity).flatMap(book::keeperOf).map(keeper -> keeper.entity().equals(entity.getUniqueId()))
                .orElse(false);
    }

    /** A shopkeeper just loaded with its chunk: one that is not its shop's goes, the shop's is dressed again. */
    public void loaded(Entity entity) {
        storeOf(entity).ifPresent(id -> {
            Optional<Store> store = book.store(id);
            if (store.isEmpty() || !isCurrent(entity)) {
                entity.remove();
                book.unplaced(entity.getUniqueId());
                return;
            }
            dress((Mannequin) entity, store.get());
        });
    }

    /**
     * Every shopkeeper already loaded when the module starts — the spawn's, a forced chunk's — gets what a freshly
     * loaded one gets: their chunks loaded before anybody listened. On each chunk's own thread.
     */
    public void sweepLoaded() {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                Location at = new Location(world, chunk.getX() << 4, 64, chunk.getZ() << 4);
                Scheduling.region(plugin, at, () -> {
                    if (chunk.isLoaded()) {
                        for (Entity entity : chunk.getEntities()) {
                            loaded(entity);
                        }
                    }
                });
            }
        }
    }

    /** The shop's shopkeeper, named and dressed again, if it is loaded here. */
    public void refresh(Store store) {
        book.keeperOf(store.id()).ifPresent(keeper -> locator.location(keeper.spot()).ifPresent(at ->
                Scheduling.region(plugin, at, () -> {
                    Entity entity = plugin.getServer().getEntity(keeper.entity());
                    if (entity instanceof Mannequin mannequin) {
                        dress(mannequin, store);
                    }
                })));
    }

    /** A closing shop's shopkeeper; one in an unloaded chunk goes when it is next loaded. */
    public void remove(Store store) {
        book.keeperOf(store.id()).ifPresent(this::takeAway);
    }

    private void takeAway(Statue keeper) {
        book.unplaced(keeper.entity());
        locator.location(keeper.spot()).ifPresent(at -> Scheduling.region(plugin, at, () -> {
            Entity entity = plugin.getServer().getEntity(keeper.entity());
            if (entity != null) {
                entity.remove();
            }
        }));
    }

    private void dress(Mannequin keeper, Store store) {
        keeper.customName(name(store));
        keeper.setDescription(motto(store));
        keeper.setProfile(look(store));
    }

    /** The shop's name over its head, painted in the shop's style when it has one — Core's Gradients. */
    private Component name(Store store) {
        return messages.get("shops.keeper.name", "store", painted(store.name(), store.nameStyle()),
                "owner", store.ownerName());
    }

    private Component motto(Store store) {
        return store.motto().isEmpty() ? messages.get("shops.keeper.no-motto")
                : messages.get("shops.keeper.motto", "motto", painted(store.motto(), store.mottoStyle()));
    }

    /** Text as it is, or painted: a component is put in as it is, never read as markup. */
    private static Object painted(String text, de.raindancer.core.ui.text.NameStyle style) {
        return style.isEmpty() ? text : de.raindancer.core.ui.text.Gradients.styled(text, style);
    }

    /** The owner's own skin, found by their id — a renamed player keeps their shopkeeper's face. */
    private static ResolvableProfile look(Store store) {
        ResolvableProfile.Builder profile = ResolvableProfile.resolvableProfile().uuid(store.owner());
        if (!store.ownerName().isBlank()) {
            profile.name(store.ownerName());
        }
        return profile.build();
    }
}

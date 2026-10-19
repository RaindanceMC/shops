package de.raindancer.modules.shops.visual;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.world.npc.Attendants;
import de.raindancer.core.world.protection.Land;
import de.raindancer.core.world.protection.LandAction;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.model.Statue;
import de.raindancer.modules.shops.model.Store;
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
 * Statues of a store's owner: their own skin, the store's name over their head, its motto under it. Clicking one
 * opens the store. The statue is Core's attendant — player-shaped, still, unhurtable, saved with its chunk — and
 * carries the store's id, so a statue whose store has closed takes itself away the next time it is loaded.
 */
public final class StoreStatues implements IShopsService {

    public static final String MARK = "store";
    public static final String SOURCE = "shops.statue";

    private final Plugin plugin;
    private final ShopBook book;
    private final ShopLocator locator;
    private final Land land;
    private final Messages messages;
    private final LongSupplier clock;
    private final NamespacedKey mark;
    private volatile ShopsSettings settings;

    public StoreStatues(Plugin plugin, ShopBook book, ShopLocator locator, Land land, Messages messages,
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

    public int limit() {
        return settings.statueLimit();
    }

    /** Whether one more statue of this store may stand where the player is. Charges and places nothing. */
    public Optional<Refusal> mayPlace(Player by, Store store) {
        if (!store.isOwner(by.getUniqueId()) && !by.hasPermission(PermissionNodes.ADMIN)) {
            return Optional.of(Refusal.NOT_ALLOWED);
        }
        if (settings.statueLimit() <= 0) {
            return Optional.of(Refusal.NO_STATUES);
        }
        if (book.statuesOf(store.id()).size() >= settings.statueLimit()) {
            return Optional.of(Refusal.TOO_MANY_STATUES);
        }
        if (!by.hasPermission(PermissionNodes.ADMIN) && !land.can(by, by.getLocation(), LandAction.BUILD)) {
            return Optional.of(Refusal.NOT_YOUR_LAND);
        }
        return Optional.empty();
    }

    /** A statue where the player stands, facing the way they face. On the player's own thread. */
    public Answer<Statue> place(Player by, Store store) {
        Optional<Refusal> refused = mayPlace(by, store);
        if (refused.isPresent()) {
            return Answer.refused(refused.get());
        }
        Money fee = by.hasPermission(PermissionNodes.NO_FEES) ? Money.ZERO : Fees.amount(settings.statuePrice());
        EconomyResult paid = Fees.charge(by.getUniqueId(), fee, "A statue for " + store.name(), SOURCE);
        if (!paid.succeeded()) {
            return Answer.refused(Refusal.YOU_CANNOT_AFFORD);
        }
        Mannequin placed = Attendants.place(by, name(store), motto(store), look(store),
                entity -> entity.getPersistentDataContainer().set(mark, PersistentDataType.STRING,
                        store.id().toString()));
        Location at = placed.getLocation();
        Statue statue = new Statue(placed.getUniqueId(), store.id(),
                locator.spotOf(at.getBlock()), at.getYaw(), clock.getAsLong());
        book.placed(statue);
        return Answer.done(statue);
    }

    /** The store a statue stands for. */
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

    /** Takes a statue away. On the entity's own thread. */
    public Answer<UUID> remove(Player by, Entity statue) {
        Optional<Store> store = storeOf(statue).flatMap(book::store);
        if (store.isPresent() && !store.get().isOwner(by.getUniqueId())
                && !by.hasPermission(PermissionNodes.ADMIN)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        statue.remove();
        book.unplaced(statue.getUniqueId());
        return Answer.done(statue.getUniqueId());
    }

    /**
     * A statue just loaded with its chunk: one of a closed store goes, one of a renamed store is renamed. On the
     * entity's own thread.
     */
    public void loaded(Entity entity) {
        storeOf(entity).ifPresent(id -> {
            Optional<Store> store = book.store(id);
            if (store.isEmpty()) {
                entity.remove();
                book.unplaced(entity.getUniqueId());
                return;
            }
            dress((Mannequin) entity, store.get());
        });
    }

    /** Every standing statue of this store that is loaded here, named and dressed again. */
    public void refresh(Store store) {
        for (Statue statue : book.statuesOf(store.id())) {
            locator.location(statue.spot()).ifPresent(at -> Scheduling.region(plugin, at, () -> {
                Entity entity = plugin.getServer().getEntity(statue.entity());
                if (entity instanceof Mannequin mannequin) {
                    dress(mannequin, store);
                }
            }));
        }
    }

    /** All of a closing store's statues on this server; those in unloaded chunks go when they are next loaded. */
    public void removeAll(Store store) {
        for (Statue statue : book.statuesOf(store.id())) {
            locator.location(statue.spot()).ifPresent(at -> Scheduling.region(plugin, at, () -> {
                Entity entity = plugin.getServer().getEntity(statue.entity());
                if (entity != null) {
                    entity.remove();
                }
            }));
        }
    }

    public Optional<Spot> firstSpot(Store store) {
        return book.statuesOf(store.id()).stream().findFirst().map(Statue::spot);
    }

    private void dress(Mannequin statue, Store store) {
        statue.customName(name(store));
        statue.setDescription(motto(store));
        statue.setProfile(look(store));
    }

    private Component name(Store store) {
        return messages.get("shops.statue.name", "store", store.name(), "owner", store.ownerName());
    }

    private Component motto(Store store) {
        return store.motto().isEmpty() ? messages.get("shops.statue.no-motto")
                : messages.get("shops.statue.motto", "motto", store.motto());
    }

    /** The owner's own skin, found by their id — a renamed player keeps their statue's face. */
    private static ResolvableProfile look(Store store) {
        ResolvableProfile.Builder profile = ResolvableProfile.resolvableProfile().uuid(store.owner());
        if (!store.ownerName().isBlank()) {
            profile.name(store.ownerName());
        }
        return profile.build();
    }
}

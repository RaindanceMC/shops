package de.raindancer.modules.shops.service;

import de.raindancer.core.platform.network.Arrivals;
import de.raindancer.core.platform.network.NetworkTravel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.world.protection.Land;
import de.raindancer.core.world.teleport.Travel;
import de.raindancer.core.world.teleport.TravelReason;
import de.raindancer.core.world.teleport.TravelWatcher;
import de.raindancer.core.world.teleport.Trip;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.util.PermissionNodes;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Going to a shop or a store's statue from the browser — through Core's travel, warm-up and safe landing included,
 * and across the network to another server's shop. A fee is taken first and given back if the trip does not happen.
 */
public final class ShopTravel implements IShopsService {

    public static final String SOURCE = "shops.travel";

    private final Plugin plugin;
    private final Travel travel;
    private final Land land;
    private final Messages messages;
    private final ShopLocator locator;
    private final Optional<NetworkTravel> network;
    private volatile ShopsSettings settings;

    public ShopTravel(Plugin plugin, Travel travel, Land land, Messages messages, ShopLocator locator,
                      Optional<NetworkTravel> network, ShopsSettings settings) {
        this.plugin = plugin;
        this.travel = travel;
        this.land = land;
        this.messages = messages;
        this.locator = locator;
        this.network = network;
        this.settings = settings;
    }

    @Override
    public void settings(ShopsSettings fresh) {
        settings = fresh;
    }

    public boolean enabled() {
        return settings.travelToShops();
    }

    /** @param name what the trip is to, for the countdown line */
    public Optional<Refusal> go(Player traveller, Spot spot, String name) {
        if (!settings.travelToShops()) {
            return Optional.of(Refusal.NO_TRAVEL);
        }
        if (travel.isTravelling(traveller.getUniqueId())) {
            return Optional.of(Refusal.BUSY);
        }
        Optional<Location> here = locator.location(spot);
        if (here.isEmpty() && (network.isEmpty() || spot.server().isBlank())) {
            return Optional.of(Refusal.NOT_HERE);
        }
        Location front = here.map(block -> block.clone().add(0.5, 1, 0.5)).orElse(null);
        if (front != null && !land.mayTeleportInto(traveller, front)) {
            return Optional.of(Refusal.NOT_YOUR_LAND);
        }
        Money fee = traveller.hasPermission(PermissionNodes.NO_FEES) ? Money.ZERO
                : Fees.amount(settings.travelPrice());
        EconomyResult paid = Fees.charge(traveller.getUniqueId(), fee, "Trip to " + name, SOURCE);
        if (!paid.succeeded()) {
            return Optional.of(Refusal.YOU_CANNOT_AFFORD);
        }
        Money taken = paid.amount();
        if (front == null) {
            Arrivals.Destination there = new Arrivals.Destination(spot.world(), spot.x() + 0.5, spot.y() + 1,
                    spot.z() + 0.5, 0, 0);
            network.orElseThrow().travel(traveller, spot.server(), Optional.of(there)).thenAccept(moved -> {
                if (!moved) {
                    Fees.refund(traveller.getUniqueId(), taken, "Trip to " + name + " did not happen", SOURCE);
                    Scheduling.entity(plugin, traveller, () -> messages.send(traveller, "shops.travel.failed",
                            "shop", name));
                }
            });
            return Optional.empty();
        }
        Trip trip = Trip.to(name).after(settings.warmup()).searching(4);
        travel.go(traveller, front, trip, new TravelWatcher() {
            @Override
            public void counting(Player who, int secondsLeft, Trip going) {
                messages.send(who, "shops.travel.counting", "shop", name, "seconds", Integer.toString(secondsLeft));
            }

            @Override
            public void arrived(Player who, Location where, Trip going) {
                messages.send(who, "shops.travel.arrived", "shop", name);
            }

            @Override
            public void cancelled(Player who, TravelReason why, Trip going) {
                Fees.refund(who.getUniqueId(), taken, "Trip to " + name + " cancelled", SOURCE);
                messages.send(who, "shops.travel.cancelled", "shop", name);
            }

            @Override
            public void refused(Player who, TravelReason why, Trip going) {
                Fees.refund(who.getUniqueId(), taken, "Trip to " + name + " refused", SOURCE);
                messages.send(who, "shops.travel.failed", "shop", name);
            }
        });
        return Optional.empty();
    }
}

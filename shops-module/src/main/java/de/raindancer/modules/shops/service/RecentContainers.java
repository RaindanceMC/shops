package de.raindancer.modules.shops.service;

import de.raindancer.modules.shops.model.Spot;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The container each player clicked last, for a minute: {@code /bazaar create} and the other "this shop" commands
 * take it when the player is not looking straight at one — aiming at a block from a phone or a controller is
 * unreliable, and "click the chest, then type" is what shop plugins have taught everybody.
 */
public final class RecentContainers {

    private static final long FOR_MILLIS = 60_000;

    private record Click(Spot spot, long at) {
    }

    private final Map<UUID, Click> clicks = new ConcurrentHashMap<>();

    public void clicked(UUID player, Spot spot, long now) {
        clicks.put(player, new Click(spot, now));
    }

    public Optional<Spot> of(UUID player, long now) {
        Click click = clicks.get(player);
        if (click == null || now - click.at() > FOR_MILLIS) {
            return Optional.empty();
        }
        return Optional.of(click.spot());
    }

    public void forget(UUID player) {
        clicks.remove(player);
    }

    public int size() {
        return clicks.size();
    }
}

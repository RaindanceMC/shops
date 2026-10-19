package de.raindancer.modules.shops.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is linking chests to which offer right now: while it lasts, a right click on a chest links it to the offer or
 * unlinks it. It ends with {@code /bazaar done}, on leaving, or after two minutes without a click.
 */
public final class Linking {

    public static final long IDLE_MILLIS = 120_000;

    private record Mode(UUID offer, long lastAt) {
    }

    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();

    public void start(UUID player, UUID offer, long now) {
        modes.put(player, new Mode(offer, now));
    }

    /** A click while linking: the two minutes start again. */
    public void touched(UUID player, long now) {
        modes.computeIfPresent(player, (who, mode) -> new Mode(mode.offer(), now));
    }

    public Optional<UUID> offerOf(UUID player, long now) {
        Mode mode = modes.get(player);
        if (mode == null) {
            return Optional.empty();
        }
        if (now - mode.lastAt() > IDLE_MILLIS) {
            modes.remove(player, mode);
            return Optional.empty();
        }
        return Optional.of(mode.offer());
    }

    /** @return the offer that was being linked, if any */
    public Optional<UUID> stop(UUID player) {
        return Optional.ofNullable(modes.remove(player)).map(Mode::offer);
    }
}

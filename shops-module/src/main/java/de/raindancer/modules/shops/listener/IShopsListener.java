package de.raindancer.modules.shops.listener;

import org.bukkit.event.Listener;

import java.util.UUID;

/** A listener of this module. One that remembers a player forgets them when told — see MODULE-LAYOUT.md. */
public interface IShopsListener extends Listener {

    default void forget(UUID player) {
    }
}

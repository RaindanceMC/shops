package de.raindancer.modules.shops.model;

import java.util.UUID;

/**
 * A player's store: a name over any number of their shops, and what a statue of them opens. A player may run several
 * — a smithy and a farm stand — and every shop belongs to exactly one.
 *
 * @param motto one line under the name, on the statue and the store's page
 */
public record Store(UUID id, UUID owner, String ownerName, String name, String motto, long createdAt) {

    public Store {
        ownerName = ownerName == null ? "" : ownerName;
        name = name == null || name.isBlank() ? (ownerName.isEmpty() ? "Store" : ownerName + "'s Store")
                : name.strip();
        motto = motto == null ? "" : motto.strip();
    }

    public boolean isOwner(UUID who) {
        return owner.equals(who);
    }

    public Store withName(String changed) {
        return new Store(id, owner, ownerName, changed, motto, createdAt);
    }

    public Store withMotto(String changed) {
        return new Store(id, owner, ownerName, name, changed, createdAt);
    }

    public Store withOwnerName(String changed) {
        return new Store(id, owner, changed, name, motto, createdAt);
    }
}

package de.raindancer.modules.shops.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A player's shop, as players say it: a shopkeeper with the owner's skin, a name, and the offers ({@link Shop}) it
 * sells. A player may run several — a smithy and a farm stand — each with its own staff.
 *
 * @param motto one line under the name, on the shopkeeper and the shop's page
 * @param staff who else works here, and as what — clerks stock the chests, managers also price the offers
 */
public record Store(UUID id, UUID owner, String ownerName, String name, String motto, long createdAt,
                    Map<UUID, Role> staff) {

    public Store {
        ownerName = ownerName == null ? "" : ownerName;
        name = name == null || name.isBlank() ? (ownerName.isEmpty() ? "Shop" : ownerName + "'s Shop")
                : name.strip();
        motto = motto == null ? "" : motto.strip();
        staff = staff == null ? Map.of() : Map.copyOf(staff);
    }

    public boolean isOwner(UUID who) {
        return owner.equals(who);
    }

    public Optional<Role> roleOf(UUID who) {
        return Optional.ofNullable(staff.get(who));
    }

    /** Owner, clerk or manager: somebody who may open the stock chests. */
    public boolean mayStock(UUID who) {
        return isOwner(who) || staff.containsKey(who);
    }

    /** Owner or manager: somebody who may change what the offers ask and link their chests. */
    public boolean mayManage(UUID who) {
        return isOwner(who) || staff.get(who) == Role.MANAGER;
    }

    public Store withName(String changed) {
        return new Store(id, owner, ownerName, changed, motto, createdAt, staff);
    }

    public Store withMotto(String changed) {
        return new Store(id, owner, ownerName, name, changed, createdAt, staff);
    }

    public Store withOwnerName(String changed) {
        return new Store(id, owner, changed, name, motto, createdAt, staff);
    }

    public Store withStaff(UUID who, Role role) {
        Map<UUID, Role> changed = new LinkedHashMap<>(staff);
        if (role == null) {
            changed.remove(who);
        } else if (!isOwner(who)) {
            changed.put(who, role);
        }
        return new Store(id, owner, ownerName, name, motto, createdAt, changed);
    }
}

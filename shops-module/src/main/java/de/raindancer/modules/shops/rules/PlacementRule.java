package de.raindancer.modules.shops.rules;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Which blocks may be shops, and in which worlds. */
public final class PlacementRule implements IShopsRule {

    /**
     * Never a shop, whatever a server writes: a box is carried off with its stock in it, and an ender chest is every
     * player's own inventory, so the "stock" would be the customer's.
     */
    private static final Set<String> NEVER = Set.of("ENDER_CHEST");

    /** A setting's list as a set of upper-case names, blanks left out. */
    public Set<String> names(List<String> listed) {
        Set<String> names = new LinkedHashSet<>();
        if (listed == null) {
            return names;
        }
        for (String part : listed) {
            String name = part == null ? "" : part.strip().toUpperCase(Locale.ROOT);
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    public boolean mayBeShop(String material, Set<String> allowed) {
        if (material == null) {
            return false;
        }
        String name = material.toUpperCase(Locale.ROOT);
        return allowed.contains(name) && !NEVER.contains(name) && !name.endsWith("SHULKER_BOX");
    }

    /** @param closed worlds where no shop may be opened; empty is everywhere */
    public boolean worldAllowed(String world, Set<String> closed) {
        return world != null && !closed.contains(world.toUpperCase(Locale.ROOT));
    }
}

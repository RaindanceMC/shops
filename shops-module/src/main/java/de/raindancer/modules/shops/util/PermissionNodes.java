package de.raindancer.modules.shops.util;

import org.bukkit.Server;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.List;

/** The permissions player shops know. */
public final class PermissionNodes {

    /** Trading at shops and browsing them. */
    public static final String USE = "rainsshops.use";

    /** Opening shops of one's own. */
    public static final String CREATE = "rainsshops.create";

    /** {@code rainsshops.limit.<n>}: how many shops; read through Core's NumberedLimit. */
    public static final String LIMIT_PREFIX = "rainsshops.limit.";

    public static final String UNLIMITED = "rainsshops.limit.unlimited";

    /** Opening a shop and travelling to one without paying for it. */
    public static final String NO_FEES = "rainsshops.bypass.fees";

    /** Every shop: inspecting, closing, opening, removing, and opening their containers. */
    public static final String ADMIN = "rainsshops.admin";

    private PermissionNodes() {
    }

    public static List<Permission> declared() {
        return List.of(
                new Permission(USE, "Trade at players' shops and browse them", PermissionDefault.TRUE),
                new Permission(CREATE, "Open shops of one's own", PermissionDefault.TRUE),
                // Declared FALSE so an operator is not quietly unlimited: an undeclared node is true for ops.
                new Permission(UNLIMITED, "Open as many shops as one likes", PermissionDefault.FALSE),
                new Permission(NO_FEES, "Open shops and travel to them for free", PermissionDefault.OP),
                new Permission(ADMIN, "Inspect, close, open and remove anybody's shop", PermissionDefault.OP));
    }

    /** @return how many were added, for the line in the log */
    public static int register(Server server) {
        if (server == null) {
            return 0;
        }
        int added = 0;
        for (Permission permission : declared()) {
            if (server.getPluginManager().getPermission(permission.getName()) != null) {
                continue;
            }
            try {
                server.getPluginManager().addPermission(permission);
                added++;
            } catch (IllegalArgumentException alreadyThere) {
                // Another copy of the module added it between the look and the add.
            }
        }
        return added;
    }
}

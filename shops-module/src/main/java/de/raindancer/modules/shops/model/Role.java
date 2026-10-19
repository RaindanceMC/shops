package de.raindancer.modules.shops.model;

/**
 * What somebody the owner trusts may do in a shop. The owner may do everything, which is not a role: it cannot be
 * given away by accident through the staff page.
 */
public enum Role {
    /** Fills and empties the container. */
    CLERK("Clerk", "fills and empties the stock"),
    /** A clerk who also sets the prices, the bundle and whether the shop is open. */
    MANAGER("Manager", "stock, prices and opening hours");

    private final String title;
    private final String does;

    Role(String title, String does) {
        this.title = title;
        this.does = does;
    }

    public String title() {
        return title;
    }

    public String does() {
        return does;
    }

    public Role next() {
        return this == CLERK ? MANAGER : CLERK;
    }

    public static Role parse(String name) {
        try {
            return name == null ? CLERK : valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return CLERK;
        }
    }
}

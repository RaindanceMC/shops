package de.raindancer.modules.shops.model;

/** Whether a shop trades right now, and if not, whose doing that is. */
public enum Status {
    OPEN,
    /** Its owner or a manager closed it for now. */
    PAUSED,
    /** The rent is not paid; it opens again the moment it is. */
    UNPAID,
    /** Staff closed it; only staff open it again. */
    CLOSED;

    public boolean trades() {
        return this == OPEN;
    }

    public static Status parse(String name) {
        try {
            return name == null ? OPEN : valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return OPEN;
        }
    }
}

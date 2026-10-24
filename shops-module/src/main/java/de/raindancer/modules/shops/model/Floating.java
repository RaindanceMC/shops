package de.raindancer.modules.shops.model;

/**
 * What floats over a chest shop: the item it trades, its price, its name — each its owner's to switch off.
 */
public record Floating(boolean item, boolean price, boolean name) {

    public enum Line { ITEM, PRICE, NAME }

    public static final Floating ALL = new Floating(true, true, true);
    public static final Floating NONE = new Floating(false, false, false);

    public boolean shows(Line line) {
        return switch (line) {
            case ITEM -> item;
            case PRICE -> price;
            case NAME -> name;
        };
    }

    public Floating toggled(Line line) {
        return switch (line) {
            case ITEM -> new Floating(!item, price, name);
            case PRICE -> new Floating(item, !price, name);
            case NAME -> new Floating(item, price, !name);
        };
    }

    public boolean anything() {
        return item || price || name;
    }

    /** Whether there is a line of words to float, not only the item. */
    public boolean hasText() {
        return price || name;
    }

    /** One number for the database: a bit per line. */
    public int encode() {
        return (item ? 1 : 0) | (price ? 2 : 0) | (name ? 4 : 0);
    }

    public static Floating parse(int bits) {
        if (bits < 0 || bits > 7) {
            return ALL;
        }
        return new Floating((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0);
    }
}

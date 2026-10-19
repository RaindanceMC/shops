package de.raindancer.modules.shops.model;

import java.util.UUID;

/** What a customer thought of a shop: one to five stars and a line, one per customer and shop. */
public record Review(UUID shop, UUID author, String authorName, int stars, String text, long at) {

    public static final int FEWEST_STARS = 1;
    public static final int MOST_STARS = 5;

    public Review {
        authorName = authorName == null ? "" : authorName;
        stars = Math.max(FEWEST_STARS, Math.min(MOST_STARS, stars));
        text = text == null ? "" : text.strip();
    }

    /** {@code ★★★☆☆}. */
    public static String stars(double average) {
        int whole = (int) Math.round(Math.max(0, Math.min(MOST_STARS, average)));
        return "★".repeat(whole) + "☆".repeat(MOST_STARS - whole);
    }
}

package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.modules.shops.model.Rating;
import de.raindancer.modules.shops.model.Review;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What customers said about shops: one review each per shop, the latest standing. */
public final class ReviewStore {

    private final Database database;

    public ReviewStore(Database database) {
        this.database = database;
    }

    public boolean save(Review review) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO review (shop, author, author_name, stars, text, at) VALUES (?,?,?,?,?,?) "
                            + "ON CONFLICT(shop, author) DO UPDATE SET author_name = excluded.author_name, "
                            + "stars = excluded.stars, text = excluded.text, at = excluded.at")) {
                row.setString(1, review.shop().toString());
                row.setString(2, review.author().toString());
                row.setString(3, review.authorName());
                row.setInt(4, review.stars());
                row.setString(5, review.text());
                row.setLong(6, review.at());
                row.executeUpdate();
            }
        });
    }

    public boolean remove(UUID shop, UUID author) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "DELETE FROM review WHERE shop = ? AND author = ?")) {
                row.setString(1, shop.toString());
                row.setString(2, author.toString());
                row.executeUpdate();
            }
        });
    }

    /** Newest first. */
    public List<Review> of(UUID shop, int most) {
        return database.read(connection -> {
            List<Review> reviews = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT author, author_name, stars, text, at FROM review WHERE shop = ? "
                            + "ORDER BY at DESC LIMIT ?")) {
                query.setString(1, shop.toString());
                query.setInt(2, Math.max(1, most));
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        reviews.add(new Review(shop, UUID.fromString(rows.getString(1)), rows.getString(2),
                                rows.getInt(3), rows.getString(4), rows.getLong(5)));
                    }
                }
            }
            return reviews;
        }).orElse(List.of());
    }

    public Rating rating(UUID shop) {
        return ratings().getOrDefault(shop, Rating.NONE);
    }

    /** Every shop's rating at once, for the browser. */
    public Map<UUID, Rating> ratings() {
        return database.read(connection -> {
            Map<UUID, Rating> ratings = new HashMap<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT shop, AVG(stars), COUNT(*) FROM review GROUP BY shop");
                 ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    ratings.put(UUID.fromString(rows.getString(1)), new Rating(rows.getDouble(2), rows.getInt(3)));
                }
            }
            return ratings;
        }).orElse(Map.of());
    }
}

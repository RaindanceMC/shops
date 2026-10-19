package de.raindancer.modules.shops.model;

/** The reviews of a shop, summed up. */
public record Rating(double average, int count) {

    public static final Rating NONE = new Rating(0, 0);

    public Rating {
        average = count <= 0 ? 0 : Math.max(0, Math.min(Review.MOST_STARS, average));
        count = Math.max(0, count);
    }

    public boolean exists() {
        return count > 0;
    }

    /**
     * The average pulled towards three stars by how few reviews there are, so one five-star review from the owner's
     * friend does not rank above forty honest fours. Bayesian, with three imaginary three-star reviews.
     */
    public double ranked() {
        double prior = 3.0;
        int weight = 3;
        return (average * count + prior * weight) / (count + weight);
    }
}

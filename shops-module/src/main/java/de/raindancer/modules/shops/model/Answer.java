package de.raindancer.modules.shops.model;

import java.util.Optional;

/** Something done, or the reason it was not. */
public record Answer<T>(T value, Refusal refusal) {

    public static <T> Answer<T> done(T value) {
        return new Answer<>(value, null);
    }

    public static <T> Answer<T> refused(Refusal why) {
        return new Answer<>(null, why);
    }

    public boolean ok() {
        return refusal == null;
    }

    public Optional<T> result() {
        return Optional.ofNullable(value);
    }
}

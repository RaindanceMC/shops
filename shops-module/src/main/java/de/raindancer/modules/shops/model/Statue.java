package de.raindancer.modules.shops.model;

import java.util.UUID;

/**
 * A statue of a store's owner, standing somewhere: clicking it opens the store. The entity carries the store's id
 * itself; this row is so that a store can list, count, find and remove its statues without searching the world.
 *
 * @param entity the Mannequin's own id
 */
public record Statue(UUID entity, UUID store, Spot spot, float yaw, long placedAt) {
}

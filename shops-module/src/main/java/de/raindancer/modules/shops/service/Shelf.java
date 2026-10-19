package de.raindancer.modules.shops.service;

/**
 * Somewhere the ware is kept — the shop's container, or the customer's inventory — counted in items. A seam: on a
 * server it is an inventory and one exact item ({@link InventoryShelf}); in a test it is a number.
 *
 * <p>Items are taken away and made again from the shop's own copy of the ware rather than carried across. Only
 * stacks {@code isSimilar} to that copy are ever counted or taken, so what is made is the same thing to the last
 * enchantment.
 */
public interface Shelf {

    int count();

    /** How many more would fit. */
    int room();

    /** Takes exactly this many, or none at all. */
    boolean remove(int items);

    /** @return how many did not fit and are not anywhere */
    int add(int items);
}

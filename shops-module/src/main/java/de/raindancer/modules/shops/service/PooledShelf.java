package de.raindancer.modules.shops.service;

import java.util.List;

/**
 * An offer's linked chests as one shelf. Taking is all or nothing across them; adding fills them in the order they
 * were linked, and what fits nowhere goes to the first, which spills it rather than losing it.
 */
public final class PooledShelf implements Shelf {

    private final List<Shelf> shelves;

    public PooledShelf(List<? extends Shelf> shelves) {
        this.shelves = List.copyOf(shelves);
    }

    @Override
    public int count() {
        int count = 0;
        for (Shelf shelf : shelves) {
            count += shelf.count();
        }
        return count;
    }

    @Override
    public int room() {
        int room = 0;
        for (Shelf shelf : shelves) {
            room += shelf.room();
        }
        return room;
    }

    @Override
    public boolean remove(int items) {
        if (items > count()) {
            return false;
        }
        int[] taken = new int[shelves.size()];
        int left = items;
        for (int at = 0; at < shelves.size() && left > 0; at++) {
            int here = Math.min(left, shelves.get(at).count());
            if (here <= 0) {
                continue;
            }
            if (!shelves.get(at).remove(here)) {
                for (int back = 0; back < at; back++) {
                    if (taken[back] > 0) {
                        shelves.get(back).add(taken[back]);
                    }
                }
                return false;
            }
            taken[at] = here;
            left -= here;
        }
        return left == 0;
    }

    @Override
    public int add(int items) {
        if (shelves.isEmpty()) {
            return items;
        }
        int left = items;
        for (Shelf shelf : shelves) {
            int fits = Math.min(left, shelf.room());
            if (fits > 0) {
                left -= fits - shelf.add(fits);
            }
            if (left == 0) {
                return 0;
            }
        }
        return shelves.get(0).add(left);
    }
}

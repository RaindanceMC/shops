package de.raindancer.modules.shops.model;

import de.raindancer.core.ui.choose.Catalogue;
import de.raindancer.core.ui.text.WordSearch;

import java.util.Locale;

/**
 * What a shop trades in: one kind of item, exactly — enchantments, name and damage included.
 *
 * @param material the item's material name, for searching and for a fallback icon
 * @param encoded  the item itself as Core's {@code ItemText}; decoded only where a server exists
 * @param label    what a player calls it: its custom name, or the material's name in words
 */
public record Ware(String material, String encoded, String label) {

    public Ware {
        material = material == null ? "" : material.toUpperCase(Locale.ROOT);
        encoded = encoded == null ? "" : encoded;
        label = label == null || label.isBlank() ? (material.isEmpty() ? "Nothing" : Catalogue.readable(material)) : label.strip();
    }

    /** Whether somebody searching for {@code typed} means this — Core's word search over its label and material. */
    public boolean matches(String typed) {
        return WordSearch.matches(typed, label, material);
    }
}

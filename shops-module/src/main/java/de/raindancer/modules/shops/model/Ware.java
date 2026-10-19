package de.raindancer.modules.shops.model;

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
        label = label == null || label.isBlank() ? words(material) : label.strip();
    }

    /** {@code DIAMOND_SWORD} as {@code Diamond Sword}. */
    public static String words(String material) {
        if (material == null || material.isBlank()) {
            return "Nothing";
        }
        StringBuilder out = new StringBuilder();
        for (String part : material.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    /**
     * Whether somebody searching for {@code typed} means this: every word they typed appears in the label or the
     * material, in any order and case — {@code "sword dia"} finds a diamond sword.
     */
    public boolean matches(String typed) {
        if (typed == null || typed.isBlank()) {
            return true;
        }
        String haystack = (label + " " + material.replace('_', ' ')).toLowerCase(Locale.ROOT);
        for (String word : typed.toLowerCase(Locale.ROOT).replace('_', ' ').trim().split("\\s+")) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }
}

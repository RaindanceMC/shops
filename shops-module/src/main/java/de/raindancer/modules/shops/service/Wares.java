package de.raindancer.modules.shops.service;

import de.raindancer.core.content.items.ItemWords;
import de.raindancer.core.data.nbt.ItemText;
import de.raindancer.modules.shops.model.Ware;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Between a real item and the {@link Ware} a shop stores — decoded once per ware, handed out as copies. */
public final class Wares {

    private final Map<String, ItemStack> decoded = new ConcurrentHashMap<>();

    /** One of this item, as a ware. */
    public Ware of(ItemStack item) {
        ItemStack one = item.clone();
        one.setAmount(1);
        return new Ware(one.getType().name(), ItemText.encode(one), label(one));
    }

    /** One of the ware, a fresh copy; empty when the encoded item cannot be read on this server any more. */
    public Optional<ItemStack> item(Ware ware) {
        if (ware.encoded().isEmpty()) {
            return Optional.empty();
        }
        ItemStack known = decoded.computeIfAbsent(ware.encoded(), encoded -> {
            try {
                ItemStack read = ItemText.decode(encoded);
                return read == null ? new ItemStack(Material.AIR) : read;
            } catch (RuntimeException unreadable) {
                return new ItemStack(Material.AIR);
            }
        });
        return known.getType().isAir() ? Optional.empty() : Optional.of(known.clone());
    }

    /** The material to draw when the item itself cannot be had. */
    public Material material(Ware ware) {
        Material material = Material.matchMaterial(ware.material());
        return material == null || !material.isItem() ? Material.BARRIER : material;
    }

    /** What a player calls it: its given name, or the material's — Core's {@code ItemWords}. */
    private static String label(ItemStack item) {
        return ItemWords.of(item).shown();
    }
}

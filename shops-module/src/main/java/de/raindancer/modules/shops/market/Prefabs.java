package de.raindancer.modules.shops.market;

import de.raindancer.core.world.build.Schematic;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds of the owner's own for the market world, as Sponge schematics: stalls for unbuilt plots and house fronts for
 * the house rows. Drawn as they are, turned half round where they face the other way.
 *
 * <p>How a schematic must lie: its lowest layer is the street's level (the ground the build stands on); for a stall,
 * its north side (smallest z) is the front to the street and it is at most 12 × 12; for a house, its west side
 * (smallest x) is the front to the cross street, at most 7 deep and 25 long.
 *
 * @param stalls for unbuilt plots, one picked per plot
 * @param houses for the house rows, picked along each row while they fit; the rest of a row is built as usual
 */
public record Prefabs(List<Schematic> stalls, List<Schematic> houses) {

    public static final Prefabs NONE = new Prefabs(List.of(), List.of());

    private static final Pattern FACING = Pattern.compile("facing=(north|south|east|west)");
    private static final Pattern ROTATION = Pattern.compile("rotation=(\\d+)");
    private static final Pattern SIDE = Pattern.compile("(?<=[\\[,])(north|south|east|west)=");

    public Prefabs {
        stalls = List.copyOf(stalls);
        houses = List.copyOf(houses);
    }

    /** Why a schematic cannot be a stall; empty when it can. */
    public static String whyNotAStall(Schematic schematic) {
        if (schematic.width() > MarketLayout.PLOT) {
            return "is " + schematic.width() + " wide (x); a plot is " + MarketLayout.PLOT;
        }
        if (schematic.length() > MarketLayout.PLOT) {
            return "is " + schematic.length() + " deep (z); a plot is " + MarketLayout.PLOT;
        }
        return tooHigh(schematic);
    }

    /** Why a schematic cannot be a house front; empty when it can. */
    public static String whyNotAHouse(Schematic schematic) {
        if (schematic.width() > MarketLayout.HOUSES) {
            return "is " + schematic.width() + " deep (x); a house row is " + MarketLayout.HOUSES;
        }
        if (schematic.length() > MarketLayout.PLOT * 2 + MarketLayout.HEDGE) {
            return "is " + schematic.length() + " long (z); a house row is " + (MarketLayout.PLOT * 2 + MarketLayout.HEDGE);
        }
        return tooHigh(schematic);
    }

    private static String tooHigh(Schematic schematic) {
        int most = MarketDesigns.highest() - MarketDesigns.GROUND + 1;
        if (schematic.height() > most) {
            return "is " + schematic.height() + " high; the market builds up to " + most;
        }
        if (!schematic.foreign().isEmpty()) {
            return "has blocks no plain server knows: " + String.join(", ", schematic.foreign());
        }
        return "";
    }

    /** A state turned half round about the vertical: facings, connections and a standing sign's rotation. */
    public static String turned(String state) {
        if (state == null || state.indexOf('[') < 0) {
            return state;
        }
        Matcher facing = FACING.matcher(state);
        String out = facing.find() ? facing.replaceFirst("facing=" + opposite(facing.group(1))) : state;
        Matcher sides = SIDE.matcher(out);
        StringBuilder swapped = new StringBuilder();
        while (sides.find()) {
            sides.appendReplacement(swapped, opposite(sides.group(1)) + "=");
        }
        sides.appendTail(swapped);
        out = swapped.toString();
        Matcher rotation = ROTATION.matcher(out);
        if (rotation.find()) {
            out = rotation.replaceFirst("rotation=" + ((Integer.parseInt(rotation.group(1)) + 8) % 16));
        }
        return sortedProperties(out);
    }

    /** Properties in name order, as Minecraft writes them, so a turned state reads the same as one written so. */
    private static String sortedProperties(String state) {
        int open = state.indexOf('[');
        String[] properties = state.substring(open + 1, state.length() - 1).split(",");
        java.util.Arrays.sort(properties);
        return state.substring(0, open) + "[" + String.join(",", properties) + "]";
    }

    private static String opposite(String face) {
        return switch (face) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }
}

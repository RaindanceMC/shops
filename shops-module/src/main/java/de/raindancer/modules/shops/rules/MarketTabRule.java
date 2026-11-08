package de.raindancer.modules.shops.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** What {@code /market} completes: its verbs, the wares on sale (word by word) and {@code admin rotate}. */
public final class MarketTabRule implements IShopsRule {

    private static final List<String> WORDS = List.of("browse", "sell", "mine", "plot", "claim", "home", "help",
            "statement", "admin");
    private static final Set<String> NEED_A_MARKET = Set.of("plot", "claim", "home");
    private static final Set<String> SEARCHING = Set.of("browse", "find", "sell");

    /**
     * @param args   what is typed, the last one being completed
     * @param wares  labels of what the shops here and elsewhere trade, as they are shown
     */
    public List<String> suggest(String[] args, boolean admin, boolean marketWorld, Collection<String> wares) {
        if (args.length == 0) {
            return suggest(new String[]{""}, admin, marketWorld, wares);
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> found = new ArrayList<>();
            for (String word : WORDS) {
                if (word.startsWith(first) && (admin || !word.equals("admin"))
                        && (marketWorld || !NEED_A_MARKET.contains(word))) {
                    found.add(word);
                }
            }
            if (!first.isEmpty()) {
                found.addAll(words(wares, args, 0));
            }
            return found;
        }
        if (SEARCHING.contains(first)) {
            return words(wares, args, 1);
        }
        if (first.equals("admin") && admin && marketWorld && args.length == 2
                && "rotate".startsWith(args[1].toLowerCase(Locale.ROOT))) {
            return List.of("rotate");
        }
        if (!WORDS.contains(first)) {
            return words(wares, args, 0);
        }
        return List.of();
    }

    /** The next word of every ware label that begins with the words typed from {@code from} on. */
    private static List<String> words(Collection<String> wares, String[] args, int from) {
        int typed = args.length - from;
        String[] before = new String[typed - 1];
        for (int at = 0; at < before.length; at++) {
            before[at] = args[from + at].toLowerCase(Locale.ROOT);
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        Set<String> next = new LinkedHashSet<>();
        for (String ware : wares) {
            String[] label = ware.toLowerCase(Locale.ROOT).split(" +");
            if (label.length < typed) {
                continue;
            }
            boolean fits = label[typed - 1].startsWith(last);
            for (int at = 0; fits && at < before.length; at++) {
                fits = label[at].equals(before[at]);
            }
            if (fits) {
                next.add(label[typed - 1]);
            }
        }
        return new ArrayList<>(next);
    }
}

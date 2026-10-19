package de.raindancer.modules.shops.command;

import de.raindancer.core.platform.command.PlayerLookup;
import de.raindancer.core.platform.command.PlayerTargets;
import de.raindancer.core.social.economy.Economies;
import de.raindancer.core.social.economy.Economy;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Role;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Statue;
import de.raindancer.modules.shops.model.Status;
import de.raindancer.modules.shops.model.Store;
import de.raindancer.modules.shops.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /bazaar}: every players' shop thing under one name. Bare, it opens the browser; a word it does not know is a
 * search — {@code /bazaar diamond} — so the most common thing needs nothing to remember.
 */
public final class BazaarCommand implements IShopsCommand {

    private static final List<String> WORDS = List.of("sell", "mine", "create", "info", "price", "rent", "trust",
            "untrust", "newstore", "statue", "statement", "admin");

    /** How far a player may be looking at a container they mean. */
    private static final int LOOK = 6;

    /** How near a container clicked a moment ago must still be. */
    private static final int NEAR = 8;

    private final Supplier<ShopsServices> services;

    public BazaarCommand(Supplier<ShopsServices> services) {
        this.services = services;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        ShopsServices live = services.get();
        try {
            run(live, source, args);
        } catch (RuntimeException | LinkageError broken) {
            live.log().error(broken, "/bazaar {} by {} failed: {}", String.join(" ", args),
                    source.getSender().getName(), broken.toString());
            live.messages().send(source.getSender(), "shops.broke");
        }
    }

    private void run(ShopsServices live, CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            live.messages().send(sender, "shops.only-a-player");
            return;
        }
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String[] rest = args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length);
        switch (first) {
            case "" -> live.screens().browse(player, "", TradeSide.BUY);
            case "sell" -> live.screens().browse(player, String.join(" ", rest), TradeSide.SELL);
            case "mine" -> live.screens().mine(player);
            case "create" -> create(live, player, rest);
            case "info" -> looked(live, player).ifPresent(shop -> info(live, player, shop));
            case "price" -> price(live, player, rest);
            case "rent" -> looked(live, player).ifPresent(shop -> say(live, player,
                    live.keeper().payRent(player, shop, rest.length > 0 ? number(rest[0], 1) : 1), "shops.rent.paid"));
            case "trust" -> trust(live, player, rest, true);
            case "untrust" -> trust(live, player, rest, false);
            case "newstore" -> newStore(live, player, rest);
            case "statue" -> statue(live, player);
            case "statement" -> live.screens().statement(player);
            case "admin" -> {
                if (player.hasPermission(PermissionNodes.ADMIN)) {
                    live.screens().admin(player);
                } else {
                    live.messages().send(player, "shops.refused.not-allowed");
                }
            }
            default -> live.screens().browse(player, String.join(" ", args), TradeSide.BUY);
        }
    }

    private static void create(ShopsServices live, Player player, String[] args) {
        Block block = targeted(live, player);
        if (block == null) {
            live.messages().send(player, "shops.look-at-container");
            return;
        }
        Money buy = args.length > 0 ? money(args[0]).orElse(null) : Money.ZERO;
        Money sell = args.length > 1 ? money(args[1]).orElse(null) : Money.ZERO;
        int bundle = args.length > 2 ? number(args[2], -1) : 1;
        if (buy == null || sell == null || bundle < 1) {
            live.messages().send(player, "shops.create.usage");
            return;
        }
        String name = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        ItemStack ware = player.getInventory().getItemInMainHand();
        if (ware.getType().isAir() && block.getState(false) instanceof Container container) {
            ware = Arrays.stream(container.getInventory().getStorageContents())
                    .filter(item -> item != null && !item.getType().isAir()).findFirst().orElse(null);
        }
        Answer<Shop> opened = live.keeper().open(player, block, ware, new Prices(buy, sell, bundle), name, null);
        if (!opened.ok()) {
            live.log().info("{} could not open a shop at {} {} {}: {}", player.getName(), block.getX(), block.getY(),
                    block.getZ(), opened.refusal());
            live.refuse(player, opened.refusal());
            return;
        }
        Shop shop = opened.value();
        live.messages().send(player, shop.status() == Status.OPEN ? "shops.opened" : "shops.opened-unpriced",
                "shop", shop.name(), "ware", shop.ware().label());
        if (shop.status() != Status.OPEN) {
            live.screens().manage(player, shop);
        }
    }

    private static void info(ShopsServices live, Player player, Shop shop) {
        live.messages().send(player, "shops.info", "shop", shop.name(), "owner", shop.ownerName(),
                "ware", shop.ware().label(), "bundle", Integer.toString(shop.prices().bundle()),
                "buy", shop.prices().sells() ? Fees.format(shop.prices().buy()) : "—",
                "sell", shop.prices().buys() ? Fees.format(shop.prices().sell()) : "—",
                "stock", Integer.toString(live.book().knownOf(shop.id()).stock()));
    }

    private static void price(ShopsServices live, Player player, String[] args) {
        if (args.length < 2 || !(args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("sell"))) {
            live.messages().send(player, "shops.price.usage");
            return;
        }
        Optional<Money> amount = args[1].equals("0") ? Optional.of(Money.ZERO) : money(args[1]);
        if (amount.isEmpty()) {
            live.messages().send(player, "shops.prompt.not-money", "typed", args[1]);
            return;
        }
        looked(live, player).ifPresent(shop -> {
            Prices now = shop.prices();
            Prices changed = args[0].equalsIgnoreCase("buy") ? now.withBuy(amount.get()) : now.withSell(amount.get());
            say(live, player, live.keeper().prices(player, shop, changed), "shops.changed");
        });
    }

    private static void trust(ShopsServices live, Player player, String[] args, boolean trusting) {
        if (args.length == 0) {
            live.messages().send(player, "shops.trust.usage");
            return;
        }
        PlayerLookup found = PlayerTargets.lookup(live.server(), player, args[0]);
        Optional<OfflinePlayer> who = found.single();
        if (who.isEmpty()) {
            live.messages().send(player, "shops.no-such-player", "player", args[0]);
            return;
        }
        Role role = args.length > 1 && args[1].equalsIgnoreCase("manager") ? Role.MANAGER : Role.CLERK;
        String name = who.get().getName() == null ? args[0] : who.get().getName();
        looked(live, player).ifPresent(shop -> say(live, player, live.keeper().trust(player, shop,
                who.get().getUniqueId(), trusting ? role : null), trusting ? "shops.trusted" : "shops.untrusted",
                "player", name, "shop", shop.name()));
    }

    private static void newStore(ShopsServices live, Player player, String[] args) {
        if (args.length == 0) {
            live.messages().send(player, "shops.store.usage");
            return;
        }
        String name = String.join(" ", args);
        Answer<Store> made = live.keeper().openStore(player, name);
        if (made.ok()) {
            live.messages().send(player, "shops.store.opened", "store", made.value().name());
            live.screens().storeManage(player, made.value());
        } else {
            live.refuse(player, made.refusal());
        }
    }

    private static void statue(ShopsServices live, Player player) {
        List<Store> stores = live.book().storesOf(player.getUniqueId());
        if (stores.size() > 1) {
            live.screens().mine(player);
            live.messages().send(player, "shops.statue.which");
            return;
        }
        Store store = stores.isEmpty() ? live.keeper().firstStore(player.getUniqueId(), player.getName())
                : stores.get(0);
        Answer<Statue> placed = live.statues().place(player, store);
        if (placed.ok()) {
            live.messages().send(player, "shops.statue.placed", "store", store.name());
        } else {
            live.refuse(player, placed.refusal());
        }
    }

    /**
     * The container the player looks at, or else the one they clicked within the last minute and still stand near —
     * aiming exactly is hard from a phone or a controller.
     */
    private static Block targeted(ShopsServices live, Player player) {
        Block looked = player.getTargetBlockExact(LOOK);
        if (looked != null && (looked.getState(false) instanceof Container
                || live.locator().shopAt(looked).isPresent())) {
            return looked;
        }
        return live.recent().of(player.getUniqueId(), System.currentTimeMillis())
                .flatMap(live.locator()::location)
                .filter(at -> at.getWorld().equals(player.getWorld())
                        && at.distanceSquared(player.getLocation()) <= NEAR * NEAR)
                .map(org.bukkit.Location::getBlock)
                .orElse(looked);
    }

    private static Optional<Shop> looked(ShopsServices live, Player player) {
        Block block = targeted(live, player);
        Optional<Shop> shop = block == null ? Optional.empty() : live.locator().shopAt(block);
        if (shop.isEmpty()) {
            live.messages().send(player, "shops.look-at-shop");
        }
        return shop;
    }

    private static void say(ShopsServices live, Player player, Answer<?> answer, String done, Object... values) {
        if (answer.ok()) {
            live.messages().send(player, done, values);
        } else {
            live.refuse(player, answer.refusal());
        }
    }

    private static Optional<Money> money(String typed) {
        return Economies.current().map(Economy::currency)
                .orElse(de.raindancer.core.social.economy.Currency.DEFAULT).parse(typed);
    }

    private static int number(String typed, int otherwise) {
        try {
            return Integer.parseInt(typed.strip());
        } catch (NumberFormatException notANumber) {
            return otherwise;
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> words = new ArrayList<>();
            for (String word : WORDS) {
                if (word.startsWith(typed) && (!word.equals("admin")
                        || source.getSender().hasPermission(PermissionNodes.ADMIN))) {
                    words.add(word);
                }
            }
            return words;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if ((first.equals("trust") || first.equals("untrust")) && args.length == 2) {
            ShopsServices live = services.get();
            return PlayerTargets.suggest(live.server(), source.getSender(), args[1], viewer -> true);
        }
        if (first.equals("trust") && args.length == 3) {
            return List.of("clerk", "manager");
        }
        if (first.equals("price") && args.length == 2) {
            return List.of("buy", "sell");
        }
        return List.of();
    }

    @Override
    public String permission() {
        return PermissionNodes.USE;
    }
}

package de.raindancer.modules.shops;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Range;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import org.bukkit.Material;

/**
 * What an owner can decide about players' shops — shopkeepers, their offers and the chests the offers sell from.
 * The record is the schema: the file, its comments, its validation
 * and the {@code /settings} pages all come from it.
 */
@Settings(id = "shops", topics = {
        @Topic(path = "shops", title = "Player shops", icon = Material.CHEST),
        @Topic(path = "shops/opening", title = "Opening a shop", icon = Material.OAK_SIGN),
        @Topic(path = "shops/trading", title = "Trading", icon = Material.EMERALD),
        @Topic(path = "shops/money", title = "Fees, rent and the server's cut", icon = Material.GOLD_INGOT),
        @Topic(path = "shops/finding", title = "Finding shops", icon = Material.SPYGLASS),
})
public record ShopsSettings(

        @In("shops/opening") @Title("Blocks that may be stock chests")
        @Describe("Containers, by name, separated by commas, that an offer may keep its stock in. Shulker boxes and "
                + "ender chests never are, whatever this says: a box is carried off with its stock in it, and an ender "
                + "chest is each player's own.")
        @Key("containers")
        String containers,

        @In("shops/opening") @Title("Worlds without shops")
        @Describe("World names, separated by commas, where nobody may set up a shop — an arena, a lobby. Blank allows "
                + "them everywhere the land allows building.")
        @Key("closed-worlds")
        String closedWorlds,

        @In("shops/opening") @Title("Shops one player may have") @Range(min = 0, max = 100)
        @Describe("A shop is a shopkeeper with the owner's skin and the offers it sells. Permissions "
                + "rainsshops.limit.<number> raise it for some; rainsshops.limit.unlimited has no limit. Zero lets only "
                + "those with a permission set up shops.")
        @Key("most-shops")
        int mostShops,

        @In("shops/opening") @Title("Offers one shop may have") @Range(min = 1, max = 200)
        @Describe("Each offer is one item, its prices, and the chests its stock is kept in.")
        @Key("most-offers")
        int mostOffers,

        @In("shops/opening") @Title("Chests one offer may take its stock from") @Range(min = 1, max = 64)
        @Describe("Both halves of a double chest count as one.")
        @Key("most-chests")
        int mostChests,

        @In("shops/opening") @Title("Farthest a stock chest may be from its shopkeeper") @Range(min = 4, max = 64)
        @Describe("Blocks. The chests and the shopkeeper make one shop; a chest across the map is not part of it.")
        @Key("link-reach")
        int linkReach,

        @In("shops/opening") @Title("Longest a shop's or an offer's name may be") @Range(min = 3, max = 48)
        @Describe("Characters. It heads the shop's page and stands over the shopkeeper, and a long one is cut off.")
        @Key("longest-name")
        int longestName,

        @In("shops/money") @Title("Price of setting up a shop")
        @Describe("Money, written like 25 or 1.5k, taken when a shopkeeper is placed for a new shop. 0 makes it free. "
                + "Staff with rainsshops.bypass.fees pay nothing.")
        @Key("opening-price")
        String openingPrice,

        @In("shops/money") @Title("Rent per offer per week")
        @Describe("Money each offer costs every week, the first week included in adding it. Unpaid, the offer pauses "
                + "until its owner pays — nothing is ever taken away or deleted for it. 0 switches rent off.")
        @Key("rent-per-week")
        String rentPerWeek,

        @In("shops/money") @Title("Most weeks paid ahead") @Range(min = 1, max = 52)
        @Describe("How far ahead an owner may pay rent. Stops a fortune vanishing into rent by a slip.")
        @Key("most-weeks-ahead")
        int mostWeeksAhead,

        @In("shops/money") @Title("Server's cut of every trade (percent)") @Range(min = 0, max = 100)
        @Describe("How much of what is paid in a trade nobody receives — a sink for money. The buyer pays the price; "
                + "the seller receives it minus this. 0 is no cut.")
        @Key("server-cut-percent")
        int serverCutPercent,

        @In("shops/money") @Title("Highest price for one bundle")
        @Describe("Money. A ceiling on what an offer may ask or pay for one bundle, against typos with six zeros. 0 is "
                + "no ceiling.")
        @Key("most-price")
        String mostPrice,

        @In("shops/trading") @Title("Trade what is possible")
        @Describe("Whether a trade too large for the stock, the room or the money is cut down to what is possible "
                + "rather than refused. Either way the customer is told.")
        @Key("partial-trades")
        boolean partialTrades,

        @In("shops/trading") @Title("Hoppers may fill stock chests")
        @Describe("Whether a hopper may push items into a stock chest, for a farm that stocks its owner's offer. A "
                + "hopper never takes anything out of one, whatever this says.")
        @Key("hoppers-fill-shops")
        boolean hoppersFillShops,

        @In("shops/trading") @Title("Tell owners at once")
        @Describe("Whether an owner who is online hears about each trade as it happens. Owners who are away always get "
                + "a summary when they come back.")
        @Key("tell-online-owners")
        boolean tellOnlineOwners,

        @In("shops/trading") @Title("Keep trades for (days)") @Range(min = 7, max = 3650)
        @Describe("How long a shop's log and an owner's statement go back. Older trades are forgotten.")
        @Key("keep-trades-days")
        int keepTradesDays,

        @In("shops/trading") @Title("Trade from the bazaar")
        @Describe("Whether customers may buy and sell from /bazaar without walking to the shop, as long as its stock "
                + "chests are on this server. Off, /bazaar only finds shops and takes you there.")
        @Key("remote-trades")
        boolean remoteTrades,

        @In("shops/trading") @Title("Days a customer may review after trading") @Range(min = 1, max = 365)
        @Describe("Only somebody who traded at a shop within this many days may review it.")
        @Key("review-days")
        int reviewDays,

        @In("shops/trading") @Title("Longest review") @Range(min = 10, max = 256)
        @Describe("Characters.")
        @Key("longest-review")
        int longestReview,

        @In("shops/finding") @Title("Show shops on other servers")
        @Describe("On a network: whether /bazaar lists the offers of the other servers too, with their stock as of "
                + "their last trade.")
        @Key("browse-network")
        boolean browseNetwork,

        @In("shops/finding") @Title("Travel to shops")
        @Describe("Whether a shop in the bazaar can be clicked to go to its shopkeeper — on another server too. The "
                + "shop's land still decides whether visitors may arrive.")
        @Key("travel-to-shops")
        boolean travelToShops,

        @In("shops/finding") @Title("Stand still before travelling") @Range(min = 0, max = 60)
        @Describe("Seconds. Zero goes at once.")
        @Key("travel-warmup")
        int travelWarmup,

        @In("shops/finding") @Title("Price of travelling to a shop")
        @Describe("Money taken for each trip to a shop. 0 is free.")
        @Key("travel-price")
        String travelPrice) {

    public static final ShopsSettings DEFAULTS = new ShopsSettings(
            "CHEST, TRAPPED_CHEST, BARREL", "", 3, 27, 12, 24, 24,
            "0", "0", 8, 0, "0",
            true, true, true, 180, true, 30, 120,
            true, true, 3, "0");

    public int cutPercent() {
        return Math.max(0, Math.min(100, serverCutPercent));
    }

    public int nameLimit() {
        return Math.max(3, Math.min(48, longestName));
    }

    /** Shops (shopkeepers) one player may have, before permissions. */
    public int shopLimit() {
        return Math.max(0, Math.min(100, mostShops));
    }

    public int offerLimit() {
        return Math.max(1, Math.min(200, mostOffers));
    }

    public int chestLimit() {
        return Math.max(1, Math.min(64, mostChests));
    }

    /** Farthest a stock chest may be from its shopkeeper, in blocks. */
    public int reach() {
        return Math.max(4, Math.min(64, linkReach));
    }

    public int weeksAhead() {
        return Math.max(1, Math.min(52, mostWeeksAhead));
    }

    public int warmup() {
        return Math.max(0, Math.min(60, travelWarmup));
    }

    public int reviewWindowDays() {
        return Math.max(1, Math.min(365, reviewDays));
    }

    public int reviewLimit() {
        return Math.max(10, Math.min(256, longestReview));
    }

    public int tradeDays() {
        return Math.max(7, Math.min(3650, keepTradesDays));
    }

    public ShopsSettings withServerCutPercent(int percent) {
        return new ShopsSettings(containers, closedWorlds, mostShops, mostOffers, mostChests, linkReach, longestName,
                openingPrice, rentPerWeek, mostWeeksAhead, percent, mostPrice, partialTrades, hoppersFillShops,
                tellOnlineOwners, keepTradesDays, remoteTrades, reviewDays, longestReview, browseNetwork,
                travelToShops, travelWarmup, travelPrice);
    }

    public ShopsSettings withPartialTrades(boolean partial) {
        return new ShopsSettings(containers, closedWorlds, mostShops, mostOffers, mostChests, linkReach, longestName,
                openingPrice, rentPerWeek, mostWeeksAhead, serverCutPercent, mostPrice, partial, hoppersFillShops,
                tellOnlineOwners, keepTradesDays, remoteTrades, reviewDays, longestReview, browseNetwork,
                travelToShops, travelWarmup, travelPrice);
    }

    public ShopsSettings withRentPerWeek(String rent) {
        return new ShopsSettings(containers, closedWorlds, mostShops, mostOffers, mostChests, linkReach, longestName,
                openingPrice, rent, mostWeeksAhead, serverCutPercent, mostPrice, partialTrades, hoppersFillShops,
                tellOnlineOwners, keepTradesDays, remoteTrades, reviewDays, longestReview, browseNetwork,
                travelToShops, travelWarmup, travelPrice);
    }

    public ShopsSettings withOpeningPrice(String price) {
        return new ShopsSettings(containers, closedWorlds, mostShops, mostOffers, mostChests, linkReach, longestName,
                price, rentPerWeek, mostWeeksAhead, serverCutPercent, mostPrice, partialTrades, hoppersFillShops,
                tellOnlineOwners, keepTradesDays, remoteTrades, reviewDays, longestReview, browseNetwork,
                travelToShops, travelWarmup, travelPrice);
    }

    public ShopsSettings withMostShops(int most) {
        return new ShopsSettings(containers, closedWorlds, most, mostOffers, mostChests, linkReach, longestName,
                openingPrice, rentPerWeek, mostWeeksAhead, serverCutPercent, mostPrice, partialTrades,
                hoppersFillShops, tellOnlineOwners, keepTradesDays, remoteTrades, reviewDays, longestReview,
                browseNetwork, travelToShops, travelWarmup, travelPrice);
    }

    public ShopsSettings withLinkReach(int blocks) {
        return new ShopsSettings(containers, closedWorlds, mostShops, mostOffers, mostChests, blocks, longestName,
                openingPrice, rentPerWeek, mostWeeksAhead, serverCutPercent, mostPrice, partialTrades,
                hoppersFillShops, tellOnlineOwners, keepTradesDays, remoteTrades, reviewDays, longestReview,
                browseNetwork, travelToShops, travelWarmup, travelPrice);
    }

    public ShopsSettings withRemoteTrades(boolean remote) {
        return new ShopsSettings(containers, closedWorlds, mostShops, mostOffers, mostChests, linkReach, longestName,
                openingPrice, rentPerWeek, mostWeeksAhead, serverCutPercent, mostPrice, partialTrades,
                hoppersFillShops, tellOnlineOwners, keepTradesDays, remote, reviewDays, longestReview, browseNetwork,
                travelToShops, travelWarmup, travelPrice);
    }
}

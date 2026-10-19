package de.raindancer.modules.shops;

import de.raindancer.core.RainsCore;
import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.ui.chat.Brand;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.service.NoticeService;
import de.raindancer.modules.shops.service.ReviewService;
import de.raindancer.modules.shops.service.ShopBook;
import de.raindancer.modules.shops.service.ShopKeeper;
import de.raindancer.modules.shops.service.ShopLocator;
import de.raindancer.modules.shops.service.ShopTrades;
import de.raindancer.modules.shops.service.ShopTravel;
import de.raindancer.modules.shops.service.Wares;
import de.raindancer.modules.shops.visual.Shopkeepers;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/**
 * Everything this module has built, handed to the listeners, screens and commands. Data, not a god object.
 *
 * @param settings behind a supplier: a reload replaces the snapshot, and a screen holding the old one would draw
 *                 yesterday's fees
 */
public record ShopsServices(
        Plugin plugin,
        Server server,
        RainsCore core,
        LogChannel log,
        Messages messages,
        Brand brand,
        Supplier<ShopsSettings> settings,
        SettingsStore<ShopsSettings> store,
        ShopBook book,
        ShopLocator locator,
        de.raindancer.modules.shops.service.Linking linking,
        Wares wares,
        ShopKeeper keeper,
        ShopTrades trades,
        NoticeService notices,
        ReviewService reviews,
        ShopTravel travel,
        Shopkeepers keepers,
        IShopsScreensOpener screens) {

    public ShopsSettings config() {
        return settings.get();
    }

    /** Sends a refusal's own sentence. */
    public void refuse(Player player, Refusal why) {
        messages.send(player, why.key());
    }
}

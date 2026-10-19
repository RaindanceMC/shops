package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Review;
import de.raindancer.modules.shops.model.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** One to five stars, then a line typed in chat (or none). */
public final class ReviewMenu extends Menu implements IShopsScreen {

    private final ShopsServices services;
    private final Shop shop;

    public ReviewMenu(ShopsServices services, Player viewer, Menu parent, Shop shop) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.shop = shop;
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Your review of " + Lines.plain(shop.name()));
    }

    @Override
    public String breadcrumb() {
        return "Your review";
    }

    @Override
    protected void render() {
        for (int stars = Review.FEWEST_STARS; stars <= Review.MOST_STARS; stars++) {
            int given = stars;
            band(MenuLayout.RULES, stars + 1, Icons.of(stars >= 4 ? Material.GOLD_BLOCK
                            : stars >= 3 ? Material.GOLD_INGOT : Material.GOLD_NUGGET,
                    "<gold>" + Review.stars(stars), "<gray>Click, then type a line in chat",
                    "<gray>— or a dash for none."), click -> Lines.ask(services, viewer, "shops.prompt.review",
                    typed -> send(given, typed), () -> send(given, "")));
        }
    }

    private void send(int stars, String text) {
        services.reviews().review(viewer, shop, stars, text, refused -> refused.ifPresentOrElse(
                why -> services.refuse(viewer, why),
                () -> services.messages().send(viewer, "shops.review.thanks", "shop", shop.name())));
    }

    @Override
    public String describe() {
        return "writing a review";
    }
}

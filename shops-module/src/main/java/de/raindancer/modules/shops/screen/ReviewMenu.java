package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.core.ui.prompt.AnvilInput;
import de.raindancer.core.ui.prompt.Parsers;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Review;
import de.raindancer.modules.shops.model.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** One to five stars, and a line of words typed into Core's anvil — or none. */
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
        return MiniMessage.miniMessage().deserialize("<dark_gray>Your review of " + Lines.named(shop.name(), shop.nameStyle(), ""));
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
                    "<gold>" + Review.stars(stars), "<yellow>Click to add a line of words",
                    "<yellow>Right click for stars alone"), click -> {
                if (click.isRightClick()) {
                    send(given, "");
                } else {
                    AnvilInput.open(viewer, Review.stars(given) + " — and in words?", "",
                            Parsers.text(services.config().reviewLimit()), typed -> send(given, typed), this::open);
                }
            });
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

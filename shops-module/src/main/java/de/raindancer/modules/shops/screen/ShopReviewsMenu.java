package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Review;
import de.raindancer.modules.shops.model.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** What customers said, newest first; and the way to say something yourself. Staff take a review down with shift. */
public final class ShopReviewsMenu extends PaginatedMenu<Review> implements IShopsScreen {

    private final ShopsServices services;
    private final Shop shop;
    private final List<Review> reviews;

    public ShopReviewsMenu(ShopsServices services, Player viewer, Menu parent, Shop shop, List<Review> reviews) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.shop = shop;
        this.reviews = new ArrayList<>(reviews);
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Reviews · " + Lines.named(shop.name(), shop.nameStyle(), ""));
    }

    @Override
    public String breadcrumb() {
        return "Reviews";
    }

    @Override
    protected List<Review> entries() {
        return reviews;
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icons.of(Material.COBWEB, "<gray>No reviews yet",
                "<gray>Traded here lately? Be the first: the button below.");
    }

    @Override
    protected ItemStack icon(Review review) {
        List<String> lore = new ArrayList<>();
        lore.add("<gold>" + Review.stars(review.stars()));
        if (!review.text().isEmpty()) {
            lore.add("<white>“" + Text.literal(review.text()) + "”");
        }
        if (services.keeper().isAdmin(viewer)) {
            lore.add("<red>Shift click to take it down (staff)");
        }
        return Icons.head(review.author(), "<white>" + Text.literal(review.authorName()), lore);
    }

    @Override
    protected void onClick(Review review, InventoryClickEvent event) {
        if (event.isShiftClick() && services.keeper().isAdmin(viewer)) {
            services.reviews().remove(shop, review.author());
            reviews.remove(review);
            services.messages().send(viewer, "shops.review.removed", "player", review.authorName());
            refresh();
            return;
        }
        services.messages().send(viewer, "shops.review.line", "player", review.authorName(),
                "stars", Review.stars(review.stars()), "text", review.text());
    }

    @Override
    protected void decorate() {
        super.decorate();
        boolean own = services.book().staffOf(shop, viewer.getUniqueId());
        toolbar(4, !own, Icons.of(Material.WRITABLE_BOOK, "<white>Write a review",
                        "<gray>For customers who traded here lately."),
                "Not about your own offer", click -> new ReviewMenu(services, viewer, this, shop).open());
    }

    @Override
    public String describe() {
        return "what customers said about a shop";
    }
}

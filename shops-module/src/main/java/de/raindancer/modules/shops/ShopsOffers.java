package de.raindancer.modules.shops;

import de.raindancer.core.ui.travel.TravelDoor;
import de.raindancer.modules.shops.util.PermissionNodes;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** What the shops offer to Core's {@code /travel} page: the market world, while it is open. */
final class ShopsOffers {

    private ShopsOffers() {
    }

    static TravelDoor marketDoor(Consumer<Player> go) {
        return TravelDoor.of("shops:market", 55, "Market", "Streets of stalls where players run their shops.",
                        (player, parent) -> go.accept(player))
                .needing(PermissionNodes.USE);
    }
}

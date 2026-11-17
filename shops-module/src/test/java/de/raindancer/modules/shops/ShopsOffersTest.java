package de.raindancer.modules.shops;

import de.raindancer.core.ui.travel.TravelDoor;
import de.raindancer.modules.shops.util.PermissionNodes;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopsOffersTest {

    @Test
    @DisplayName("the market world is a door on /travel that goes where /market goes")
    void marketDoor() {
        Player player = mock(Player.class);
        List<Player> sent = new ArrayList<>();
        TravelDoor door = ShopsOffers.marketDoor(sent::add);

        assertThat(door.id()).isEqualTo("shops:market");
        assertThat(door.title()).isEqualTo("Market");
        assertThat(door.order()).isBetween(50, 60);

        door.open().accept(player, null);
        assertThat(sent).containsExactly(player);
    }

    @Test
    @DisplayName("the market door is locked for whoever may not use shops")
    void marketDoorLocked() {
        Player player = mock(Player.class);
        TravelDoor door = ShopsOffers.marketDoor(who -> { });

        when(player.hasPermission(PermissionNodes.USE)).thenReturn(false);
        assertThat(door.locked().apply(player)).isPresent();
        when(player.hasPermission(PermissionNodes.USE)).thenReturn(true);
        assertThat(door.locked().apply(player)).isEmpty();
    }
}

package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.menu.ConfirmMenu;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.modules.shops.ShopsServices;
import org.bukkit.entity.Player;

import java.util.List;

/** Core's "are you sure?", under this module's name, so ScreenGrammarTest can see every danger button confirms. */
public final class ConfirmScreen extends ConfirmMenu implements IShopsScreen {

    public ConfirmScreen(ShopsServices services, Player viewer, Menu parent, String question,
                         List<String> consequences, Runnable onYes) {
        super(viewer, services.brand(), parent, question, consequences, onYes);
    }

    @Override
    public String describe() {
        return "asking before something that cannot be undone";
    }
}

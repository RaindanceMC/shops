package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.model.Refusal;

import java.util.Optional;

/** Whether a name can stand on a display, in a menu and in chat without doing anything but being read. */
public final class ShopNameRule implements IShopsRule {

    public Optional<Refusal> check(String name, int longest) {
        if (name == null || name.isBlank() || name.strip().length() > longest) {
            return Optional.of(Refusal.BAD_NAME);
        }
        for (char each : name.toCharArray()) {
            if (Character.isISOControl(each) || each == '<' || each == '>' || each == '§') {
                return Optional.of(Refusal.BAD_NAME);
            }
        }
        return Optional.empty();
    }
}

package de.raindancer.modules.shops.rules;

/**
 * What a right click on a chest shop does. Staff open the chest as any chest; a manager sneaking with an empty hand
 * gets the shop's back room — with something in hand, sneaking places it as the game does. A customer gets the
 * counter, from the chest, its sign or its item frame alike.
 */
public final class ChestClickRule implements IShopsRule {

    public enum Outcome { BACK_ROOM, AS_THE_GAME_DOES, COUNTER, OWN_SHOP }

    public Outcome decide(boolean staff, boolean manager, boolean sneaking, boolean emptyHand) {
        if (!staff) {
            return Outcome.COUNTER;
        }
        return manager && sneaking && emptyHand ? Outcome.BACK_ROOM : Outcome.AS_THE_GAME_DOES;
    }

    /** A click on the shop's sign or item frame. */
    public Outcome fixture(boolean staff, boolean manager) {
        if (manager) {
            return Outcome.BACK_ROOM;
        }
        return staff ? Outcome.OWN_SHOP : Outcome.COUNTER;
    }
}

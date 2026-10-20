package de.raindancer.modules.shops.rules;

/**
 * What a right click on a stock chest does. Staff open it as any chest; a manager sneaking with an empty hand gets the
 * offer's back room — with something in hand, sneaking places it (an item frame, a sign on the chest) as the game does.
 * A customer never opens it.
 */
public final class ChestClickRule implements IShopsRule {

    public enum Outcome { BACK_ROOM, AS_THE_GAME_DOES, TO_THE_SHOPKEEPER }

    public Outcome decide(boolean staff, boolean manager, boolean sneaking, boolean emptyHand) {
        if (!staff) {
            return Outcome.TO_THE_SHOPKEEPER;
        }
        return manager && sneaking && emptyHand ? Outcome.BACK_ROOM : Outcome.AS_THE_GAME_DOES;
    }
}

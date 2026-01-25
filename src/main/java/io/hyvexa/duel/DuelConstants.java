package io.hyvexa.duel;

public final class DuelConstants {

    private DuelConstants() {
    }

    // Timing
    public static final int COUNTDOWN_SECONDS = 3;
    public static final long POST_MATCH_DELAY_MS = 2500L;
    public static final long MATCH_TICK_INTERVAL_MS = 100L;

    // Detection
    public static final double TOUCH_RADIUS = 1.5;
    public static final double TOUCH_RADIUS_SQ = TOUCH_RADIUS * TOUCH_RADIUS;
    public static final double TOUCH_VERTICAL_BONUS = 1.0;

    // Items
    public static final String ITEM_FORFEIT = "Ingredient_Duel_Forfeit";
    public static final String ITEM_MENU = "Weapon_Longsword_Flame";

    // Requirements
    public static final int DUEL_UNLOCK_MIN_COMPLETED_MAPS = 40;

    // Messages
    public static final String MSG_QUEUE_JOINED = "You joined the duel queue (%s). Position: #%d.";
    public static final String MSG_QUEUE_LEFT = "You left the duel queue.";
    public static final String MSG_QUEUE_ALREADY = "You're already in the duel queue. Position: #%d.";
    public static final String MSG_IN_MATCH = "You're already in a duel.";
    public static final String MSG_IN_PARKOUR = "Leave your current parkour run first. (/pk leave)";
    public static final String MSG_NO_MAPS = "No duel maps are available for your selected categories.";
    public static final String MSG_DUEL_UNLOCK_REQUIRED =
            "Complete %d maps to unlock duels. You need %d more. Progress: %d/%d.";
    public static final String MSG_MATCH_FOUND = "Match found! Opponent: %s - Map: %s.";
    public static final String MSG_WIN = "Victory! Time: %s - Opponent: %s.";
    public static final String MSG_WIN_VS = "Victory! Time: %s - Opponent: %s.";
    public static final String MSG_LOSE = "Defeat. Opponent time: %s - Opponent: %s.";
    public static final String MSG_WIN_FORFEIT = "Victory! %s forfeited.";
    public static final String MSG_WIN_DISCONNECT = "Victory! %s disconnected.";
    public static final String MSG_FORFEITED = "You forfeited the duel.";
    public static final String MSG_STATS = "Duel stats for %s: %dW / %dL (%d%%).";
    public static final String MSG_STATS_NONE = "No duel stats found for %s.";
}

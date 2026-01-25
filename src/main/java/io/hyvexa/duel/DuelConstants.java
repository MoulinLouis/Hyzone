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

    // Messages
    public static final String MSG_QUEUE_JOINED = "Joined duel queue for %s. Position: #%d";
    public static final String MSG_QUEUE_LEFT = "Left duel queue.";
    public static final String MSG_QUEUE_ALREADY = "You're already in queue. Position: #%d";
    public static final String MSG_IN_MATCH = "You're already in a match!";
    public static final String MSG_IN_PARKOUR = "Leave your current parkour run first. (/pk leave)";
    public static final String MSG_NO_MAPS = "No duel maps available for your selected categories.";
    public static final String MSG_MATCH_FOUND = "Match found! Racing against %s on %s";
    public static final String MSG_WIN = "You win! Your time: %s. Say gg to %s :)";
    public static final String MSG_WIN_VS = "You win! Your time: %s. Say gg to %s :)";
    public static final String MSG_LOSE = "You lose. Opponent time: %s. Say gg to %s :)";
    public static final String MSG_WIN_FORFEIT = "You win! %s forfeited.";
    public static final String MSG_WIN_DISCONNECT = "You win! %s disconnected.";
    public static final String MSG_FORFEITED = "You forfeited the match.";
    public static final String MSG_STATS = "Duel Stats for %s: %dW / %dL (%d%%)";
    public static final String MSG_STATS_NONE = "No duel stats found for %s.";
}

package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.Message;

public final class SystemMessageUtils {

    private SystemMessageUtils() {
    }

    // Palette
    public static final String IDENTITY = "#7C5CFF";
    public static final String INFO = "#6EC1FF";
    public static final String SUCCESS = "#4CFF88";
    public static final String WARN = "#FFC857";
    public static final String ERROR = "#FF5A5F";
    public static final String DUEL = "#B388FF";
    public static final String PARKOUR = "#4DD0E1";
    public static final String SECONDARY = "#9FB0BA";
    public static final String PRIMARY_TEXT = "#E8F1F7";

    private static Message raw(String text, String color) {
        return Message.raw(text).color(color);
    }

    private static Message prefix(String label, String labelColor) {
        return Message.join(
                raw("[", IDENTITY),
                raw(label, labelColor),
                raw("] ", IDENTITY)
        );
    }

    public static Message withServerPrefix(Message... parts) {
        return Message.join(prefix("Server", IDENTITY), Message.join(parts));
    }

    public static Message withDuelPrefix(Message... parts) {
        return Message.join(prefix("Duel", DUEL), Message.join(parts));
    }

    public static Message withParkourPrefix(Message... parts) {
        return Message.join(prefix("Parkour", PARKOUR), Message.join(parts));
    }

    public static Message serverInfo(String text) {
        return withServerPrefix(raw(text, INFO));
    }

    public static Message serverSuccess(String text) {
        return withServerPrefix(raw(text, SUCCESS));
    }

    public static Message serverWarn(String text) {
        return withServerPrefix(raw(text, WARN));
    }

    public static Message serverError(String text) {
        return withServerPrefix(raw(text, ERROR));
    }

    public static Message duelInfo(String text) {
        return withDuelPrefix(raw(text, INFO));
    }

    public static Message duelSuccess(String text) {
        return withDuelPrefix(raw(text, SUCCESS));
    }

    public static Message duelWarn(String text) {
        return withDuelPrefix(raw(text, WARN));
    }

    public static Message duelError(String text) {
        return withDuelPrefix(raw(text, ERROR));
    }

    public static Message parkourInfo(String text) {
        return withParkourPrefix(raw(text, INFO));
    }

    public static Message parkourSuccess(String text) {
        return withParkourPrefix(raw(text, SUCCESS));
    }

    public static Message parkourWarn(String text) {
        return withParkourPrefix(raw(text, WARN));
    }

    public static Message parkourError(String text) {
        return withParkourPrefix(raw(text, ERROR));
    }

    public static Message presence(Message rankPart, String playerName, boolean joined) {
        String verb = joined ? "joined" : "left";
        String verbColor = joined ? SUCCESS : WARN;
        return withServerPrefix(
                raw("[", SECONDARY),
                rankPart,
                raw("] ", SECONDARY),
                raw(playerName, PRIMARY_TEXT),
                raw(" " + verb + " the server.", verbColor)
        );
    }

    public static Message adminAnnouncement(String text) {
        return withServerPrefix(
                raw("Admin: ", WARN),
                raw(text, PRIMARY_TEXT)
        );
    }

}


package io.hyvexa.parkour.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.util.io.BlockingDiskFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GlobalMessageStore extends BlockingDiskFile {

    public static final long DEFAULT_INTERVAL_MINUTES = 10L;
    private static final long MIN_INTERVAL_MINUTES = 1L;
    private static final long MAX_INTERVAL_MINUTES = 1440L;
    private static final int MAX_MESSAGE_LENGTH = 240;
    private static final List<String> DEFAULT_MESSAGES = List.of(
            "Want to contribute? Join our Discord ({link}).",
            "Need help or want to report a bug? Discord ({link}).",
            "Any suggestion? Tell us on Discord! ({link})."
    );

    private final List<String> messages = new ArrayList<>();
    private long intervalMinutes = DEFAULT_INTERVAL_MINUTES;

    public GlobalMessageStore() {
        super(Path.of("Parkour/GlobalMessages.json"));
    }

    public List<String> getMessages() {
        this.fileLock.readLock().lock();
        try {
            return List.copyOf(messages);
        } finally {
            this.fileLock.readLock().unlock();
        }
    }

    public long getIntervalMinutes() {
        this.fileLock.readLock().lock();
        try {
            return intervalMinutes;
        } finally {
            this.fileLock.readLock().unlock();
        }
    }

    public void setIntervalMinutes(long minutes) {
        long clamped = clampInterval(minutes);
        this.fileLock.writeLock().lock();
        try {
            intervalMinutes = clamped;
        } finally {
            this.fileLock.writeLock().unlock();
        }
        this.syncSave();
    }

    public boolean addMessage(String message) {
        String cleaned = normalizeMessage(message);
        if (cleaned.isEmpty()) {
            return false;
        }
        boolean added;
        this.fileLock.writeLock().lock();
        try {
            messages.add(cleaned);
            added = true;
        } finally {
            this.fileLock.writeLock().unlock();
        }
        if (added) {
            this.syncSave();
        }
        return added;
    }

    public boolean removeMessage(int index) {
        boolean removed = false;
        this.fileLock.writeLock().lock();
        try {
            if (index >= 0 && index < messages.size()) {
                messages.remove(index);
                removed = true;
            }
        } finally {
            this.fileLock.writeLock().unlock();
        }
        if (removed) {
            this.syncSave();
        }
        return removed;
    }

    @Override
    protected void read(BufferedReader bufferedReader) throws IOException {
        messages.clear();
        intervalMinutes = DEFAULT_INTERVAL_MINUTES;
        JsonElement parsed = JsonParser.parseReader(bufferedReader);
        if (!parsed.isJsonObject()) {
            applyDefaults();
            return;
        }
        JsonObject object = parsed.getAsJsonObject();
        if (object.has("intervalMinutes")) {
            intervalMinutes = clampInterval(object.get("intervalMinutes").getAsLong());
        }
        if (object.has("messages") && object.get("messages").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("messages")) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }
                String cleaned = normalizeMessage(element.getAsString());
                if (!cleaned.isEmpty()) {
                    messages.add(cleaned);
                }
            }
        }
    }

    @Override
    protected void write(BufferedWriter bufferedWriter) throws IOException {
        JsonObject object = new JsonObject();
        object.addProperty("intervalMinutes", intervalMinutes);
        JsonArray array = new JsonArray();
        for (String message : messages) {
            array.add(message);
        }
        object.add("messages", array);
        bufferedWriter.write(object.toString());
    }

    @Override
    protected void create(BufferedWriter bufferedWriter) throws IOException {
        applyDefaults();
        write(bufferedWriter);
    }

    private void applyDefaults() {
        messages.clear();
        messages.addAll(DEFAULT_MESSAGES);
        intervalMinutes = DEFAULT_INTERVAL_MINUTES;
    }

    private static long clampInterval(long minutes) {
        if (minutes < MIN_INTERVAL_MINUTES) {
            return MIN_INTERVAL_MINUTES;
        }
        if (minutes > MAX_INTERVAL_MINUTES) {
            return MAX_INTERVAL_MINUTES;
        }
        return minutes;
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_MESSAGE_LENGTH);
        }
        return trimmed;
    }
}

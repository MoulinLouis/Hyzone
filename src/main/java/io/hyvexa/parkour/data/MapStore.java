package io.hyvexa.parkour.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.io.BlockingDiskFile;
import io.hyvexa.parkour.ParkourConstants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;

public class MapStore extends BlockingDiskFile {

    private static final String MAP_ID_PATTERN = "^[a-zA-Z0-9_-]+$";
    private static final double MAX_COORDINATE = 30000000.0;
    private final java.util.Map<String, Map> maps = new LinkedHashMap<>();

    public MapStore() {
        super(Path.of("Parkour/Maps.json"));
        migrateLegacyCoursesFile();
    }

    private void migrateLegacyCoursesFile() {
        Path newPath = Path.of("Parkour/Maps.json");
        Path legacyPath = Path.of("Parkour/Courses.json");
        if (Files.exists(newPath) || !Files.exists(legacyPath)) {
            return;
        }
        try {
            Files.copy(legacyPath, newPath);
        } catch (IOException ignored) {
        }
    }

    public boolean hasMap(String id) {
        return this.maps.containsKey(id);
    }

    public Map getMap(String id) {
        return this.maps.get(id);
    }

    public List<Map> listMaps() {
        return Collections.unmodifiableList(new ArrayList<>(this.maps.values()));
    }

    public void addMap(Map map) {
        validateMap(map);
        this.fileLock.writeLock().lock();
        try {
            this.maps.put(map.getId(), map);
        } finally {
            this.fileLock.writeLock().unlock();
        }
        this.syncSave();
    }

    public void updateMap(Map map) {
        validateMap(map);
        this.fileLock.writeLock().lock();
        try {
            this.maps.put(map.getId(), map);
        } finally {
            this.fileLock.writeLock().unlock();
        }
        this.syncSave();
    }

    public boolean removeMap(String id) {
        this.fileLock.writeLock().lock();
        boolean removed;
        try {
            removed = this.maps.remove(id) != null;
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
        this.maps.clear();
        JsonElement parsed = JsonParser.parseReader(bufferedReader);
        if (!parsed.isJsonArray()) {
            return;
        }
        for (JsonElement entry : parsed.getAsJsonArray()) {
            try {
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject object = entry.getAsJsonObject();
                Map map = new Map();
                String id = readString(object, "id", "");
                map.setId(id);
                map.setName(readString(object, "name", id));
                map.setCategory(readString(object, "category", ""));
                map.setWorld(readString(object, "world", "Unknown"));
                map.setStart(readTransform(object, "start"));
                map.setFinish(readTransform(object, "finish"));
                map.setStartTrigger(readTransform(object, "startTrigger"));
                map.setLeaveTrigger(readTransform(object, "leaveTrigger"));
                map.setLeaveTeleport(readTransform(object, "leaveTeleport"));
                map.setFirstCompletionXp(readLong(object, "firstCompletionXp",
                        ParkourConstants.XP_BONUS_FIRST_COMPLETION));
                map.setCreatedAt(readLong(object, "createdAt", 0L));
                map.setUpdatedAt(readLong(object, "updatedAt", map.getCreatedAt()));

                JsonArray checkpointsArray = object.has("checkpoints") && object.get("checkpoints").isJsonArray()
                        ? object.getAsJsonArray("checkpoints")
                        : new JsonArray();
                for (JsonElement checkpointElement : checkpointsArray) {
                    if (!checkpointElement.isJsonObject()) {
                        continue;
                    }
                    map.getCheckpoints().add(readTransform(checkpointElement.getAsJsonObject()));
                }

                if (map.getId() != null && !map.getId().isEmpty()) {
                    this.maps.put(map.getId(), map);
                }
            } catch (Exception e) {
                HytaleLogger.forEnclosingClass().at(Level.WARNING)
                        .log("Skipping invalid entry in maps data: " + e.getMessage());
            }
        }
    }

    @Override
    protected void write(BufferedWriter bufferedWriter) throws IOException {
        JsonArray array = new JsonArray();
        for (Map map : this.maps.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", map.getId());
            object.addProperty("name", map.getName());
            if (map.getCategory() != null && !map.getCategory().isEmpty()) {
                object.addProperty("category", map.getCategory());
            }
            object.addProperty("world", map.getWorld());
            object.addProperty("createdAt", map.getCreatedAt());
            object.addProperty("updatedAt", map.getUpdatedAt());
            if (map.getStart() != null) {
                object.add("start", writeTransform(map.getStart()));
            }
            if (map.getFinish() != null) {
                object.add("finish", writeTransform(map.getFinish()));
            }
            if (map.getStartTrigger() != null) {
                object.add("startTrigger", writeTransform(map.getStartTrigger()));
            }
            if (map.getLeaveTrigger() != null) {
                object.add("leaveTrigger", writeTransform(map.getLeaveTrigger()));
            }
            if (map.getLeaveTeleport() != null) {
                object.add("leaveTeleport", writeTransform(map.getLeaveTeleport()));
            }
            object.addProperty("firstCompletionXp", Math.max(0L, map.getFirstCompletionXp()));
            JsonArray checkpoints = new JsonArray();
            for (TransformData checkpoint : map.getCheckpoints()) {
                checkpoints.add(writeTransform(checkpoint));
            }
            object.add("checkpoints", checkpoints);
            array.add(object);
        }
        bufferedWriter.write(array.toString());
    }

    @Override
    protected void create(BufferedWriter bufferedWriter) throws IOException {
        bufferedWriter.write("[]");
    }

    private static String readString(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static long readLong(JsonObject object, String key, long fallback) {
        return object.has(key) ? object.get(key).getAsLong() : fallback;
    }

    private static TransformData readTransform(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        return readTransform(parent.getAsJsonObject(key));
    }

    private static TransformData readTransform(JsonObject object) {
        TransformData data = new TransformData();
        data.setX(validateCoordinate(object.has("x") ? object.get("x").getAsDouble() : 0.0));
        data.setY(validateCoordinate(object.has("y") ? object.get("y").getAsDouble() : 0.0));
        data.setZ(validateCoordinate(object.has("z") ? object.get("z").getAsDouble() : 0.0));
        data.setRotX(object.has("rotX") ? object.get("rotX").getAsFloat() : 0.0f);
        data.setRotY(object.has("rotY") ? object.get("rotY").getAsFloat() : 0.0f);
        data.setRotZ(object.has("rotZ") ? object.get("rotZ").getAsFloat() : 0.0f);
        return data;
    }

    private static JsonObject writeTransform(TransformData data) {
        JsonObject object = new JsonObject();
        object.addProperty("x", data.getX());
        object.addProperty("y", data.getY());
        object.addProperty("z", data.getZ());
        object.addProperty("rotX", data.getRotX());
        object.addProperty("rotY", data.getRotY());
        object.addProperty("rotZ", data.getRotZ());
        return object;
    }

    private static void validateMap(Map map) {
        if (map == null) {
            throw new IllegalArgumentException("Map cannot be null.");
        }
        String id = map.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Map ID cannot be empty.");
        }
        String trimmedId = id.trim();
        if (trimmedId.length() > 32) {
            throw new IllegalArgumentException("Map ID too long.");
        }
        if (!trimmedId.matches(MAP_ID_PATTERN)) {
            throw new IllegalArgumentException("Map ID can only contain letters, numbers, underscores, and hyphens.");
        }
        map.setId(trimmedId);
        String name = map.getName();
        String trimmedName = name != null ? name.trim() : "";
        if (trimmedName.isEmpty()) {
            trimmedName = trimmedId;
        }
        if (trimmedName.length() > 64) {
            throw new IllegalArgumentException("Map name too long.");
        }
        map.setName(trimmedName);
        String category = map.getCategory();
        String trimmedCategory = category != null ? category.trim() : "";
        if (!trimmedCategory.isEmpty() && trimmedCategory.length() > 32) {
            throw new IllegalArgumentException("Map category too long.");
        }
        if (!trimmedCategory.isEmpty()) {
            map.setCategory(trimmedCategory);
        }
        map.setFirstCompletionXp(Math.max(0L, map.getFirstCompletionXp()));
    }

    private static double validateCoordinate(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        if (value > MAX_COORDINATE) {
            return MAX_COORDINATE;
        }
        if (value < -MAX_COORDINATE) {
            return -MAX_COORDINATE;
        }
        return value;
    }
}

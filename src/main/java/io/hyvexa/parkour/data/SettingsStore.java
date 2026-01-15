package io.hyvexa.parkour.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.util.io.BlockingDiskFile;
import io.hyvexa.parkour.ParkourConstants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SettingsStore extends BlockingDiskFile {

    private static final double MAX_COORDINATE = 30000000.0;
    private double fallRespawnSeconds = ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS;
    private TransformData spawnPosition = defaultSpawn();
    private final List<String> categoryOrder = new ArrayList<>();
    private boolean idleFallRespawnForOp = false;

    public SettingsStore() {
        super(Path.of("Parkour/Settings.json"));
    }

    public double getFallRespawnSeconds() {
        return fallRespawnSeconds;
    }

    public void setFallRespawnSeconds(double fallRespawnSeconds) {
        this.fileLock.writeLock().lock();
        try {
            this.fallRespawnSeconds = Math.max(0.1, fallRespawnSeconds);
        } finally {
            this.fileLock.writeLock().unlock();
        }
        this.syncSave();
    }

    public TransformData getSpawnPosition() {
        return spawnPosition;
    }

    public boolean isIdleFallRespawnForOp() {
        return idleFallRespawnForOp;
    }

    public void setIdleFallRespawnForOp(boolean idleFallRespawnForOp) {
        this.fileLock.writeLock().lock();
        try {
            this.idleFallRespawnForOp = idleFallRespawnForOp;
        } finally {
            this.fileLock.writeLock().unlock();
        }
        this.syncSave();
    }

    public List<String> getCategoryOrder() {
        return List.copyOf(categoryOrder);
    }

    public void setCategoryOrder(List<String> order) {
        this.fileLock.writeLock().lock();
        try {
            categoryOrder.clear();
            if (order != null) {
                for (String entry : order) {
                    if (entry == null) {
                        continue;
                    }
                    String trimmed = entry.trim();
                    if (!trimmed.isEmpty()) {
                        categoryOrder.add(trimmed);
                    }
                }
            }
        } finally {
            this.fileLock.writeLock().unlock();
        }
        this.syncSave();
    }

    @Override
    protected void read(BufferedReader bufferedReader) throws IOException {
        JsonElement parsed = JsonParser.parseReader(bufferedReader);
        if (!parsed.isJsonObject()) {
            return;
        }
        JsonObject object = parsed.getAsJsonObject();
        fallRespawnSeconds = object.has("fallRespawnSeconds")
                ? object.get("fallRespawnSeconds").getAsDouble()
                : ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS;
        if (fallRespawnSeconds <= 0) {
            fallRespawnSeconds = ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS;
        }
        if (object.has("spawn") && object.get("spawn").isJsonObject()) {
            spawnPosition = readTransform(object.getAsJsonObject("spawn"));
        } else {
            spawnPosition = defaultSpawn();
        }
        idleFallRespawnForOp = object.has("idleFallRespawnForOp") && object.get("idleFallRespawnForOp").getAsBoolean();
        categoryOrder.clear();
        if (object.has("categoryOrder") && object.get("categoryOrder").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("categoryOrder")) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }
                String value = element.getAsString().trim();
                if (!value.isEmpty()) {
                    categoryOrder.add(value);
                }
            }
        }
    }

    @Override
    protected void write(BufferedWriter bufferedWriter) throws IOException {
        JsonObject object = new JsonObject();
        object.addProperty("fallRespawnSeconds", fallRespawnSeconds);
        if (spawnPosition != null) {
            object.add("spawn", writeTransform(spawnPosition));
        }
        object.addProperty("idleFallRespawnForOp", idleFallRespawnForOp);
        if (!categoryOrder.isEmpty()) {
            var array = new com.google.gson.JsonArray();
            for (String entry : categoryOrder) {
                array.add(entry);
            }
            object.add("categoryOrder", array);
        }
        bufferedWriter.write(object.toString());
    }

    @Override
    protected void create(BufferedWriter bufferedWriter) throws IOException {
        write(bufferedWriter);
    }

    private static TransformData defaultSpawn() {
        TransformData data = new TransformData();
        data.setX(ParkourConstants.DEFAULT_SPAWN_POSITION.getX());
        data.setY(ParkourConstants.DEFAULT_SPAWN_POSITION.getY());
        data.setZ(ParkourConstants.DEFAULT_SPAWN_POSITION.getZ());
        data.setRotX(0.0f);
        data.setRotY(0.0f);
        data.setRotZ(0.0f);
        return data;
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

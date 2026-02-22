package io.hyvexa.purge.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.purge.data.PurgeLocation;
import io.hyvexa.purge.data.PurgeMapInstance;
import io.hyvexa.purge.data.PurgeSpawnPoint;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PurgeInstanceManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Path CONFIG_PATH = Path.of("mods/Purge/purge_instances.json");
    private static final double MIN_SPAWN_DISTANCE = 15.0;

    private final ConcurrentHashMap<String, PurgeMapInstance> instancesById = new ConcurrentHashMap<>();
    private final Set<String> leasedInstanceIds = ConcurrentHashMap.newKeySet();

    public void loadConfiguredInstances() {
        instancesById.clear();
        leasedInstanceIds.clear();

        if (!Files.exists(CONFIG_PATH)) {
            LOGGER.atWarning().log("Instance config not found: " + CONFIG_PATH);
            return;
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonArray instances = root.getAsJsonArray("instances");
            if (instances == null) {
                LOGGER.atWarning().log("No 'instances' array in config");
                return;
            }

            for (JsonElement element : instances) {
                try {
                    JsonObject obj = element.getAsJsonObject();
                    PurgeMapInstance instance = parseInstance(obj);
                    instancesById.put(instance.instanceId(), instance);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to parse instance entry");
                }
            }

            LOGGER.atInfo().log("Loaded " + instancesById.size() + " purge instances");
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to read instance config: " + CONFIG_PATH);
        }
    }

    public synchronized PurgeMapInstance acquireAvailableInstance() {
        for (PurgeMapInstance instance : instancesById.values()) {
            if (!leasedInstanceIds.contains(instance.instanceId())) {
                leasedInstanceIds.add(instance.instanceId());
                return instance;
            }
        }
        return null;
    }

    public void releaseInstance(String instanceId) {
        if (instanceId != null) {
            leasedInstanceIds.remove(instanceId);
        }
    }

    public PurgeMapInstance getInstance(String instanceId) {
        return instanceId != null ? instancesById.get(instanceId) : null;
    }

    public int getAvailableCount() {
        return instancesById.size() - leasedInstanceIds.size();
    }

    /**
     * Select a spawn point using weighted random selection.
     * Farther points from the player are preferred. Points closer than 15 blocks are filtered out.
     * If all points are too close, the farthest one is returned.
     */
    public PurgeSpawnPoint selectSpawnPoint(PurgeMapInstance instance, double playerX, double playerZ) {
        if (instance == null) {
            return null;
        }
        List<PurgeSpawnPoint> all = instance.spawnPoints();
        if (all == null || all.isEmpty()) {
            return null;
        }

        // Filter points >= MIN_SPAWN_DISTANCE from player
        List<PurgeSpawnPoint> eligible = all.stream()
                .filter(p -> horizontalDistance(playerX, playerZ, p.x(), p.z()) >= MIN_SPAWN_DISTANCE)
                .toList();

        // If none pass filter, pick the farthest one
        if (eligible.isEmpty()) {
            PurgeSpawnPoint farthest = null;
            double maxDist = -1;
            for (PurgeSpawnPoint p : all) {
                double dist = horizontalDistance(playerX, playerZ, p.x(), p.z());
                if (dist > maxDist) {
                    maxDist = dist;
                    farthest = p;
                }
            }
            return farthest;
        }

        // Weighted random pick: weight = distance^2
        double totalWeight = 0;
        double[] weights = new double[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) {
            PurgeSpawnPoint p = eligible.get(i);
            double dist = horizontalDistance(playerX, playerZ, p.x(), p.z());
            weights[i] = dist * dist;
            totalWeight += weights[i];
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (int i = 0; i < eligible.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return eligible.get(i);
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    // --- Admin CRUD ---

    public List<PurgeMapInstance> getAllInstances() {
        List<PurgeMapInstance> list = new ArrayList<>(instancesById.values());
        list.sort(Comparator.comparing(PurgeMapInstance::instanceId));
        return list;
    }

    public String addInstance() {
        String id = generateNextId();
        PurgeLocation defaultLoc = new PurgeLocation(0, 80, 0, 0, 0, 0);
        PurgeMapInstance instance = new PurgeMapInstance(id, defaultLoc, defaultLoc, List.of());
        instancesById.put(id, instance);
        saveToFile();
        return id;
    }

    public boolean removeInstance(String instanceId) {
        if (instanceId == null || leasedInstanceIds.contains(instanceId)) {
            return false;
        }
        if (instancesById.remove(instanceId) != null) {
            saveToFile();
            return true;
        }
        return false;
    }

    public boolean setStartPoint(String instanceId, PurgeLocation location) {
        PurgeMapInstance old = instancesById.get(instanceId);
        if (old == null || location == null) return false;
        instancesById.put(instanceId, new PurgeMapInstance(old.instanceId(), location, old.exitPoint(), old.spawnPoints()));
        saveToFile();
        return true;
    }

    public boolean setExitPoint(String instanceId, PurgeLocation location) {
        PurgeMapInstance old = instancesById.get(instanceId);
        if (old == null || location == null) return false;
        instancesById.put(instanceId, new PurgeMapInstance(old.instanceId(), old.startPoint(), location, old.spawnPoints()));
        saveToFile();
        return true;
    }

    public boolean addSpawnPoint(String instanceId, PurgeSpawnPoint point) {
        PurgeMapInstance old = instancesById.get(instanceId);
        if (old == null || point == null) return false;
        List<PurgeSpawnPoint> newPoints = new ArrayList<>(old.spawnPoints());
        newPoints.add(point);
        instancesById.put(instanceId, new PurgeMapInstance(old.instanceId(), old.startPoint(), old.exitPoint(), List.copyOf(newPoints)));
        saveToFile();
        return true;
    }

    public boolean clearSpawnPoints(String instanceId) {
        PurgeMapInstance old = instancesById.get(instanceId);
        if (old == null) return false;
        instancesById.put(instanceId, new PurgeMapInstance(old.instanceId(), old.startPoint(), old.exitPoint(), List.of()));
        saveToFile();
        return true;
    }

    private String generateNextId() {
        int counter = 1;
        while (instancesById.containsKey("arena_" + counter)) {
            counter++;
        }
        return "arena_" + counter;
    }

    private void saveToFile() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (PurgeMapInstance inst : getAllInstances()) {
            arr.add(serializeInstance(inst));
        }
        root.add("instances", arr);
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save instance config");
        }
    }

    private static JsonObject serializeInstance(PurgeMapInstance inst) {
        JsonObject obj = new JsonObject();
        obj.addProperty("instanceId", inst.instanceId());
        obj.add("startPoint", serializeLocation(inst.startPoint()));
        obj.add("exitPoint", serializeLocation(inst.exitPoint()));
        JsonArray spawns = new JsonArray();
        for (PurgeSpawnPoint sp : inst.spawnPoints()) {
            JsonObject spObj = new JsonObject();
            spObj.addProperty("x", sp.x());
            spObj.addProperty("y", sp.y());
            spObj.addProperty("z", sp.z());
            spObj.addProperty("yaw", sp.yaw());
            spawns.add(spObj);
        }
        obj.add("spawnPoints", spawns);
        return obj;
    }

    private static JsonObject serializeLocation(PurgeLocation loc) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", loc.x());
        obj.addProperty("y", loc.y());
        obj.addProperty("z", loc.z());
        obj.addProperty("rotX", loc.rotX());
        obj.addProperty("rotY", loc.rotY());
        obj.addProperty("rotZ", loc.rotZ());
        return obj;
    }

    private static double horizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static PurgeMapInstance parseInstance(JsonObject obj) {
        String instanceId = obj.get("instanceId").getAsString();
        PurgeLocation startPoint = parseLocation(obj.getAsJsonObject("startPoint"));
        PurgeLocation exitPoint = parseLocation(obj.getAsJsonObject("exitPoint"));

        List<PurgeSpawnPoint> spawnPoints = new ArrayList<>();
        JsonArray spawns = obj.getAsJsonArray("spawnPoints");
        if (spawns != null) {
            int id = 0;
            for (JsonElement sp : spawns) {
                JsonObject spObj = sp.getAsJsonObject();
                spawnPoints.add(new PurgeSpawnPoint(
                        id++,
                        spObj.get("x").getAsDouble(),
                        spObj.get("y").getAsDouble(),
                        spObj.get("z").getAsDouble(),
                        spObj.has("yaw") ? spObj.get("yaw").getAsFloat() : 0f
                ));
            }
        }

        return new PurgeMapInstance(instanceId, startPoint, exitPoint, List.copyOf(spawnPoints));
    }

    private static PurgeLocation parseLocation(JsonObject obj) {
        if (obj == null) {
            return new PurgeLocation(0, 0, 0, 0, 0, 0);
        }
        return new PurgeLocation(
                obj.get("x").getAsDouble(),
                obj.get("y").getAsDouble(),
                obj.get("z").getAsDouble(),
                obj.has("rotX") ? obj.get("rotX").getAsFloat() : 0f,
                obj.has("rotY") ? obj.get("rotY").getAsFloat() : 0f,
                obj.has("rotZ") ? obj.get("rotZ").getAsFloat() : 0f
        );
    }
}

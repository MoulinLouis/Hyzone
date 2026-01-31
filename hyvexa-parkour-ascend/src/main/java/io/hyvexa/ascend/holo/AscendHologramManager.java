package io.hyvexa.ascend.holo;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.common.util.HylogramsBridge;

import java.util.ArrayList;
import java.util.List;
public class AscendHologramManager {

    private static final String MAP_INFO_PREFIX = "ascend_map_";

    public String getMapInfoName(String mapId) {
        return MAP_INFO_PREFIX + mapId;
    }

    public boolean refreshMapHolosIfPresent(AscendMap map, Store<EntityStore> store) {
        if (!HylogramsBridge.isAvailable()) {
            return false;
        }
        if (map == null || map.getId() == null || map.getId().isBlank()) {
            return false;
        }
        boolean updated = false;
        String infoName = getMapInfoName(map.getId());
        if (HylogramsBridge.exists(infoName)) {
            HylogramsBridge.updateHologramLines(infoName, buildMapInfoLines(map), store);
            updated = true;
        }
        return updated;
    }

    public boolean createOrUpdateMapInfoHolo(AscendMap map, Store<EntityStore> store,
                                             Vector3d position, String worldName) {
        if (!HylogramsBridge.isAvailable()) {
            return false;
        }
        if (map == null || map.getId() == null || map.getId().isBlank()) {
            return false;
        }
        String name = getMapInfoName(map.getId());
        return createOrUpdateHolo(name, buildMapInfoLines(map), store, position, worldName);
    }

    public boolean deleteMapInfoHolo(String mapId, Store<EntityStore> store) {
        if (!HylogramsBridge.isAvailable()) {
            return false;
        }
        if (mapId == null || mapId.isBlank()) {
            return false;
        }
        String infoName = getMapInfoName(mapId);
        if (!HylogramsBridge.exists(infoName)) {
            return false;
        }
        return HylogramsBridge.delete(infoName, store);
    }

    private boolean createOrUpdateHolo(String name, List<String> lines, Store<EntityStore> store,
                                       Vector3d position, String worldName) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (HylogramsBridge.exists(name)) {
            HylogramsBridge.Hologram holo = HylogramsBridge.get(name);
            if (holo != null && position != null) {
                holo.moveTo(position);
                if (store != null) {
                    holo.respawn(store).save(store);
                }
            }
            HylogramsBridge.updateHologramLines(name, lines, store);
            return true;
        }
        HylogramsBridge.HologramBuilder builder = HylogramsBridge.create(name, store);
        if (builder == null) {
            return false;
        }
        if (worldName != null && !worldName.isBlank()) {
            builder.inWorld(worldName);
        }
        if (position != null) {
            builder.at(position);
        }
        for (String line : lines) {
            builder.addLine(line);
        }
        HylogramsBridge.Hologram holo = builder.spawn();
        if (holo != null && store != null) {
            holo.save(store);
        }
        return holo != null;
    }

    private List<String> buildMapInfoLines(AscendMap map) {
        List<String> lines = new ArrayList<>();
        String name = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
        lines.add("ASCEND MAP");
        lines.add(name);
        lines.add("Reward: " + map.getBaseReward() + " coins");
        if (map.getPrice() > 0) {
            lines.add("Unlock: " + map.getPrice() + " coins");
        } else {
            lines.add("Unlocked");
        }
        lines.add("Use /ascend to run");
        return lines;
    }
}

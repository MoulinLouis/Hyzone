package io.hyvexa.parkour.data;

import io.hyvexa.parkour.ParkourConstants;
import java.util.ArrayList;
import java.util.List;

public class Map {
    private String id;
    private String name;
    private String category;
    private String world;
    private TransformData start;
    private TransformData finish;
    private TransformData startTrigger;
    private TransformData leaveTrigger;
    private TransformData leaveTeleport;
    private final List<TransformData> checkpoints = new ArrayList<>();
    private long firstCompletionXp;
    private int difficulty;
    private int order = ParkourConstants.DEFAULT_MAP_ORDER;
    private boolean mithrilSwordEnabled;
    private boolean mithrilDaggersEnabled;
    private boolean gliderEnabled;
    private boolean freeFallEnabled;
    private boolean duelEnabled;
    private Double flyZoneMinX;
    private Double flyZoneMinY;
    private Double flyZoneMinZ;
    private Double flyZoneMaxX;
    private Double flyZoneMaxY;
    private Double flyZoneMaxZ;
    private long createdAt;
    private long updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public TransformData getStart() {
        return start;
    }

    public void setStart(TransformData start) {
        this.start = start;
    }

    public TransformData getFinish() {
        return finish;
    }

    public void setFinish(TransformData finish) {
        this.finish = finish;
    }

    public TransformData getStartTrigger() {
        return startTrigger;
    }

    public void setStartTrigger(TransformData startTrigger) {
        this.startTrigger = startTrigger;
    }

    public TransformData getLeaveTrigger() {
        return leaveTrigger;
    }

    public void setLeaveTrigger(TransformData leaveTrigger) {
        this.leaveTrigger = leaveTrigger;
    }

    public TransformData getLeaveTeleport() {
        return leaveTeleport;
    }

    public void setLeaveTeleport(TransformData leaveTeleport) {
        this.leaveTeleport = leaveTeleport;
    }

    public List<TransformData> getCheckpoints() {
        return checkpoints;
    }

    public long getFirstCompletionXp() {
        return firstCompletionXp;
    }

    public void setFirstCompletionXp(long firstCompletionXp) {
        this.firstCompletionXp = firstCompletionXp;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isMithrilSwordEnabled() {
        return mithrilSwordEnabled;
    }

    public void setMithrilSwordEnabled(boolean mithrilSwordEnabled) {
        this.mithrilSwordEnabled = mithrilSwordEnabled;
    }

    public boolean isMithrilDaggersEnabled() {
        return mithrilDaggersEnabled;
    }

    public void setMithrilDaggersEnabled(boolean mithrilDaggersEnabled) {
        this.mithrilDaggersEnabled = mithrilDaggersEnabled;
    }

    public boolean isGliderEnabled() {
        return gliderEnabled;
    }

    public void setGliderEnabled(boolean gliderEnabled) {
        this.gliderEnabled = gliderEnabled;
    }

    public boolean isFreeFallEnabled() {
        return freeFallEnabled;
    }

    public void setFreeFallEnabled(boolean freeFallEnabled) {
        this.freeFallEnabled = freeFallEnabled;
    }

    public boolean isDuelEnabled() {
        return duelEnabled;
    }

    public void setDuelEnabled(boolean duelEnabled) {
        this.duelEnabled = duelEnabled;
    }

    public Double getFlyZoneMinX() {
        return flyZoneMinX;
    }

    public void setFlyZoneMinX(Double flyZoneMinX) {
        this.flyZoneMinX = flyZoneMinX;
    }

    public Double getFlyZoneMinY() {
        return flyZoneMinY;
    }

    public void setFlyZoneMinY(Double flyZoneMinY) {
        this.flyZoneMinY = flyZoneMinY;
    }

    public Double getFlyZoneMinZ() {
        return flyZoneMinZ;
    }

    public void setFlyZoneMinZ(Double flyZoneMinZ) {
        this.flyZoneMinZ = flyZoneMinZ;
    }

    public Double getFlyZoneMaxX() {
        return flyZoneMaxX;
    }

    public void setFlyZoneMaxX(Double flyZoneMaxX) {
        this.flyZoneMaxX = flyZoneMaxX;
    }

    public Double getFlyZoneMaxY() {
        return flyZoneMaxY;
    }

    public void setFlyZoneMaxY(Double flyZoneMaxY) {
        this.flyZoneMaxY = flyZoneMaxY;
    }

    public Double getFlyZoneMaxZ() {
        return flyZoneMaxZ;
    }

    public void setFlyZoneMaxZ(Double flyZoneMaxZ) {
        this.flyZoneMaxZ = flyZoneMaxZ;
    }

    public boolean hasFlyZone() {
        return flyZoneMinX != null && flyZoneMinY != null && flyZoneMinZ != null
            && flyZoneMaxX != null && flyZoneMaxY != null && flyZoneMaxZ != null;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}

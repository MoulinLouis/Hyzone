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

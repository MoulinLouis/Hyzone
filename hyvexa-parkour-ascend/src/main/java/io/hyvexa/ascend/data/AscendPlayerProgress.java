package io.hyvexa.ascend.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AscendPlayerProgress {

    private long coins;
    private int rebirthMultiplier = 1;
    private final Map<String, MapProgress> mapProgress = new ConcurrentHashMap<>();

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = coins;
    }

    public void addCoins(long amount) {
        this.coins = Math.max(0, this.coins + amount);
    }

    public int getRebirthMultiplier() {
        return rebirthMultiplier;
    }

    public void setRebirthMultiplier(int rebirthMultiplier) {
        this.rebirthMultiplier = Math.max(1, rebirthMultiplier);
    }

    public int addRebirthMultiplier(int amount) {
        rebirthMultiplier = Math.max(1, rebirthMultiplier + amount);
        return rebirthMultiplier;
    }

    public Map<String, MapProgress> getMapProgress() {
        return mapProgress;
    }

    public MapProgress getOrCreateMapProgress(String mapId) {
        return mapProgress.computeIfAbsent(mapId, k -> new MapProgress());
    }

    public static class MapProgress {
        private boolean unlocked;
        private boolean completedManually;
        private boolean hasRobot;
        private int robotCount;
        private int robotSpeedLevel;
        private int robotGainsLevel;
        private int multiplierValue = 1;

        public boolean isUnlocked() {
            return unlocked;
        }

        public void setUnlocked(boolean unlocked) {
            this.unlocked = unlocked;
        }

        public boolean isCompletedManually() {
            return completedManually;
        }

        public void setCompletedManually(boolean completedManually) {
            this.completedManually = completedManually;
        }

        public boolean hasRobot() {
            return hasRobot;
        }

        public void setHasRobot(boolean hasRobot) {
            this.hasRobot = hasRobot;
            if (hasRobot && robotCount <= 0) {
                robotCount = 1;
            }
        }

        public int getRobotCount() {
            return robotCount;
        }

        public void setRobotCount(int robotCount) {
            this.robotCount = Math.max(0, robotCount);
            this.hasRobot = this.robotCount > 0;
        }

        public int addRobotCount(int amount) {
            robotCount = Math.max(0, robotCount + amount);
            hasRobot = robotCount > 0;
            return robotCount;
        }

        public int getRobotSpeedLevel() {
            return robotSpeedLevel;
        }

        public void setRobotSpeedLevel(int robotSpeedLevel) {
            this.robotSpeedLevel = robotSpeedLevel;
        }

        public int getRobotGainsLevel() {
            return robotGainsLevel;
        }

        public void setRobotGainsLevel(int robotGainsLevel) {
            this.robotGainsLevel = robotGainsLevel;
        }

        public int getMultiplierValue() {
            return multiplierValue;
        }

        public void setMultiplierValue(int multiplierValue) {
            this.multiplierValue = Math.max(1, multiplierValue);
        }

        public int incrementMultiplier() {
            multiplierValue = Math.max(1, multiplierValue) + 1;
            return multiplierValue;
        }
    }
}

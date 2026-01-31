package io.hyvexa.ascend.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AscendPlayerProgress {

    private long coins;
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

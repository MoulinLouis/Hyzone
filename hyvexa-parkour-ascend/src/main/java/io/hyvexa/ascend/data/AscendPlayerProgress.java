package io.hyvexa.ascend.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AscendPlayerProgress {

    private long coins;
    private int elevationMultiplier = 1;
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

    public int getElevationMultiplier() {
        return elevationMultiplier;
    }

    public void setElevationMultiplier(int elevationMultiplier) {
        this.elevationMultiplier = Math.max(1, elevationMultiplier);
    }

    public int addElevationMultiplier(int amount) {
        elevationMultiplier = Math.max(1, elevationMultiplier + amount);
        return elevationMultiplier;
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
        private double multiplier = 1.0;

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
            this.robotSpeedLevel = Math.max(0, robotSpeedLevel);
        }

        public int incrementRobotSpeedLevel() {
            robotSpeedLevel = Math.max(0, robotSpeedLevel) + 1;
            return robotSpeedLevel;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = Math.max(1.0, multiplier);
        }

        public double addMultiplier(double amount) {
            if (amount <= 0.0) {
                return multiplier;
            }
            multiplier = Math.max(1.0, multiplier + amount);
            return multiplier;
        }
    }
}

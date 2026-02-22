package io.hyvexa.runorfall.data;

import java.util.ArrayList;
import java.util.List;

public class RunOrFallConfig {
    public String selectedMapId = "";
    public double voidY = 40.0;
    public double blockBreakDelaySeconds = 0.2;
    public int minPlayers = 2;
    public int minPlayersTimeSeconds = 300;
    public int optimalPlayers = 4;
    public int optimalPlayersTimeSeconds = 60;
    public List<RunOrFallMapConfig> maps = new ArrayList<>();

    // Legacy fields (single-map format) kept for migration from older JSON configs.
    public RunOrFallLocation lobby;
    public List<RunOrFallLocation> spawns = new ArrayList<>();
    public List<RunOrFallPlatform> platforms = new ArrayList<>();

    public RunOrFallConfig copy() {
        RunOrFallConfig copy = new RunOrFallConfig();
        copy.selectedMapId = selectedMapId;
        copy.voidY = voidY;
        copy.blockBreakDelaySeconds = blockBreakDelaySeconds;
        copy.minPlayers = minPlayers;
        copy.minPlayersTimeSeconds = minPlayersTimeSeconds;
        copy.optimalPlayers = optimalPlayers;
        copy.optimalPlayersTimeSeconds = optimalPlayersTimeSeconds;
        if (maps != null) {
            for (RunOrFallMapConfig map : maps) {
                if (map != null) {
                    copy.maps.add(map.copy());
                }
            }
        }
        return copy;
    }
}

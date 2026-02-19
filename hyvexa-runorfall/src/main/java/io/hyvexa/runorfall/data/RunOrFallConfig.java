package io.hyvexa.runorfall.data;

import java.util.ArrayList;
import java.util.List;

public class RunOrFallConfig {
    public RunOrFallLocation lobby;
    public double voidY = 40.0;
    public double blockBreakDelaySeconds = 0.2;
    public List<RunOrFallLocation> spawns = new ArrayList<>();
    public List<RunOrFallPlatform> platforms = new ArrayList<>();

    public RunOrFallConfig copy() {
        RunOrFallConfig copy = new RunOrFallConfig();
        copy.lobby = lobby != null ? lobby.copy() : null;
        copy.voidY = voidY;
        copy.blockBreakDelaySeconds = blockBreakDelaySeconds;
        if (spawns != null) {
            for (RunOrFallLocation spawn : spawns) {
                if (spawn != null) {
                    copy.spawns.add(spawn.copy());
                }
            }
        }
        if (platforms != null) {
            for (RunOrFallPlatform platform : platforms) {
                if (platform != null) {
                    copy.platforms.add(platform.copy());
                }
            }
        }
        return copy;
    }
}

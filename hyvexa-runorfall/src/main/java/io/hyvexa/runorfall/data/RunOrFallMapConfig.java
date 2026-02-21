package io.hyvexa.runorfall.data;

import java.util.ArrayList;
import java.util.List;

public class RunOrFallMapConfig {
    public String id;
    public RunOrFallLocation lobby;
    public List<RunOrFallLocation> spawns = new ArrayList<>();
    public List<RunOrFallPlatform> platforms = new ArrayList<>();

    public RunOrFallMapConfig copy() {
        RunOrFallMapConfig copy = new RunOrFallMapConfig();
        copy.id = id;
        copy.lobby = lobby != null ? lobby.copy() : null;
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


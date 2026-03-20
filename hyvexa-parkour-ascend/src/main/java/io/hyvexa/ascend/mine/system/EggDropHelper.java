package io.hyvexa.ascend.mine.system;

import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.achievement.MineAchievement;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.data.MineZoneLayer;
import io.hyvexa.ascend.mine.hud.MineHudManager;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class EggDropHelper {

    private EggDropHelper() {}

    public static void tryDropEgg(UUID playerId, MineZone zone, int blockY,
                                   MinePlayerProgress progress, MinePlayerStore store) {
        MineZoneLayer layer = zone.getLayerForY(blockY);
        if (layer == null) return;

        if (ThreadLocalRandom.current().nextDouble() >= layer.getEggDropChance()) return;

        progress.addEgg(layer.getId());
        store.markDirty(playerId);

        // Toast notification + achievement
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            MineHudManager hudManager = plugin.getMineHudManager();
            if (hudManager != null) {
                hudManager.showMineToast(playerId, "Egg", 1);
            }
        }

        if (plugin != null) {
            MineAchievementTracker tracker = plugin.getMineAchievementTracker();
            if (tracker != null) {
                tracker.checkAchievement(playerId, MineAchievement.FIRST_EGG);
            }
        }
    }
}

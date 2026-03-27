package io.hyvexa.purge.mission;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.data.PurgeScrapStore;

import java.util.List;
import java.util.UUID;

public class PurgeMissionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PurgeMissionStore missionStore;
    private final PurgeScrapStore scrapStore;

    public PurgeMissionManager(PurgeMissionStore missionStore, PurgeScrapStore scrapStore) {
        this.missionStore = missionStore;
        this.scrapStore = scrapStore;
    }

    public void recordSessionResult(UUID playerId, int waveReached, int kills, int bestCombo) {
        if (playerId == null) {
            return;
        }
        missionStore.updateAfterSession(playerId, kills, waveReached, bestCombo);
        checkAndClaimMissions(playerId);
    }

    public MissionStatus[] getMissionStatus(UUID playerId) {
        List<MissionDefinition> missions = DailyMissionRotation.getTodaysMissions();
        DailyMissionProgress progress = missionStore.getProgress(playerId);
        MissionStatus[] statuses = new MissionStatus[3];
        for (int i = 0; i < missions.size(); i++) {
            MissionDefinition mission = missions.get(i);
            int current = progress.getProgressValue(mission.category());
            boolean claimed = progress.isClaimed(mission.category());
            statuses[i] = new MissionStatus(mission, current, claimed);
        }
        return statuses;
    }

    private void checkAndClaimMissions(UUID playerId) {
        List<MissionDefinition> missions = DailyMissionRotation.getTodaysMissions();
        DailyMissionProgress progress = missionStore.getProgress(playerId);
        for (MissionDefinition mission : missions) {
            if (progress.isClaimed(mission.category())) {
                continue;
            }
            int current = progress.getProgressValue(mission.category());
            if (current >= mission.target()) {
                missionStore.markClaimed(playerId, mission.category());
                scrapStore.addScrap(playerId, mission.scrapReward());
                sendMissionCompleteMessage(playerId, mission);
            }
        }
    }

    private void sendMissionCompleteMessage(UUID playerId, MissionDefinition mission) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null || !playerRef.isValid()) {
                return;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw("Mission complete: " + mission.description()
                        + " -> +" + mission.scrapReward() + " scrap!"));
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to send mission complete message to " + playerId);
        }
    }

    public record MissionStatus(MissionDefinition mission, int currentProgress, boolean claimed) {}
}

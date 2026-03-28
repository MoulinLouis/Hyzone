package io.hyvexa.ascend.mine.quest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineQuestProgress {

    private final UUID playerId;
    private final Map<String, Integer> chainProgress = new ConcurrentHashMap<>();
    private final Map<String, Long> objectiveProgress = new ConcurrentHashMap<>();

    public MineQuestProgress(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }

    public int getQuestIndex(String chain) {
        return chainProgress.getOrDefault(chain, 0);
    }

    public void setQuestIndex(String chain, int index) {
        chainProgress.put(chain, index);
    }

    public long getObjectiveProgress(String chain) {
        return objectiveProgress.getOrDefault(chain, 0L);
    }

    public void setObjectiveProgress(String chain, long progress) {
        objectiveProgress.put(chain, progress);
    }

    public void addObjectiveProgress(String chain, long amount) {
        objectiveProgress.merge(chain, amount, Long::sum);
    }

    /**
     * Returns the active quest for the given chain, or null if chain is complete.
     */
    public MineQuest getActiveQuest(String chain) {
        int index = getQuestIndex(chain);
        return MineQuest.getByChainAndIndex(chain, index);
    }

    public boolean isChainComplete(String chain) {
        return getQuestIndex(chain) >= MineQuest.getChainLength(chain);
    }

    public boolean isReadyToTurnIn(String chain) {
        MineQuest quest = getActiveQuest(chain);
        if (quest == null) return false;
        return getObjectiveProgress(chain) >= quest.getTarget();
    }

    /**
     * Advances to the next quest in the chain and resets objective progress.
     */
    public void advanceQuest(String chain) {
        int current = getQuestIndex(chain);
        chainProgress.put(chain, current + 1);
        objectiveProgress.put(chain, 0L);
    }

    public void resetAll() {
        chainProgress.clear();
        objectiveProgress.clear();
    }
}

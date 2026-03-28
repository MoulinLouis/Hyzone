package io.hyvexa.ascend.mine.quest;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.data.MinerRarity;
import io.hyvexa.ascend.mine.hud.MineHudManager;

import java.util.UUID;

/**
 * Central game logic for the mine quest system.
 * Tracks objective progress, handles NPC turn-in, and updates the HUD.
 */
public class MineQuestManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String DEFAULT_CHAIN = "miner";

    private final MineQuestStore questStore;
    private final MinePlayerStore minePlayerStore;
    private final MineHudManager mineHudManager;
    private final MineQuestDialogueManager dialogueManager;

    public MineQuestManager(MineQuestStore questStore, MinePlayerStore minePlayerStore,
                            MineHudManager mineHudManager, MineQuestDialogueManager dialogueManager) {
        this.questStore = questStore;
        this.minePlayerStore = minePlayerStore;
        this.mineHudManager = mineHudManager;
        this.dialogueManager = dialogueManager;
    }

    // ── Event methods ──────────────────────────────────────────────────

    public void onBlocksMined(UUID playerId, int count) {
        incrementIfMatches(playerId, MineQuestObjectiveType.MINE_BLOCKS, null, count);
    }

    public void onBlocksSold(UUID playerId, int count) {
        incrementIfMatches(playerId, MineQuestObjectiveType.SELL_BLOCKS, null, count);
    }

    public void onCrystalsEarned(UUID playerId, long amount) {
        incrementIfMatches(playerId, MineQuestObjectiveType.EARN_CRYSTALS, null, amount);
    }

    public void onUpgradePurchased(UUID playerId, MineUpgradeType type, int newLevel) {
        MineQuestProgress progress = questStore.getProgress(playerId);
        if (progress == null) return;

        MineQuest quest = progress.getActiveQuest(DEFAULT_CHAIN);
        if (quest == null) return;
        if (quest.getObjectiveType() != MineQuestObjectiveType.REACH_UPGRADE_LEVEL) return;

        // Check if this upgrade matches the quest param
        if (quest.getParam() != null && quest.getParam().equals(type.name())) {
            progress.setObjectiveProgress(DEFAULT_CHAIN, newLevel);
            questStore.markDirty(playerId);
            checkAndNotify(playerId, progress);
        }
    }

    public void onPickaxeUpgraded(UUID playerId, int newTier) {
        MineQuestProgress progress = questStore.getProgress(playerId);
        if (progress == null) return;

        MineQuest quest = progress.getActiveQuest(DEFAULT_CHAIN);
        if (quest == null) return;
        if (quest.getObjectiveType() != MineQuestObjectiveType.UPGRADE_PICKAXE_TIER) return;

        progress.setObjectiveProgress(DEFAULT_CHAIN, newTier);
        questStore.markDirty(playerId);
        checkAndNotify(playerId, progress);
    }

    public void onEggOpened(UUID playerId) {
        incrementIfMatches(playerId, MineQuestObjectiveType.OPEN_EGGS, null, 1);
    }

    public void onMinerCollected(UUID playerId, MinerRarity rarity) {
        MineQuestProgress progress = questStore.getProgress(playerId);
        if (progress == null) return;

        MineQuest quest = progress.getActiveQuest(DEFAULT_CHAIN);
        if (quest == null) return;
        if (quest.getObjectiveType() != MineQuestObjectiveType.COLLECT_MINER) return;

        // param null = any rarity, otherwise must match
        if (quest.getParam() != null && !quest.getParam().equals(rarity.name())) return;

        progress.addObjectiveProgress(DEFAULT_CHAIN, 1);
        questStore.markDirty(playerId);
        checkAndNotify(playerId, progress);
    }

    // ── NPC interaction ────────────────────────────────────────────────

    /**
     * Handles NPC right-click for the quest chain.
     */
    public void handleNpcInteraction(UUID playerId, String chain, Player player) {
        // If dialogue session active, advance it
        if (dialogueManager.hasActiveSession(playerId)) {
            boolean ended = dialogueManager.advanceLine(playerId);
            if (ended) {
                onDialogueFinished(playerId, chain, player);
            }
            return;
        }

        MineQuestProgress progress = questStore.getOrLoad(playerId);

        // Chain complete
        if (progress.isChainComplete(chain)) {
            player.sendMessage(Message.raw("No more quests for now. Check back later!"));
            return;
        }

        MineQuest quest = progress.getActiveQuest(chain);
        if (quest == null) return;

        if (progress.isReadyToTurnIn(chain)) {
            // Start completion dialogue
            dialogueManager.startCompleteDialogue(playerId, quest);
        } else if (progress.getQuestIndex(chain) == 0 && progress.getObjectiveProgress(chain) == 0) {
            // First interaction — start give dialogue for first quest
            dialogueManager.startGiveDialogue(playerId, quest);
        } else {
            // In progress — show objective reminder in dialogue box (auto-fades)
            long current = progress.getObjectiveProgress(chain);
            long target = quest.getTarget();
            dialogueManager.showObjectiveReminder(playerId, quest, current, target);
        }
    }

    /**
     * Called when a dialogue sequence finishes (player clicked through all lines).
     */
    private void onDialogueFinished(UUID playerId, String chain, Player player) {
        MineQuestProgress progress = questStore.getOrLoad(playerId);
        MineQuest quest = progress.getActiveQuest(chain);
        if (quest == null) return;

        MineQuestDialogueManager.DialogueType type = dialogueManager.getLastDialogueType(playerId);
        dialogueManager.clearLastType(playerId);

        if (type == MineQuestDialogueManager.DialogueType.COMPLETE && progress.isReadyToTurnIn(chain)) {
            // Grant reward
            MinePlayerProgress mineProgress = minePlayerStore.getOrCreatePlayer(playerId);
            mineProgress.addCrystals(quest.getCrystalReward());
            minePlayerStore.markDirty(playerId);

            player.sendMessage(Message.raw("+" + quest.getCrystalReward() + " crystals!"));

            // Advance to next quest
            progress.advanceQuest(chain);
            questStore.markDirty(playerId);

            // Pre-fill next quest if applicable
            MineQuest nextQuest = progress.getActiveQuest(chain);
            if (nextQuest != null) {
                prefillProgress(playerId, progress, nextQuest, chain);
            }

            // Update HUD
            updateQuestHud(playerId, progress, chain);
        } else if (type == MineQuestDialogueManager.DialogueType.GIVE) {
            // Quest accepted — pre-fill progress and update HUD
            prefillProgress(playerId, progress, quest, chain);
            updateQuestHud(playerId, progress, chain);
        }
    }

    // ── Pre-fill ───────────────────────────────────────────────────────

    /**
     * Seeds initial progress for state-based objectives to avoid requiring regression.
     */
    private void prefillProgress(UUID playerId, MineQuestProgress progress, MineQuest quest, String chain) {
        MinePlayerProgress mineProgress = minePlayerStore.getPlayer(playerId);
        if (mineProgress == null) return;

        switch (quest.getObjectiveType()) {
            case REACH_UPGRADE_LEVEL -> {
                if (quest.getParam() != null) {
                    try {
                        MineUpgradeType type = MineUpgradeType.valueOf(quest.getParam());
                        int currentLevel = mineProgress.getUpgradeLevel(type);
                        if (currentLevel > 0) {
                            progress.setObjectiveProgress(chain, currentLevel);
                            questStore.markDirty(playerId);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            case UPGRADE_PICKAXE_TIER -> {
                int currentTier = mineProgress.getPickaxeTier();
                if (currentTier > 0) {
                    progress.setObjectiveProgress(chain, currentTier);
                    questStore.markDirty(playerId);
                }
            }
            default -> {}
        }
    }

    // ── Player lifecycle ───────────────────────────────────────────────

    public void onPlayerJoin(UUID playerId) {
        MineQuestProgress progress = questStore.getOrLoad(playerId);
        updateQuestHud(playerId, progress, DEFAULT_CHAIN);
    }

    public void resetQuests(UUID playerId) {
        MineQuestProgress progress = questStore.getOrLoad(playerId);
        progress.resetAll();
        questStore.markDirty(playerId);
        dialogueManager.endDialogue(playerId);
        updateQuestHud(playerId, progress, DEFAULT_CHAIN);
    }

    public void evict(UUID playerId) {
        dialogueManager.endDialogue(playerId);
        questStore.evict(playerId);
    }

    public void flushAll() {
        questStore.flushAll();
    }

    public void tickDialogue(UUID playerId) {
        dialogueManager.tick(playerId);
    }

    public MineQuestStore getStore() {
        return questStore;
    }

    // ── Internals ──────────────────────────────────────────────────────

    private void incrementIfMatches(UUID playerId, MineQuestObjectiveType type, String param, long amount) {
        MineQuestProgress progress = questStore.getProgress(playerId);
        if (progress == null) return;

        MineQuest quest = progress.getActiveQuest(DEFAULT_CHAIN);
        if (quest == null) return;
        if (quest.getObjectiveType() != type) return;
        if (param != null && !param.equals(quest.getParam())) return;

        progress.addObjectiveProgress(DEFAULT_CHAIN, amount);
        questStore.markDirty(playerId);
        checkAndNotify(playerId, progress);
    }

    private void checkAndNotify(UUID playerId, MineQuestProgress progress) {
        updateQuestHud(playerId, progress, DEFAULT_CHAIN);
    }

    private void updateQuestHud(UUID playerId, MineQuestProgress progress, String chain) {
        if (mineHudManager == null) return;

        if (progress.isChainComplete(chain)) {
            mineHudManager.hideQuestTracker(playerId);
            return;
        }

        MineQuest quest = progress.getActiveQuest(chain);
        if (quest == null) {
            mineHudManager.hideQuestTracker(playerId);
            return;
        }

        long current = progress.getObjectiveProgress(chain);
        long target = quest.getTarget();

        if (progress.isReadyToTurnIn(chain)) {
            mineHudManager.showQuestTurnIn(playerId, quest.getTitle());
        } else {
            mineHudManager.updateQuestTracker(playerId, quest.getTitle(), quest.getObjectiveText(), current, target);
        }
    }
}

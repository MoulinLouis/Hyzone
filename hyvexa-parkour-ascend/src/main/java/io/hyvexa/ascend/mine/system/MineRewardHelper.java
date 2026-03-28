package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.BlockConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.ascend.mine.quest.MineQuestManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MineRewardHelper {

    private MineRewardHelper() {}

    public static int rollFortune(int fortuneLevel) {
        if (fortuneLevel <= 0) return 1;
        double tripleChance = fortuneLevel * 0.4 / 100.0;
        double doubleChance = fortuneLevel * 2.0 / 100.0;
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < tripleChance) return 3;
        if (roll < tripleChance + doubleChance) return 2;
        return 1;
    }

    static double calculateCashbackAmount(long blockPrice, int blocksGained, double cashbackPercent) {
        if (blockPrice <= 0 || blocksGained <= 0 || cashbackPercent <= 0.0) {
            return 0.0;
        }
        return BigDecimal.valueOf(blockPrice)
                .multiply(BigDecimal.valueOf(blocksGained))
                .multiply(BigDecimal.valueOf(cashbackPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .doubleValue();
    }

    /**
     * Adds blocks to inventory, auto-sells overflow, shows toast, tracks achievements, and marks dirty.
     * Returns true if the bag was completely full (stored == 0).
     */
    public static boolean rewardBlock(UUID playerId, MinePlayerProgress mineProgress, String blockTypeName,
                                       int blocksGained, String mineId, MineManager mineManager,
                                       MinePlayerStore minePlayerStore, MineHudManager mineHudManager,
                                       MineAchievementTracker achievementTracker) {
        return rewardBlock(playerId, mineProgress, blockTypeName, blocksGained, mineId,
            mineManager, minePlayerStore, mineHudManager, achievementTracker, null);
    }

    public static boolean rewardBlock(UUID playerId, MinePlayerProgress mineProgress, String blockTypeName,
                                       int blocksGained, String mineId, MineManager mineManager,
                                       MinePlayerStore minePlayerStore, MineHudManager mineHudManager,
                                       MineAchievementTracker achievementTracker, MineQuestManager questManager) {
        int stored = mineProgress.addToInventoryUpTo(blockTypeName, blocksGained);
        boolean bagFull = false;
        if (stored < blocksGained) {
            BlockConfigStore configStore = mineManager.getBlockConfigStore();
            long blockPrice = configStore.getBlockPrice(blockTypeName);
            int overflow = blocksGained - stored;
            long fallbackCrystals = blockPrice * overflow;
            mineProgress.addCrystals(fallbackCrystals);
            if (stored == 0) {
                bagFull = true;
            }
            // Quest: auto-sold overflow
            if (questManager != null && overflow > 0) {
                questManager.onBlocksSold(playerId, overflow);
                if (fallbackCrystals > 0) {
                    questManager.onCrystalsEarned(playerId, fallbackCrystals);
                }
            }
        }
        int cashbackLevel = mineProgress.getUpgradeLevel(MineUpgradeType.CASHBACK);
        if (cashbackLevel > 0) {
            BlockConfigStore configStore = mineManager.getBlockConfigStore();
            long blockPrice = configStore.getBlockPrice(blockTypeName);
            double cashbackPercent = MineUpgradeType.CASHBACK.getEffect(cashbackLevel);
            double cashbackAmount = calculateCashbackAmount(blockPrice, blocksGained, cashbackPercent);
            if (cashbackAmount > 0) {
                mineProgress.addCrystals(cashbackAmount);
            }
        }

        minePlayerStore.markDirty(playerId);

        if (mineHudManager != null) {
            mineHudManager.showMineToast(playerId, blockTypeName, blocksGained);
        }

        if (achievementTracker != null) {
            achievementTracker.incrementBlocksMined(playerId, blocksGained);
            achievementTracker.incrementManualBlocksMined(playerId, blocksGained);
        }

        return bagFull;
    }

    public static void handleMomentumCombo(UUID playerId, MinePlayerProgress mineProgress,
                                           MineHudManager mineHudManager) {
        int momentumLevel = mineProgress.getUpgradeLevel(MineUpgradeType.MOMENTUM);
        if (momentumLevel > 0) {
            mineProgress.checkComboExpired();
            mineProgress.incrementCombo();
            if (mineHudManager != null) {
                mineHudManager.showCombo(playerId, mineProgress.getComboCount(), 1.0f);
            }
        }
    }

    public static void sendBagFullMessageIfNeeded(UUID playerId, Player player, Map<UUID, Long> lastBagFullMessage) {
        long now = System.currentTimeMillis();
        Long last = lastBagFullMessage.get(playerId);
        if (last == null || now - last > 3000) {
            lastBagFullMessage.put(playerId, now);
            player.sendMessage(Message.raw("Bag full! Sell your blocks with /mine sell"));
        }
    }

    public static void sendRegenMessageIfNeeded(UUID playerId, Player player, MineManager mineManager,
                                                 String zoneId, Map<UUID, Long> lastRegenMessage) {
        long now = System.currentTimeMillis();
        Long last = lastRegenMessage.get(playerId);
        if (last == null || now - last > 3000) {
            lastRegenMessage.put(playerId, now);
            player.sendMessage(Message.raw("Mine is resetting..."));
        }
    }
}

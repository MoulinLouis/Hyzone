package io.hyvexa.ascend.mine.quest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MineQuestProgressTest {

    private MineQuestProgress progress;

    @BeforeEach
    void setUp() {
        progress = new MineQuestProgress(UUID.randomUUID());
    }

    @Test
    void defaultStateIsQuestZeroWithZeroProgress() {
        assertEquals(0, progress.getQuestIndex("miner"));
        assertEquals(0L, progress.getObjectiveProgress("miner"));
    }

    @Test
    void getActiveQuestReturnsFirstQuestInitially() {
        MineQuest quest = progress.getActiveQuest("miner");
        assertNotNull(quest);
        assertEquals(0, quest.getOrderInChain());
    }

    @Test
    void isReadyToTurnInReturnsFalseWhenProgressBelowTarget() {
        MineQuest quest = progress.getActiveQuest("miner");
        progress.setObjectiveProgress("miner", quest.getTarget() - 1);
        assertFalse(progress.isReadyToTurnIn("miner"));
    }

    @Test
    void isReadyToTurnInReturnsTrueWhenProgressMeetsTarget() {
        MineQuest quest = progress.getActiveQuest("miner");
        progress.setObjectiveProgress("miner", quest.getTarget());
        assertTrue(progress.isReadyToTurnIn("miner"));
    }

    @Test
    void isReadyToTurnInReturnsTrueWhenProgressExceedsTarget() {
        MineQuest quest = progress.getActiveQuest("miner");
        progress.setObjectiveProgress("miner", quest.getTarget() + 100);
        assertTrue(progress.isReadyToTurnIn("miner"));
    }

    @Test
    void advanceQuestIncrementsIndexAndResetsProgress() {
        progress.setObjectiveProgress("miner", 999);
        progress.advanceQuest("miner");

        assertEquals(1, progress.getQuestIndex("miner"));
        assertEquals(0L, progress.getObjectiveProgress("miner"));
    }

    @Test
    void addObjectiveProgressAccumulatesCorrectly() {
        progress.addObjectiveProgress("miner", 10);
        progress.addObjectiveProgress("miner", 25);
        assertEquals(35L, progress.getObjectiveProgress("miner"));
    }

    @Test
    void isChainCompleteReturnsFalseWhenQuestsRemain() {
        assertFalse(progress.isChainComplete("miner"));
    }

    @Test
    void isChainCompleteReturnsTrueWhenIndexExceedsChainLength() {
        int length = MineQuest.getChainLength("miner");
        progress.setQuestIndex("miner", length);
        assertTrue(progress.isChainComplete("miner"));
    }

    @Test
    void getActiveQuestReturnsNullWhenChainComplete() {
        int length = MineQuest.getChainLength("miner");
        progress.setQuestIndex("miner", length);
        assertNull(progress.getActiveQuest("miner"));
    }

    @Test
    void isReadyToTurnInReturnsFalseWhenChainComplete() {
        int length = MineQuest.getChainLength("miner");
        progress.setQuestIndex("miner", length);
        assertFalse(progress.isReadyToTurnIn("miner"));
    }

    @Test
    void multipleChainsDontInterfere() {
        progress.setQuestIndex("miner", 3);
        progress.setObjectiveProgress("miner", 100);
        progress.setQuestIndex("other", 1);
        progress.setObjectiveProgress("other", 50);

        assertEquals(3, progress.getQuestIndex("miner"));
        assertEquals(100L, progress.getObjectiveProgress("miner"));
        assertEquals(1, progress.getQuestIndex("other"));
        assertEquals(50L, progress.getObjectiveProgress("other"));
    }

    @Test
    void unknownChainReturnsDefaults() {
        assertEquals(0, progress.getQuestIndex("unknown"));
        assertEquals(0L, progress.getObjectiveProgress("unknown"));
        assertTrue(progress.isChainComplete("unknown")); // 0 >= 0
    }

    @Test
    void walkThroughEntireMinerChain() {
        int length = MineQuest.getChainLength("miner");
        for (int i = 0; i < length; i++) {
            MineQuest quest = progress.getActiveQuest("miner");
            assertNotNull(quest, "Quest should be active at index " + i);
            assertEquals(i, quest.getOrderInChain());

            progress.setObjectiveProgress("miner", quest.getTarget());
            assertTrue(progress.isReadyToTurnIn("miner"));

            progress.advanceQuest("miner");
        }
        assertTrue(progress.isChainComplete("miner"));
        assertNull(progress.getActiveQuest("miner"));
    }
}

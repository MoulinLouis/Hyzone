package io.hyvexa.ascend.mine.quest;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MineQuestTest {

    @Test
    void allQuestsHaveUniqueChainAndIndexPairs() {
        Set<String> seen = new HashSet<>();
        for (MineQuest q : MineQuest.values()) {
            String key = q.getChain() + ":" + q.getOrderInChain();
            assertTrue(seen.add(key), "Duplicate chain+index: " + key);
        }
    }

    @Test
    void chainIndicesAreContiguousStartingAtZero() {
        int length = MineQuest.getChainLength("miner");
        assertTrue(length > 0, "Miner chain should have quests");
        for (int i = 0; i < length; i++) {
            MineQuest q = MineQuest.getByChainAndIndex("miner", i);
            assertNotNull(q, "Missing quest at miner index " + i);
            assertEquals(i, q.getOrderInChain());
        }
    }

    @Test
    void getByChainAndIndexReturnsNullForInvalidIndex() {
        assertNull(MineQuest.getByChainAndIndex("miner", 999));
        assertNull(MineQuest.getByChainAndIndex("nonexistent", 0));
    }

    @Test
    void getChainLengthReturnsZeroForUnknownChain() {
        assertEquals(0, MineQuest.getChainLength("nonexistent"));
    }

    @Test
    void allQuestsHavePositiveTargetAndReward() {
        for (MineQuest q : MineQuest.values()) {
            assertTrue(q.getTarget() > 0, q.name() + " target should be positive");
            assertTrue(q.getCrystalReward() > 0, q.name() + " reward should be positive");
        }
    }

    @Test
    void allQuestsHaveNonEmptyDialogues() {
        for (MineQuest q : MineQuest.values()) {
            assertTrue(q.getGiveDialogue().length > 0, q.name() + " needs give dialogue");
            assertTrue(q.getCompleteDialogue().length > 0, q.name() + " needs complete dialogue");
            assertNotNull(q.getTitle());
            assertNotNull(q.getObjectiveText());
            assertFalse(q.getTitle().isEmpty());
            assertFalse(q.getObjectiveText().isEmpty());
        }
    }

    @Test
    void minerChainRewardsIncrease() {
        int length = MineQuest.getChainLength("miner");
        long previousReward = 0;
        for (int i = 0; i < length; i++) {
            MineQuest q = MineQuest.getByChainAndIndex("miner", i);
            assertTrue(q.getCrystalReward() > previousReward,
                q.name() + " reward should exceed previous (" + q.getCrystalReward() + " <= " + previousReward + ")");
            previousReward = q.getCrystalReward();
        }
    }
}

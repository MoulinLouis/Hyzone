package io.hyvexa.ascend.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AscendMapTest {

    private AscendMap createMap(int displayOrder) {
        AscendMap map = new AscendMap();
        map.setDisplayOrder(displayOrder);
        return map;
    }

    @Test
    void getEffectivePriceDelegatesToAscendConstants() {
        assertEquals(0L, createMap(0).getEffectivePrice());
        assertEquals(2500L, createMap(3).getEffectivePrice());
    }

    @Test
    void getEffectiveBaseRewardDelegatesToAscendConstants() {
        assertEquals(1L, createMap(0).getEffectiveBaseReward());
        assertEquals(2500L, createMap(5).getEffectiveBaseReward());
    }

    @Test
    void getEffectiveBaseRunTimeMsDelegatesToAscendConstants() {
        assertEquals(5000L, createMap(0).getEffectiveBaseRunTimeMs());
        assertEquals(68000L, createMap(5).getEffectiveBaseRunTimeMs());
    }

    @Test
    void getEffectiveRobotPriceAlwaysReturnsZero() {
        for (int i = 0; i <= 5; i++) {
            assertEquals(0L, createMap(i).getEffectiveRobotPrice(),
                "Robot price should be 0 for displayOrder " + i);
        }
    }

    @Test
    void pricesAreNonDecreasing() {
        long prevPrice = -1;
        for (int i = 0; i <= 5; i++) {
            long price = createMap(i).getEffectivePrice();
            assertTrue(price >= prevPrice,
                "Price should be non-decreasing at displayOrder " + i);
            prevPrice = price;
        }
    }

    @Test
    void rewardsAreStrictlyIncreasing() {
        long prevReward = 0;
        for (int i = 0; i <= 5; i++) {
            long reward = createMap(i).getEffectiveBaseReward();
            assertTrue(reward > prevReward,
                "Reward should be strictly increasing at displayOrder " + i);
            prevReward = reward;
        }
    }

    @Test
    void runTimesAreStrictlyIncreasing() {
        long prevTime = 0;
        for (int i = 0; i <= 5; i++) {
            long time = createMap(i).getEffectiveBaseRunTimeMs();
            assertTrue(time > prevTime,
                "Run time should be strictly increasing at displayOrder " + i);
            prevTime = time;
        }
    }
}

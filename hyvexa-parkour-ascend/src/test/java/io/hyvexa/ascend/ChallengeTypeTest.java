package io.hyvexa.ascend;

import io.hyvexa.ascend.AscensionConstants.ChallengeType;
import io.hyvexa.ascend.SummitConstants.SummitCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChallengeTypeTest {

    @Test
    void allChallengesHavePositiveDivisors() {
        for (ChallengeType type : ChallengeType.values()) {
            assertTrue(type.getSpeedDivisor() > 0,
                type.name() + " speedDivisor must be > 0");
            assertTrue(type.getMultiplierGainDivisor() > 0,
                type.name() + " multiplierGainDivisor must be > 0");
            assertTrue(type.getEvolutionPowerDivisor() > 0,
                type.name() + " evolutionPowerDivisor must be > 0");
        }
    }

    @Test
    void challengeDifficultyIncreases() {
        // CHALLENGE_8 should be harder than CHALLENGE_1
        double maxDivisorC1 = Math.max(ChallengeType.CHALLENGE_1.getSpeedDivisor(),
            Math.max(ChallengeType.CHALLENGE_1.getMultiplierGainDivisor(),
                ChallengeType.CHALLENGE_1.getEvolutionPowerDivisor()));

        // CHALLENGE_8 blocks elevation and summit entirely — hardest
        assertTrue(ChallengeType.CHALLENGE_8.blocksElevation(),
            "Challenge 8 should block elevation");
        assertTrue(ChallengeType.CHALLENGE_8.blocksAllSummit(),
            "Challenge 8 should block all summit");

        // Earlier challenges should not block both
        assertFalse(ChallengeType.CHALLENGE_1.blocksElevation());
        assertFalse(ChallengeType.CHALLENGE_1.blocksAllSummit());
    }

    @Test
    void blockedMapsAreValidDisplayOrders() {
        for (ChallengeType type : ChallengeType.values()) {
            for (int mapOrder : type.getBlockedMapDisplayOrders()) {
                assertTrue(mapOrder >= 0 && mapOrder <= 5,
                    type.name() + " has blocked map " + mapOrder + " outside range 0-5");
            }
        }
    }

    @Test
    void blockedSummitCategoriesAreValidEnums() {
        for (ChallengeType type : ChallengeType.values()) {
            for (SummitCategory cat : type.getBlockedSummitCategories()) {
                assertNotNull(cat, type.name() + " has null blocked summit category");
            }
        }
    }

    @Test
    void allChallengesHaveNonBlankMetadata() {
        for (ChallengeType type : ChallengeType.values()) {
            assertTrue(type.getId() > 0, type.name() + " id should be positive");
            assertNotNull(type.getDisplayName());
            assertFalse(type.getDisplayName().isBlank(), type.name() + " displayName is blank");
            assertNotNull(type.getDescription());
            assertFalse(type.getDescription().isBlank(), type.name() + " description is blank");
            assertNotNull(type.getAccentColor());
            assertFalse(type.getAccentColor().isBlank(), type.name() + " accentColor is blank");
        }
    }

    @Test
    void fromIdReturnsCorrectChallenges() {
        for (ChallengeType type : ChallengeType.values()) {
            assertEquals(type, ChallengeType.fromId(type.getId()),
                "fromId should return " + type.name());
        }
    }

    @Test
    void fromIdReturnsNullForInvalidId() {
        assertNull(ChallengeType.fromId(0));
        assertNull(ChallengeType.fromId(99));
        assertNull(ChallengeType.fromId(-1));
    }
}

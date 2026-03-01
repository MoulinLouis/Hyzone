package io.hyvexa.duel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DuelMatchTest {

    private UUID player1;
    private UUID player2;
    private DuelMatch match;

    @BeforeEach
    void setUp() {
        player1 = UUID.randomUUID();
        player2 = UUID.randomUUID();
        match = new DuelMatch("test-match", player1, player2, "map1");
    }

    @Test
    void getOpponentReturnsOtherPlayer() {
        assertEquals(player2, match.getOpponent(player1));
        assertEquals(player1, match.getOpponent(player2));
    }

    @Test
    void getOpponentUnknownReturnsNull() {
        assertNull(match.getOpponent(UUID.randomUUID()));
    }

    @Test
    void hasPlayerTrueForBoth() {
        assertTrue(match.hasPlayer(player1));
        assertTrue(match.hasPlayer(player2));
    }

    @Test
    void hasPlayerFalseForUnknown() {
        assertFalse(match.hasPlayer(UUID.randomUUID()));
    }

    @Test
    void trySetWinnerSucceedsFirstTime() {
        assertTrue(match.trySetWinner(player1));
        assertEquals(player1, match.getWinnerId());
    }

    @Test
    void trySetWinnerFailsSecondTime() {
        assertTrue(match.trySetWinner(player1));
        assertFalse(match.trySetWinner(player2));
        assertEquals(player1, match.getWinnerId());
    }

    @Test
    void getFinishTimeForReturnsCorrectPlayer() {
        match.setFinishTimeFor(player1, 1000L);
        match.setFinishTimeFor(player2, 2000L);
        assertEquals(1000L, match.getFinishTimeFor(player1));
        assertEquals(2000L, match.getFinishTimeFor(player2));
    }

    @Test
    void getFinishTimeForUnknownReturnsZero() {
        assertEquals(0L, match.getFinishTimeFor(UUID.randomUUID()));
    }

    @Test
    void setFinishTimeForOnlySetsMatchingPlayer() {
        match.setFinishTimeFor(player1, 5000L);
        assertEquals(5000L, match.getFinishTimeFor(player1));
        assertEquals(0L, match.getFinishTimeFor(player2));
    }

    @Test
    void setFinishTimeForUnknownPlayerIsNoOp() {
        match.setFinishTimeFor(UUID.randomUUID(), 9999L);
        assertEquals(0L, match.getFinishTimeFor(player1));
        assertEquals(0L, match.getFinishTimeFor(player2));
    }

    @Test
    void initialStateIsStarting() {
        assertEquals(DuelState.STARTING, match.getState());
    }

    @Test
    void matchFields() {
        assertEquals("test-match", match.getMatchId());
        assertEquals(player1, match.getPlayer1());
        assertEquals(player2, match.getPlayer2());
        assertEquals("map1", match.getMapId());
    }
}

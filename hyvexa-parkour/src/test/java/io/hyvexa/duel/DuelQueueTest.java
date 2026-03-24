package io.hyvexa.duel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DuelQueueTest {

    private DuelQueue queue;
    private UUID player1;
    private UUID player2;
    private UUID player3;
    private UUID player4;

    @BeforeEach
    void setUp() {
        queue = new DuelQueue();
        player1 = UUID.randomUUID();
        player2 = UUID.randomUUID();
        player3 = UUID.randomUUID();
        player4 = UUID.randomUUID();
    }

    // --- Basic Operations ---

    @Test
    void joinAddsPlayerToQueue() {
        assertTrue(queue.join(player1));
        assertTrue(queue.isQueued(player1));
        assertEquals(1, queue.size());
    }

    @Test
    void joinRejectsDuplicatePlayer() {
        assertTrue(queue.join(player1));
        assertFalse(queue.join(player1));
        assertEquals(1, queue.size());
    }

    @Test
    void leaveRemovesPlayerFromQueue() {
        queue.join(player1);
        assertTrue(queue.leave(player1));
        assertFalse(queue.isQueued(player1));
        assertEquals(0, queue.size());
    }

    @Test
    void leaveReturnsFalseForUnknownPlayer() {
        assertFalse(queue.leave(UUID.randomUUID()));
    }

    @Test
    void isQueuedReturnsFalseForUnknownPlayer() {
        assertFalse(queue.isQueued(UUID.randomUUID()));
    }

    // --- Position Tracking ---

    @Test
    void getPositionReturnsOneIndexedOrder() {
        queue.join(player1);
        queue.join(player2);
        queue.join(player3);

        assertEquals(1, queue.getPosition(player1));
        assertEquals(2, queue.getPosition(player2));
        assertEquals(3, queue.getPosition(player3));
    }

    @Test
    void getPositionReturnsMinusOneForUnqueuedPlayer() {
        assertEquals(-1, queue.getPosition(UUID.randomUUID()));
    }

    @Test
    void getPositionUpdatesAfterLeave() {
        queue.join(player1);
        queue.join(player2);
        queue.join(player3);

        queue.leave(player1);

        assertEquals(1, queue.getPosition(player2));
        assertEquals(2, queue.getPosition(player3));
    }

    // --- Matchmaking ---

    @Test
    void tryMatchReturnsNullWhenFewerThanTwoPlayers() {
        assertNull(queue.tryMatch());

        queue.join(player1);
        assertNull(queue.tryMatch());
    }

    @Test
    void tryMatchReturnsFirstTwoPlayersInOrder() {
        queue.join(player1);
        queue.join(player2);
        queue.join(player3);

        UUID[] pair = queue.tryMatch();

        assertNotNull(pair);
        assertEquals(player1, pair[0]);
        assertEquals(player2, pair[1]);
        assertEquals(1, queue.size());
        assertFalse(queue.isQueued(player1));
        assertFalse(queue.isQueued(player2));
    }

    @Test
    void tryMatchConsecutiveCallsDrainQueue() {
        queue.join(player1);
        queue.join(player2);
        queue.join(player3);
        queue.join(player4);

        UUID[] first = queue.tryMatch();
        assertNotNull(first);
        assertEquals(player1, first[0]);
        assertEquals(player2, first[1]);

        UUID[] second = queue.tryMatch();
        assertNotNull(second);
        assertEquals(player3, second[0]);
        assertEquals(player4, second[1]);

        assertNull(queue.tryMatch());
    }

    // --- Advanced Operations ---

    @Test
    void addToFrontMovesPlayerToFrontOfQueue() {
        queue.join(player1);
        queue.join(player2);
        queue.join(player3);

        queue.addToFront(player2);

        assertEquals(1, queue.getPosition(player2));
        assertEquals(2, queue.getPosition(player1));
        assertEquals(3, queue.getPosition(player3));
    }

    @Test
    void addToFrontWithNewPlayerAddsToFront() {
        queue.join(player1);

        queue.addToFront(player2);

        assertEquals(1, queue.getPosition(player2));
        assertEquals(2, queue.getPosition(player1));
    }

    @Test
    void removePairRemovesBothPlayers() {
        queue.join(player1);
        queue.join(player2);
        queue.join(player3);

        assertTrue(queue.removePair(player1, player2));
        assertEquals(1, queue.size());
        assertFalse(queue.isQueued(player1));
        assertFalse(queue.isQueued(player2));
        assertTrue(queue.isQueued(player3));
    }

    @Test
    void removePairReturnsFalseIfEitherMissing() {
        queue.join(player1);

        assertFalse(queue.removePair(player1, UUID.randomUUID()));
        assertEquals(1, queue.size());
    }

    @Test
    void getWaitingPlayersReturnsDefensiveCopy() {
        queue.join(player1);
        queue.join(player2);

        List<UUID> list = queue.getWaitingPlayers();
        assertEquals(2, list.size());

        list.clear();
        assertEquals(2, queue.size());
    }

    @Test
    void clearRemovesAllPlayers() {
        queue.join(player1);
        queue.join(player2);
        queue.join(player3);

        queue.clear();

        assertEquals(0, queue.size());
        assertNull(queue.tryMatch());
    }
}

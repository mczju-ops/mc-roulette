package com.mczju.mcroulette.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TurnOrderTest {

    @Test
    void forwardOrderMatchesSpecExamples() {
        // 5 人局第 2 轮：2 -> 3 -> 4 -> 5 -> 1
        assertEquals(List.of(2, 3, 4, 5, 1), TurnOrder.seats(2, 5, false));
        // 3 人局第 9 轮：3 -> 1 -> 2
        assertEquals(List.of(3, 1, 2), TurnOrder.seats(9, 3, false));
    }

    @Test
    void reverseOrderFlipsTheWholeRound() {
        // 5 人局第 2 轮正序 2,3,4,5,1，逆流后 1,5,4,3,2
        assertEquals(List.of(1, 5, 4, 3, 2), TurnOrder.seats(2, 5, true));
        // 4 人局第 1 轮正序 1,2,3,4，逆流后 4,3,2,1
        assertEquals(List.of(4, 3, 2, 1), TurnOrder.seats(1, 4, true));
    }

    @Test
    void reverseChangesTheOpeningPlayer() {
        for (int players = 2; players <= 12; players++) {
            for (int round = 1; round <= 6; round++) {
                List<Integer> forward = TurnOrder.seats(round, players, false);
                List<Integer> reversed = TurnOrder.seats(round, players, true);
                assertNotEquals(forward, reversed, "逆流在 " + players + " 人局第 " + round + " 轮没有改变顺序");
                assertEquals(forward.get(forward.size() - 1), reversed.get(0),
                        "逆流后应当由正序最后一名先行动");
            }
        }
    }

    @Test
    void eliminatedSeatsAreSkipped() {
        assertEquals(List.of(1, 3, 5), TurnOrder.seats(1, 5, false, Set.of(1, 3, 5)));
        // 第 2 轮正序 2,3,4,5,1，存活 1/2/4 -> 2,4,1，逆流后 1,4,2
        assertEquals(List.of(1, 4, 2), TurnOrder.seats(2, 5, true, Set.of(1, 2, 4)));
    }

    @Test
    void twoPlayersReverseStillChangesWhoStarts() {
        assertEquals(List.of(1, 2), TurnOrder.seats(1, 2, false));
        assertEquals(List.of(2, 1), TurnOrder.seats(1, 2, true));
    }
}

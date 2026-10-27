package com.mczju.mcroulette.core;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouletteEventTest {

    @Test
    void twoPlayerGamesNeverDrawMyopia() {
        for (int i = 0; i < 3000; i++) {
            RouletteEvent event = RouletteEvent.rollFor(2);
            assertNotEquals(RouletteEvent.MYOPIA, event, "两人局抽到了没有可见效果的短视");
        }
    }

    @Test
    void largeTablesCanDrawEveryEvent() {
        EnumSet<RouletteEvent> seen = EnumSet.noneOf(RouletteEvent.class);
        for (int i = 0; i < 4000; i++) {
            seen.add(RouletteEvent.rollFor(6));
        }
        for (RouletteEvent event : RouletteEvent.values()) {
            assertTrue(seen.contains(event), "4000 次抽取里没有出现 " + event);
        }
    }

    @Test
    void noEventIsAboutOneThirdAndMisfireIsRarest() {
        Map<RouletteEvent, Integer> counts = new EnumMap<>(RouletteEvent.class);
        for (RouletteEvent event : RouletteEvent.values()) {
            counts.put(event, 0);
        }
        int rolls = 30000;
        for (int i = 0; i < rolls; i++) {
            RouletteEvent event = RouletteEvent.roll();
            counts.merge(event, 1, Integer::sum);
        }
        double noneRate = counts.get(RouletteEvent.NONE) / (double) rolls;
        assertTrue(noneRate > 0.28 && noneRate < 0.39, "无事发生的比例偏离 1/3：" + noneRate);

        int misfire = counts.get(RouletteEvent.MISFIRE);
        for (RouletteEvent other : RouletteEvent.values()) {
            if (other == RouletteEvent.NONE || other == RouletteEvent.MISFIRE) {
                continue;
            }
            assertTrue(misfire < counts.get(other),
                    "走火 " + misfire + " 次，不少于 " + other + " 的 " + counts.get(other) + " 次");
        }
    }
}

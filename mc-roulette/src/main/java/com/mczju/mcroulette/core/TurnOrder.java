package com.mczju.mcroulette.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 回合顺序的纯计算逻辑，方便单独测试。
 *
 * <p>第 M 轮从序号 {@code (M-1) % N + 1} 开始，沿序号方向依次前进，绕一圈后结束。
 * 逆流事件把整轮顺序倒过来，也就是原本最后行动的人先行动。
 */
public final class TurnOrder {

    private TurnOrder() {
    }

    /**
     * @param round        轮次，从 1 开始
     * @param totalPlayers 本局人数 N（含已淘汰玩家）
     * @param reversed     是否逆流
     * @param aliveSeats   仍可行动的序号；为 null 表示全部
     * @return 本轮的行动序号顺序
     */
    public static List<Integer> seats(int round, int totalPlayers, boolean reversed, Set<Integer> aliveSeats) {
        List<Integer> result = new ArrayList<>();
        if (totalPlayers <= 0) {
            return result;
        }
        int seat = Math.floorMod(round - 1, totalPlayers) + 1;
        for (int i = 0; i < totalPlayers; i++) {
            if (aliveSeats == null || aliveSeats.contains(seat)) {
                result.add(seat);
            }
            seat = seat % totalPlayers + 1;
        }
        if (reversed) {
            Collections.reverse(result);
        }
        return result;
    }

    /** 不排除任何玩家时的顺序。 */
    public static List<Integer> seats(int round, int totalPlayers, boolean reversed) {
        return seats(round, totalPlayers, reversed, null);
    }
}

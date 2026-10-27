package com.mczju.mcroulette.core;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 每轮开始时抽取的随机事件。
 *
 * <p>抽取规则：先有 1/3 概率「无事发生」，其余 2/3 在五个事件里按权重抽取，
 * 其中走火权重为其它事件的一半，避免频繁被迫射击。
 */
public enum RouletteEvent {

    /** 本轮没有特殊事件。 */
    NONE("无事发生", "本轮没有特殊事件"),

    MISFIRE("走火", "所有玩家的下一次行动固定为射击"),
    TRUCE("非攻", "本轮无法射击、挑衅，被破坏后也无法射击"),
    REVERSE("逆流", "本轮行动顺序反转"),
    METEOR("陨星", "每名玩家回合结束时，有 1/3 概率失去 1 点生命（闪避有效）"),
    MYOPIA("短视", "选择目标时只能选择下一名玩家");

    /** 走火出现 1 次，其余事件各 2 次。 */
    private static final RouletteEvent[] WEIGHTED = {
            MISFIRE, TRUCE, TRUCE, REVERSE, REVERSE, METEOR, METEOR, MYOPIA, MYOPIA
    };

    private final String displayName;
    private final String description;

    RouletteEvent(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** 播报文本，MiniMessage 格式。 */
    public String announce() {
        return "<gold>[事件] <yellow>" + displayName + " <dark_gray>- <gray>" + description;
    }

    /** 1/3 无事发生，否则按权重抽取一个事件。 */
    public static RouletteEvent roll() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(3) == 0) {
            return NONE;
        }
        return WEIGHTED[random.nextInt(WEIGHTED.length)];
    }

    /**
     * 按本局人数抽取事件。两人局里短视没有任何可观察的效果（本来也只能选另一名玩家），
     * 抽到会重抽，避免玩家看到「事件触发但没有任何变化」。
     */
    public static RouletteEvent rollFor(int totalPlayers) {
        for (int attempt = 0; attempt < 4; attempt++) {
            RouletteEvent candidate = roll();
            if (candidate == NONE) {
                return NONE;
            }
            if (totalPlayers <= 2 && candidate == MYOPIA) {
                continue;
            }
            return candidate;
        }
        return NONE;
    }
}

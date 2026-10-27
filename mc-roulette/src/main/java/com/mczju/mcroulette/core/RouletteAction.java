package com.mczju.mcroulette.core;

/**
 * 玩家可以执行的五种行动。
 */
public enum RouletteAction {

    LOAD("装填"),
    SHOOT("射击"),
    PROVOKE("挑衅"),
    DODGE("闪避"),
    SABOTAGE("破坏");

    private final String displayName;

    RouletteAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

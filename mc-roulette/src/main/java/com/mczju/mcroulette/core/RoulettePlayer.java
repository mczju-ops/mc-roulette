package com.mczju.mcroulette.core;

import java.util.Objects;
import java.util.UUID;

/**
 * 一名玩家在本局中的运行时状态。只在本局内存活，不参与持久化。
 */
public final class RoulettePlayer {

    private final UUID uuid;
    private final String name;
    private final int seat;

    private int health;
    private int bullets;
    private boolean dodging;
    private boolean alive = true;

    public RoulettePlayer(UUID uuid, String name, int seat) {
        this.uuid = uuid;
        this.name = name;
        this.seat = seat;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getSeat() {
        return seat;
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public int getBullets() {
        return bullets;
    }

    public void setBullets(int bullets) {
        this.bullets = bullets;
    }

    public boolean isDodging() {
        return dodging;
    }

    public void setDodging(boolean dodging) {
        this.dodging = dodging;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    /** 团队战中的阵营：序号为奇数属于一队，序号为偶数属于另一队。 */
    public boolean isOddTeam() {
        return seat % 2 == 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RoulettePlayer other)) {
            return false;
        }
        return uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}

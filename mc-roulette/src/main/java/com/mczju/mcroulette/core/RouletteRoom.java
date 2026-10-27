package com.mczju.mcroulette.core;

import com.github.mczjuops.mczjugamecore.game.room.FieldDescription;
import com.github.mczjuops.mczjugamecore.game.room.JsonGameRoom;
import org.bukkit.Location;

/**
 * 轮盘赌的房间配置。所有可调参数都在这里，使用 /mgcop room edit 修改。
 */
public class RouletteRoom extends JsonGameRoom {

    @FieldDescription("每名玩家开局时的生命值")
    public int health = 3;

    @FieldDescription({"游戏模式：false 为个人战（活到最后的人获胜）", "true 为团队战（奇数序号一队、偶数序号一队，消灭对方全队获胜）"})
    public boolean teamMode = false;

    @FieldDescription("开局时所有玩家传送到的位置，可留空")
    public Location startLocation = null;

    @FieldDescription("结算 5 秒后返回的位置，可留空")
    public Location exitLocation = null;
}

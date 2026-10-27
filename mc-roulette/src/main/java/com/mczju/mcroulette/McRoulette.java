package com.mczju.mcroulette;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.item.MGCItem;
import com.mczju.mcroulette.core.RouletteGame;
import com.mczju.mcroulette.core.RouletteRoom;
import com.mczju.mcroulette.item.DodgeItem;
import com.mczju.mcroulette.item.LoadItem;
import com.mczju.mcroulette.item.MagazineItem;
import com.mczju.mcroulette.item.NumberCardItem;
import com.mczju.mcroulette.item.ProvokeItem;
import com.mczju.mcroulette.item.SabotageItem;
import com.mczju.mcroulette.item.ShootItem;
import com.mczju.mcroulette.listener.ActionItemListener;
import com.mczju.mcroulette.listener.ItemGuardListener;
import com.mczju.mcroulette.listener.MenuListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * mc-roulette：基于 MCZJUGameCore 的轮盘赌小游戏。
 */
public final class McRoulette extends JavaPlugin {

    private static McRoulette instance;

    public static McRoulette getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // 注册游戏与房间配置，MGC 会自动加载房间文件
        MCZJUGameCore.getGameManager().registerGame(RouletteGame.class, RouletteRoom.class);

        for (MGCItem item : List.of(
                NumberCardItem.INSTANCE,
                LoadItem.INSTANCE,
                ShootItem.INSTANCE,
                ProvokeItem.INSTANCE,
                DodgeItem.INSTANCE,
                SabotageItem.INSTANCE,
                MagazineItem.INSTANCE)) {
            MCZJUGameCore.getItemManager().register(item);
        }

        Bukkit.getPluginManager().registerEvents(new ActionItemListener(), this);
        Bukkit.getPluginManager().registerEvents(new ItemGuardListener(), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);

        getLogger().info("mc-roulette 已加载，游戏 ID：" + RouletteGame.GAME_ID);
    }

    @Override
    public void onDisable() {
        for (RouletteGame game : RouletteGame.activeGames()) {
            game.dispose();
        }
        instance = null;
    }
}

package com.mczju.mcroulette.listener;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.mczju.mcroulette.item.RouletteItems;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * 防止本局物品被放置到地图上或丢出去。
 */
public final class ItemGuardListener implements Listener {

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!RouletteItems.isRouletteItem(event.getItemInHand())) {
            return;
        }
        event.setCancelled(true);
        new PlayerExt(event.getPlayer()).sender().warn("这是轮盘赌的物品，不能放置");
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!RouletteItems.isRouletteItem(event.getItemDrop().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        new PlayerExt(event.getPlayer()).sender().warn("这是轮盘赌的物品，不能丢弃");
    }
}

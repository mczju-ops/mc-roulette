package com.mczju.mcroulette.listener;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.mczju.mcroulette.core.RouletteAction;
import com.mczju.mcroulette.core.RouletteGame;
import com.mczju.mcroulette.item.RouletteItems;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 处理玩家右键号码牌 / 弹夹 / 行动羊毛。
 */
public final class ActionItemListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!RouletteItems.isRouletteItem(item)) {
            return;
        }
        // 羊毛/箱子都要阻止放置或使用
        event.setCancelled(true);

        PlayerExt ext = new PlayerExt(event.getPlayer());
        AbstractGame game = ext.getGame();
        if (!(game instanceof RouletteGame roulette)) {
            return;
        }
        RouletteAction action = RouletteItems.actionOf(item);
        if (action == null) {
            roulette.showStatus(ext);
            return;
        }
        roulette.handleItemUse(ext, action);
    }
}

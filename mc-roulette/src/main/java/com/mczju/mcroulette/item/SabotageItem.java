package com.mczju.mcroulette.item;

import com.github.mczjuops.mczjugamecore.item.MGCItem;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 行动物品：破坏，试图打掉对方一发子弹。 */
public final class SabotageItem extends MGCItem {

    public static final SabotageItem INSTANCE = new SabotageItem();

    private SabotageItem() {
    }

    @Override
    protected ItemStack createRawItem() {
        return ItemBuilder.of(Material.PURPLE_WOOL)
                .customName("<light_purple><bold>破坏")
                .lore(List.of(
                        "<gray>选择一名其他玩家",
                        "<gray>有 50% 的概率使其子弹数 -1",
                        "<gray>若破坏失败，这名玩家可以选择是否向你射击一次"))
                .build();
    }

    @Override
    public String getId() {
        return "mc_roulette_sabotage";
    }
}

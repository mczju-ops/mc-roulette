package com.mczju.mcroulette.item;

import com.github.mczjuops.mczjugamecore.item.MGCItem;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 行动物品：挑衅，让对方朝你开一枪。 */
public final class ProvokeItem extends MGCItem {

    public static final ProvokeItem INSTANCE = new ProvokeItem();

    private ProvokeItem() {
    }

    @Override
    protected ItemStack createRawItem() {
        return ItemBuilder.of(Material.ORANGE_WOOL)
                .customName("<gold><bold>挑衅")
                .lore(List.of(
                        "<gray>选择一名其他玩家向你射击一次",
                        "<gray>若未命中，你的弹夹中增加一发子弹"))
                .build();
    }

    @Override
    public String getId() {
        return "mc_roulette_provoke";
    }
}

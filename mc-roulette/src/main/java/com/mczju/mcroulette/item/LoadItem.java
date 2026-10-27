package com.mczju.mcroulette.item;

import com.github.mczjuops.mczjugamecore.item.MGCItem;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 行动物品：装填一发子弹。 */
public final class LoadItem extends MGCItem {

    public static final LoadItem INSTANCE = new LoadItem();

    private LoadItem() {
    }

    @Override
    protected ItemStack createRawItem() {
        return ItemBuilder.of(Material.YELLOW_WOOL)
                .customName("<yellow><bold>装填")
                .lore(List.of("<gray>装填一发子弹"))
                .build();
    }

    @Override
    public String getId() {
        return "mc_roulette_load";
    }
}

package com.mczju.mcroulette.item;

import com.github.mczjuops.mczjugamecore.item.MGCItem;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 行动物品：向其他玩家射击。 */
public final class ShootItem extends MGCItem {

    public static final ShootItem INSTANCE = new ShootItem();

    private ShootItem() {
    }

    @Override
    protected ItemStack createRawItem() {
        return ItemBuilder.of(Material.RED_WOOL)
                .customName("<red><bold>射击")
                .lore(List.of(
                        "<gray>向一名其他玩家射击",
                        "<gray>命中概率 = 弹夹中弹药数 / 6",
                        "<gray>无论是否命中都会清空弹夹"))
                .build();
    }

    @Override
    public String getId() {
        return "mc_roulette_shoot";
    }
}

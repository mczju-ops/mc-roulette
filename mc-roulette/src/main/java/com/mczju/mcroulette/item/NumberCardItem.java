package com.mczju.mcroulette.item;

import com.github.mczjuops.mczjugamecore.item.MGCItem;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 号码牌：显示玩家本局的序号。 */
public final class NumberCardItem extends MGCItem {

    public static final NumberCardItem INSTANCE = new NumberCardItem();

    private NumberCardItem() {
    }

    @Override
    protected ItemStack createRawItem() {
        return ItemBuilder.of(Material.PAPER)
                .customName("<yellow><bold>号码牌")
                .lore(List.of("<gray>右键查看本局状态"))
                .build();
    }

    @Override
    public String getId() {
        return "mc_roulette_number_card";
    }
}

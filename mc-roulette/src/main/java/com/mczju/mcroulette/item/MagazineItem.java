package com.mczju.mcroulette.item;

import com.github.mczjuops.mczjugamecore.item.MGCItem;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 弹夹：显示当前装填的子弹数。 */
public final class MagazineItem extends MGCItem {

    public static final MagazineItem INSTANCE = new MagazineItem();

    private MagazineItem() {
    }

    @Override
    protected ItemStack createRawItem() {
        return ItemBuilder.of(Material.CHEST)
                .customName("<gold><bold>弹夹")
                .lore(List.of("<gray>装填弹药数 <yellow>0<gray>/6"))
                .build();
    }

    @Override
    public String getId() {
        return "mc_roulette_magazine";
    }
}

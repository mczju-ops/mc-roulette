package com.mczju.mcroulette.item;

import com.github.mczjuops.mczjugamecore.item.MGCItem;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 行动物品：闪避，直到下一个自己的回合开始前减免伤害。 */
public final class DodgeItem extends MGCItem {

    public static final DodgeItem INSTANCE = new DodgeItem();

    private DodgeItem() {
    }

    @Override
    protected ItemStack createRawItem() {
        return ItemBuilder.of(Material.BLUE_WOOL)
                .customName("<aqua><bold>闪避")
                .lore(List.of(
                        "<gray>直到下一个你的回合开始前",
                        "<gray>你受到伤害时有 50% 概率闪避"))
                .build();
    }

    @Override
    public String getId() {
        return "mc_roulette_dodge";
    }
}

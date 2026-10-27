package com.mczju.mcroulette.item;

import com.mczju.mcroulette.core.RouletteAction;
import org.bukkit.inventory.ItemStack;

/**
 * 物品识别工具：把手中的物品映射为行动，或判断它是否属于本插件。
 */
public final class RouletteItems {

    private RouletteItems() {
    }

    /** @return 物品对应的行动；不是行动物品时返回 null */
    public static RouletteAction actionOf(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        if (LoadItem.INSTANCE.isThis(stack)) {
            return RouletteAction.LOAD;
        }
        if (ShootItem.INSTANCE.isThis(stack)) {
            return RouletteAction.SHOOT;
        }
        if (ProvokeItem.INSTANCE.isThis(stack)) {
            return RouletteAction.PROVOKE;
        }
        if (DodgeItem.INSTANCE.isThis(stack)) {
            return RouletteAction.DODGE;
        }
        if (SabotageItem.INSTANCE.isThis(stack)) {
            return RouletteAction.SABOTAGE;
        }
        return null;
    }

    /** 是否是本插件的任意物品（含号码牌与弹夹）。 */
    public static boolean isRouletteItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        return actionOf(stack) != null
                || NumberCardItem.INSTANCE.isThis(stack)
                || MagazineItem.INSTANCE.isThis(stack);
    }
}

package com.mczju.mcroulette.ui;

import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import com.mczju.mcroulette.McRoulette;
import com.mczju.mcroulette.core.RoulettePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本插件使用的箱子菜单：选择行动目标，或询问是否反击。
 *
 * <p>点击、关闭、超时都只会触发一次回调；{@link #dispose()} 用于游戏结束时静默关闭，
 * 不会触发任何回调。
 */
public final class ChoiceMenu implements InventoryHolder {

    private final Player owner;
    private final Inventory inventory;
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private final Runnable onCancel;

    private BukkitTask timeoutTask;
    private boolean done;

    private ChoiceMenu(Player owner, int rows, String title, Runnable onCancel) {
        this.owner = owner;
        this.onCancel = onCancel;
        this.inventory = Bukkit.createInventory(this, rows * 9, text(title));
    }

    /** 目标选择菜单：每个存活玩家占一个槽位。 */
    public static ChoiceMenu forTargets(Player owner, List<RoulettePlayer> targets,
                                        java.util.function.Consumer<RoulettePlayer> onSelect,
                                        Runnable onCancel) {
        int rows = Math.max(1, Math.min(6, (targets.size() + 8) / 9));
        ChoiceMenu menu = new ChoiceMenu(owner, rows, "<gold>选择行动目标", onCancel);
        int slot = 0;
        for (RoulettePlayer target : targets) {
            Player online = Bukkit.getPlayer(target.getUuid());
            menu.inventory.setItem(slot, head(target.getName(), online));
            RoulettePlayer captured = target;
            menu.actions.put(slot, () -> onSelect.accept(captured));
            slot++;
        }
        return menu;
    }

    /** 反击确认菜单：左键红色羊毛反击，右键屏障放弃。 */
    public static ChoiceMenu forReaction(Player owner, String saboteurName,
                                         Runnable onFight, Runnable onDecline) {
        ChoiceMenu menu = new ChoiceMenu(owner, 1, "<red>是否反击？", onDecline);
        menu.inventory.setItem(2, ItemBuilder.of(Material.RED_WOOL)
                .customName("<red><bold>立刻反击")
                .lore(List.of(
                        "<gray>向 <yellow>" + saboteurName + " <gray>射击一次",
                        "<gray>无论是否命中都会清空你的弹夹"))
                .build());
        menu.actions.put(2, onFight);
        menu.inventory.setItem(6, ItemBuilder.of(Material.BARRIER)
                .customName("<gray><bold>放弃反击")
                .lore(List.of("<gray>不使用这次射击机会"))
                .build());
        menu.actions.put(6, onDecline);
        return menu;
    }

    /** 打开界面，并在指定秒数后自动取消。 */
    public void open(int timeoutSeconds) {
        owner.openInventory(inventory);
        if (timeoutSeconds > 0) {
            timeoutTask = Bukkit.getScheduler().runTaskLater(
                    McRoulette.getInstance(), this::timeout, timeoutSeconds * 20L);
        }
    }

    /** 玩家点击槽位。 */
    public void click(int slot) {
        if (done) {
            return;
        }
        Runnable action = actions.get(slot);
        if (action == null) {
            return;
        }
        finish();
        action.run();
    }

    /** 玩家主动关闭界面。 */
    public void closed() {
        if (done) {
            return;
        }
        done = true;
        cancelTimeout();
        onCancel.run();
    }

    /** 界面超时。 */
    private void timeout() {
        if (done) {
            return;
        }
        timeoutTask = null;
        done = true;
        owner.closeInventory();
        onCancel.run();
    }

    /** 游戏结束时静默关闭，不触发回调。 */
    public void dispose() {
        done = true;
        cancelTimeout();
        if (owner.isOnline()) {
            owner.closeInventory();
        }
    }

    public boolean isOwnerOnline() {
        return owner.isOnline();
    }

    private void finish() {
        done = true;
        cancelTimeout();
        owner.closeInventory();
    }

    private void cancelTimeout() {
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }
        timeoutTask = null;
    }

    private static ItemStack head(String name, Player owner) {
        ItemStack item = ItemBuilder.of(Material.PLAYER_HEAD)
                .customName("<yellow>" + name)
                .lore(List.of("<gray>点击选择该玩家"))
                .build();
        if (owner != null) {
            item.editMeta(meta -> {
                if (meta instanceof SkullMeta skull) {
                    skull.setOwningPlayer(owner);
                }
            });
        }
        return item;
    }

    private static Component text(String mini) {
        return MiniMessage.miniMessage().deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

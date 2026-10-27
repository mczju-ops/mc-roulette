package com.mczju.mcroulette.core;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerQuitStrategy;
import com.github.mczjuops.mczjugamecore.player.strategy.impl.DoNothingPlayerQuitStrategy;
import com.mczju.mcroulette.McRoulette;
import com.mczju.mcroulette.item.DodgeItem;
import com.mczju.mcroulette.item.LoadItem;
import com.mczju.mcroulette.item.MagazineItem;
import com.mczju.mcroulette.item.NumberCardItem;
import com.mczju.mcroulette.item.ProvokeItem;
import com.mczju.mcroulette.item.SabotageItem;
import com.mczju.mcroulette.item.ShootItem;
import com.mczju.mcroulette.ui.ChoiceMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 轮盘赌的对局逻辑。
 *
 * <p>规则要点：每名玩家随机分配序号与 6 格弹夹，按「第 M 轮从序号 (M-1)%N+1 开始」的顺序轮流行动，
 * 每次行动从装填 / 射击 / 挑衅 / 闪避 / 破坏中选一个；每轮开始还会抽取一个随机事件。
 */
public final class RouletteGame extends AbstractGame {

    public static final String GAME_ID = "mc_roulette";

    public static final int MAGAZINE_SIZE = 6;
    public static final int MAX_PLAYERS = 12;
    public static final int MIN_PLAYERS = 2;

    private static final int TARGET_TIMEOUT_SECONDS = 30;
    private static final int REACTION_TIMEOUT_SECONDS = 15;
    private static final long GUARD_PERIOD_TICKS = 20L;

    private static final Set<RouletteGame> ACTIVE = new LinkedHashSet<>();

    private final Map<UUID, RoulettePlayer> players = new LinkedHashMap<>();
    private final Map<Integer, RoulettePlayer> seatMap = new HashMap<>();
    private final Map<UUID, ChoiceMenu> menus = new HashMap<>();

    private List<RoulettePlayer> order = new ArrayList<>();
    private int orderIndex;
    private int roundNumber = 1;
    private int totalPlayers;
    private int maxHealth = 3;
    private RouletteEvent event = RouletteEvent.NONE;
    private RoulettePlayer current;

    private BukkitTask guardTask;
    private boolean awaitingChoice;
    private boolean ending;
    private boolean started;
    private boolean cleaned;

    public RouletteGame() {
    }

    // ---------------------------------------------------------------- 基础信息

    @Override
    public String getId() {
        return GAME_ID;
    }

    @Override
    public GameMeta getGameMeta() {
        return new GameMeta("轮盘赌", Material.FIREWORK_ROCKET, "MCZJU", List.of(
                "六发弹夹的俄罗斯轮盘",
                "轮流装填、射击、挑衅、闪避与破坏",
                "最后的幸存者（或个人战 / 团队战的胜方）获胜"));
    }

    @Override
    public GameWaitStrategy getGameWaitStrategy() {
        return new DefaultGameWaitStrategy(this, MAX_PLAYERS, MIN_PLAYERS);
    }

    @Override
    public AbstractPlayerQuitStrategy getPlayerQuitStrategy() {
        // 中途退出的人按淘汰处理，剩下的玩家继续对局
        return new DoNothingPlayerQuitStrategy(this);
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected boolean onGameInit() {
        RouletteRoom room = roomOrNull();
        if (room == null) {
            return false;
        }
        if (room.health < 1) {
            logger.warn("房间 health 必须大于 0，当前为 " + room.health);
            return false;
        }
        return true;
    }

    @Override
    protected void onGameStart() {
        players.clear();
        seatMap.clear();
        menus.clear();
        order = new ArrayList<>();
        orderIndex = 0;
        roundNumber = 1;
        current = null;
        event = RouletteEvent.NONE;
        ending = false;
        started = true;
        cleaned = false;
        awaitingChoice = false;
        ACTIVE.add(this);

        RouletteRoom room = roomOrNull();
        maxHealth = room == null ? 3 : Math.max(1, room.health);

        List<PlayerExt> members = new ArrayList<>(getPlayers());
        Collections.shuffle(members);
        totalPlayers = members.size();

        if (totalPlayers < MIN_PLAYERS) {
            sender.warn("<red>人数不足 " + MIN_PLAYERS + " 人，无法开始轮盘赌");
            Bukkit.getScheduler().runTask(McRoulette.getInstance(), this::endNow);
            return;
        }

        int seat = 1;
        for (PlayerExt ext : members) {
            RoulettePlayer rp = new RoulettePlayer(ext.player().getUniqueId(), ext.player().getName(), seat);
            seat++;
            rp.setHealth(maxHealth);
            players.put(rp.getUuid(), rp);
            seatMap.put(rp.getSeat(), rp);
        }

        if (room != null && room.startLocation != null) {
            for (PlayerExt ext : members) {
                ext.player().teleport(room.startLocation);
            }
            play(room.startLocation, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        }

        sender.info("<gold><bold>轮盘赌开始！<gray>共 " + totalPlayers + " 人");
        sender.info("<gray>每人 <red>" + maxHealth + " <gray>点生命，弹夹 <yellow>" + MAGAZINE_SIZE + " <gray>个槽位");
        sender.info(seatList());
        if (room != null && room.teamMode) {
            announceTeams();
        }

        for (PlayerExt ext : members) {
            RoulettePlayer rp = players.get(ext.player().getUniqueId());
            if (rp != null) {
                giveLoadout(rp);
            }
        }

        startGuard();
        startRound(1);
    }

    @Override
    protected void onGameCancel() {
        shutdown();
    }

    @Override
    protected void onGameAbort() {
        shutdown();
    }

    @Override
    protected void onGameEnd() {
        shutdown();
    }

    // ---------------------------------------------------------------- 回合流程

    private void startRound(int round) {
        if (ending || !started) {
            return;
        }
        roundNumber = round;
        event = RouletteEvent.rollFor(totalPlayers);
        order = buildOrder(round);
        orderIndex = 0;

        if (order.isEmpty()) {
            sender.warn("<gray>场上已经没有可以行动的玩家");
            endNow();
            return;
        }

        sender.info("<gold><bold>—— 第 " + round + " 轮 ——");
        sender.info(event.announce());
        sender.info("<gray>本轮行动顺序：<yellow>" + orderText());
        beginTurn();
    }

    /** 按「从 (M-1)%N+1 开始，走到回到这个人」构建本轮行动顺序，跳过已淘汰的人。 */
    private List<RoulettePlayer> buildOrder(int round) {
        List<RoulettePlayer> result = new ArrayList<>();
        if (totalPlayers <= 0) {
            return result;
        }
        Set<Integer> aliveSeats = new HashSet<>();
        for (RoulettePlayer rp : players.values()) {
            if (rp.isAlive()) {
                aliveSeats.add(rp.getSeat());
            }
        }
        boolean reversed = event == RouletteEvent.REVERSE;
        for (int seat : TurnOrder.seats(round, totalPlayers, reversed, aliveSeats)) {
            RoulettePlayer rp = seatMap.get(seat);
            if (rp != null) {
                result.add(rp);
            }
        }
        return result;
    }

    /** 本轮顺序的展示文本，便于确认逆流是否生效。 */
    private String orderText() {
        StringBuilder sb = new StringBuilder();
        for (RoulettePlayer rp : order) {
            if (sb.length() > 0) {
                sb.append(" <dark_gray>→ <yellow>");
            }
            sb.append("#").append(rp.getSeat());
        }
        return sb.toString();
    }

    private void beginTurn() {
        // onGameStart 内状态还是 STATING，此时也要正常开出第一回合
        if (ending || !started || !isActive()) {
            return;
        }
        // 上一回合遗留的界面一律静默关闭，避免旧回调影响新回合
        closeMenus();

        while (orderIndex < order.size() && !isTurnable(order.get(orderIndex))) {
            orderIndex++;
        }
        if (orderIndex >= order.size()) {
            startRound(roundNumber + 1);
            return;
        }

        current = order.get(orderIndex);
        awaitingChoice = false;
        // 闪避只保护到本人下一个回合开始
        current.setDodging(false);
        refreshItems(current);

        sender.info("<gray>第 <gold>" + roundNumber + " <gray>轮 · 第 <gold>" + (orderIndex + 1)
                + " <gray>回合 · 行动者：<yellow>" + current.getName() + " <dark_gray>(#" + current.getSeat() + ")");
        sender.info("<gray>生命 <red>" + Math.max(0, current.getHealth())
                + " <gray>· 弹夹 <yellow>" + current.getBullets() + "<gray>/" + MAGAZINE_SIZE);

        Player player = online(current);
        if (player != null) {
            PlayerExt ext = new PlayerExt(player);
            ext.actionBarSender().info("<gold>你的回合：右键羊毛行动");
            play(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.5f);
            player.getWorld().spawnParticle(Particle.END_ROD,
                    player.getLocation().clone().add(0.0, 1.0, 0.0), 15, 0.4, 0.6, 0.4, 0.02);
        }

        if (event == RouletteEvent.MISFIRE) {
            sender.warn("<red>走火！本轮每名玩家只能选择射击");
        } else if (event == RouletteEvent.TRUCE) {
            sender.warn("<blue>非攻！本轮不能射击或挑衅");
        }
    }

    /** 当前玩家的行动结束：结算陨星，检查胜负，然后轮到下一个人。 */
    private void finishAction() {
        if (ending || !started || !isRunning() || current == null) {
            return;
        }
        applyMeteor(current);
        if (checkVictory()) {
            return;
        }
        orderIndex++;
        beginTurn();
    }

    // ---------------------------------------------------------------- 行动入口

    /** 玩家右键了行动物品。 */
    public void handleItemUse(PlayerExt ext, RouletteAction action) {
        if (!started || ending || !isRunning()) {
            return;
        }
        RoulettePlayer rp = players.get(ext.player().getUniqueId());
        if (rp == null) {
            return;
        }
        if (!rp.isAlive()) {
            ext.sender().warn("你已被淘汰，无法行动");
            return;
        }
        if (awaitingChoice) {
            ext.sender().warn("请先完成当前的选择");
            return;
        }
        if (current != rp) {
            ext.sender().warn("现在不是你的回合");
            return;
        }
        if (action == null) {
            ext.sender().info("右键使用羊毛进行行动");
            return;
        }
        if (event == RouletteEvent.MISFIRE && action != RouletteAction.SHOOT) {
            ext.sender().warn("<red>走火！本轮你只能射击");
            return;
        }
        if (event == RouletteEvent.TRUCE
                && (action == RouletteAction.SHOOT || action == RouletteAction.PROVOKE)) {
            ext.sender().warn("<blue>非攻！本轮不能射击或挑衅");
            return;
        }

        switch (action) {
            case LOAD:
                if (rp.getBullets() >= MAGAZINE_SIZE) {
                    ext.sender().warn("弹夹已满，无法装填");
                    return;
                }
                rp.setBullets(rp.getBullets() + 1);
                sender.info("<yellow>" + rp.getName() + " <gray>装填了一发子弹 <dark_gray>(<yellow>"
                        + rp.getBullets() + "<gray>/" + MAGAZINE_SIZE + ")");
                effectsLoad(rp);
                refreshItems(rp);
                finishAction();
                break;
            case DODGE:
                rp.setDodging(true);
                sender.info("<aqua>" + rp.getName() + " <gray>进入闪避状态 <dark_gray>(50% 闪避，直到下一个自己的回合)");
                effectsDodge(rp);
                finishAction();
                break;
            case SHOOT:
                openTargetMenu(rp, target -> {
                    performShot(rp, target);
                    finishAction();
                });
                break;
            case PROVOKE:
                openTargetMenu(rp, target -> {
                    performProvoke(rp, target);
                    finishAction();
                });
                break;
            case SABOTAGE:
                openTargetMenu(rp, target -> resolveSabotage(rp, target));
                break;
            default:
                break;
        }
    }

    /** 右键号码牌 / 弹夹时显示自己的状态。 */
    public void showStatus(PlayerExt ext) {
        RoulettePlayer rp = players.get(ext.player().getUniqueId());
        if (rp == null) {
            return;
        }
        ext.sender().info("<gray>序号 <yellow>#" + rp.getSeat()
                + " <gray>· 生命 <red>" + Math.max(0, rp.getHealth()) + "<gray>/" + maxHealth
                + " <gray>· 弹夹 <yellow>" + rp.getBullets() + "<gray>/" + MAGAZINE_SIZE
                + (rp.isDodging() ? " <aqua>· 闪避中" : "")
                + (rp.isAlive() ? "" : " <dark_gray>· 已淘汰"));
        if (event != RouletteEvent.NONE) {
            ext.sender().info(event.announce());
        }
        ext.sender().info("<gray>当前回合：<yellow>" + (current == null ? "无" : current.getName()));
    }

    // ---------------------------------------------------------------- 目标选择

    private void openTargetMenu(RoulettePlayer actor, java.util.function.Consumer<RoulettePlayer> onPick) {
        Player player = online(actor);
        if (player == null) {
            return;
        }
        List<RoulettePlayer> candidates = new ArrayList<>();
        for (RoulettePlayer rp : order) {
            if (!rp.isAlive() || rp.equals(actor) || online(rp) == null) {
                continue;
            }
            candidates.add(rp);
        }
        if (event == RouletteEvent.MYOPIA) {
            RoulettePlayer next = nextAfter(actor);
            candidates.removeIf(rp -> next == null || !rp.equals(next));
        }
        if (candidates.isEmpty()) {
            new PlayerExt(player).sender().warn("现在没有可以选择的目标");
            return;
        }

        awaitingChoice = true;
        UUID actorId = actor.getUuid();
        ChoiceMenu menu = ChoiceMenu.forTargets(player, candidates,
                target -> {
                    menus.remove(actorId);
                    awaitingChoice = false;
                    if (ending || !isRunning()) {
                        return;
                    }
                    if (target.isAlive() && actor.isAlive()) {
                        onPick.accept(target);
                    }
                },
                () -> {
                    menus.remove(actorId);
                    awaitingChoice = false;
                    if (ending || !isRunning()) {
                        return;
                    }
                    if (player.isOnline()) {
                        new PlayerExt(player).sender().info("已取消选择，请重新行动");
                    }
                });
        menus.put(actorId, menu);
        menu.open(TARGET_TIMEOUT_SECONDS);
    }

    /** 短视事件：当前行动者的「下一名玩家」。 */
    private RoulettePlayer nextAfter(RoulettePlayer actor) {
        int index = order.indexOf(actor);
        if (index < 0) {
            return null;
        }
        for (int i = 1; i < order.size(); i++) {
            RoulettePlayer rp = order.get((index + i) % order.size());
            if (rp.isAlive()) {
                return rp;
            }
        }
        return null;
    }

    private void openReaction(RoulettePlayer target, RoulettePlayer saboteur) {
        Player player = online(target);
        if (player == null) {
            finishAction();
            return;
        }
        awaitingChoice = true;
        UUID targetId = target.getUuid();
        ChoiceMenu menu = ChoiceMenu.forReaction(player, saboteur.getName(),
                () -> {
                    menus.remove(targetId);
                    awaitingChoice = false;
                    if (ending || !isRunning()) {
                        return;
                    }
                    if (target.isAlive()) {
                        performShot(target, saboteur);
                    }
                    finishAction();
                },
                () -> {
                    menus.remove(targetId);
                    awaitingChoice = false;
                    if (ending || !isRunning()) {
                        return;
                    }
                    sender.info("<gray>" + target.getName() + " <gray>放弃反击");
                    finishAction();
                });
        menus.put(targetId, menu);
        menu.open(REACTION_TIMEOUT_SECONDS);
        new PlayerExt(player).sender().warn("<red>对方破坏失败！<gray>你可以在 15 秒内选择是否反击");
    }

    // ---------------------------------------------------------------- 行动结算

    private void performShot(RoulettePlayer shooter, RoulettePlayer target) {
        if (ending || !isRunning() || !shooter.isAlive() || !target.isAlive()) {
            return;
        }
        int bullets = shooter.getBullets();
        double chance = bullets / (double) MAGAZINE_SIZE;
        if (target.isDodging()) {
            chance *= 0.5;
        }
        shooter.setBullets(0);
        refreshItems(shooter);

        sender.info("<red>" + shooter.getName() + " <gray>向 <yellow>" + target.getName()
                + " <gray>开枪 <dark_gray>(弹夹 " + bullets + "/" + MAGAZINE_SIZE
                + (target.isDodging() ? "，目标闪避中" : "") + ")");
        effectsShot(shooter, target);

        boolean hit = ThreadLocalRandom.current().nextDouble() < chance;
        if (hit) {
            sender.info("<dark_red>命中！");
            applyDamage(target);
        } else {
            sender.info("<gray>没有命中");
            effectsMiss(target);
        }
    }

    private void performProvoke(RoulettePlayer provoker, RoulettePlayer shooter) {
        if (ending || !isRunning() || !provoker.isAlive() || !shooter.isAlive()) {
            return;
        }
        int bullets = shooter.getBullets();
        double chance = bullets / (double) MAGAZINE_SIZE;
        if (provoker.isDodging()) {
            chance *= 0.5;
        }
        shooter.setBullets(0);
        refreshItems(shooter);

        sender.info("<gold>" + provoker.getName() + " <gray>挑衅 <yellow>" + shooter.getName()
                + " <gray>，对方开枪还击 <dark_gray>(弹夹 " + bullets + "/" + MAGAZINE_SIZE + ")");
        effectsShot(shooter, provoker);

        boolean hit = ThreadLocalRandom.current().nextDouble() < chance;
        if (hit) {
            sender.info("<dark_red>还击命中！");
            applyDamage(provoker);
            return;
        }

        sender.info("<gray>还击没有命中");
        effectsMiss(provoker);
        if (provoker.getBullets() < MAGAZINE_SIZE) {
            provoker.setBullets(provoker.getBullets() + 1);
            sender.info("<aqua>" + provoker.getName() + " <gray>的弹夹增加一发子弹 <dark_gray>(<yellow>"
                    + provoker.getBullets() + "<gray>/" + MAGAZINE_SIZE + ")");
            refreshItems(provoker);
        } else {
            sender.info("<gray>但 " + provoker.getName() + " 的弹夹已满，无法装填");
        }
    }

    private void resolveSabotage(RoulettePlayer saboteur, RoulettePlayer target) {
        if (ending || !isRunning() || !saboteur.isAlive()) {
            return;
        }
        boolean success = ThreadLocalRandom.current().nextDouble() < 0.5 && target.getBullets() > 0;
        if (success) {
            target.setBullets(target.getBullets() - 1);
            sender.info("<light_purple>" + saboteur.getName() + " <gray>破坏了 <yellow>" + target.getName()
                    + " <gray>的弹夹 <dark_gray>(剩余 <yellow>" + target.getBullets() + "<gray>/" + MAGAZINE_SIZE + ")");
            refreshItems(target);
            effectsSabotage(target, true);
            finishAction();
            return;
        }

        sender.info("<light_purple>" + saboteur.getName() + " <gray>试图破坏 <yellow>" + target.getName()
                + " <gray>的弹夹，但失败了");
        effectsSabotage(target, false);

        if (event == RouletteEvent.TRUCE) {
            sender.info("<blue>非攻生效，无法反击");
            finishAction();
            return;
        }
        if (!target.isAlive() || online(target) == null) {
            finishAction();
            return;
        }
        openReaction(target, saboteur);
    }

    // ---------------------------------------------------------------- 伤害与胜负

    private void applyDamage(RoulettePlayer target) {
        if (!target.isAlive()) {
            return;
        }
        int hp = target.getHealth() - 1;
        target.setHealth(hp);
        Player player = online(target);
        if (player != null) {
            play(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                    player.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.3, 0.5, 0.3, 0.05);
        }
        sender.info("<red>" + target.getName() + " <gray>失去 1 点生命，剩余 <red>" + Math.max(0, hp));
        refreshItems(target);
        if (hp <= 0) {
            eliminate(target);
        }
    }

    private void eliminate(RoulettePlayer target) {
        if (!target.isAlive()) {
            return;
        }
        target.setAlive(false);
        target.setHealth(0);
        target.setDodging(false);
        target.setBullets(0);

        sender.warn("<dark_red>" + target.getName() + " <gray>被淘汰了！");
        Player player = online(target);
        if (player != null) {
            player.getInventory().clear();
            play(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            player.getWorld().spawnParticle(Particle.SMOKE,
                    player.getLocation().clone().add(0.0, 1.0, 0.0), 30, 0.4, 0.6, 0.4, 0.05);
            player.showTitle(Title.title(text("<dark_red>你被淘汰了"), text("<gray>本局结束前请观战")));
        }
    }

    /** 陨星：回合结束时 1/3 概率掉血，闪避对它也生效。 */
    private void applyMeteor(RoulettePlayer target) {
        if (event != RouletteEvent.METEOR || !target.isAlive()) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= 1.0 / 3.0) {
            sender.info("<gray>陨星从 <yellow>" + target.getName() + " <gray>头顶掠过，没有造成伤害");
            return;
        }
        if (target.isDodging() && ThreadLocalRandom.current().nextDouble() < 0.5) {
            sender.info("<aqua>" + target.getName() + " <gray>闪避了陨星的伤害");
            effectsDodge(target);
            return;
        }
        sender.info("<dark_purple>陨星坠落，砸中了 <yellow>" + target.getName());
        Player player = online(target);
        if (player != null) {
            play(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.8f);
            player.getWorld().spawnParticle(Particle.FLAME,
                    player.getLocation().clone().add(0.0, 1.0, 0.0), 25, 0.4, 0.6, 0.4, 0.08);
        }
        applyDamage(target);
    }

    private boolean checkVictory() {
        if (ending) {
            return true;
        }
        List<RoulettePlayer> alive = alivePlayers();
        if (alive.isEmpty()) {
            sender.warn("<gray>没有人活下来了，本局平局");
            endNow();
            return true;
        }

        RouletteRoom room = roomOrNull();
        if (room != null && room.teamMode) {
            long odd = alive.stream().filter(RoulettePlayer::isOddTeam).count();
            long even = alive.size() - odd;
            if (odd == 0 || even == 0) {
                if (odd == 0 && even == 0) {
                    sender.warn("<gray>两队同归于尽，平局");
                } else {
                    sender.success("<gold>" + (odd > 0 ? "奇数队" : "偶数队") + " <gray>获胜！");
                    effectsWin(alive.get(0));
                }
                endNow();
                return true;
            }
        } else if (alive.size() == 1) {
            RoulettePlayer winner = alive.get(0);
            sender.success("<gold>" + winner.getName() + " <gray>赢得了本局轮盘赌！");
            effectsWin(winner);
            endNow();
            return true;
        }
        return false;
    }

    private void endNow() {
        if (ending) {
            return;
        }
        ending = true;
        MCZJUGameCore.getGameManager().endGame(this);
    }

    // ---------------------------------------------------------------- 看护 / 清理

    private void startGuard() {
        stopGuard();
        guardTask = Bukkit.getScheduler().runTaskTimer(
                McRoulette.getInstance(), this::guardTick, GUARD_PERIOD_TICKS, GUARD_PERIOD_TICKS);
    }

    private void stopGuard() {
        if (guardTask != null && !guardTask.isCancelled()) {
            guardTask.cancel();
        }
        guardTask = null;
    }

    /** 每秒检查一次：关闭掉线玩家的菜单、把离线玩家按淘汰处理、当前行动者失效时轮转回合。 */
    private void guardTick() {
        if (ending || !started || !isRunning()) {
            stopGuard();
            return;
        }

        for (ChoiceMenu menu : new ArrayList<>(menus.values())) {
            if (!menu.isOwnerOnline()) {
                menu.closed();
            }
            if (ending) {
                return;
            }
        }

        for (RoulettePlayer rp : new ArrayList<>(players.values())) {
            if (rp.isAlive() && !isParticipant(rp)) {
                rp.setAlive(false);
                rp.setHealth(0);
                rp.setDodging(false);
                rp.setBullets(0);
                sender.warn("<gray>" + rp.getName() + " <gray>离开了游戏，按淘汰处理");
            }
        }

        if (checkVictory()) {
            return;
        }
        if (current == null || !isTurnable(current)) {
            orderIndex++;
            beginTurn();
        }
    }

    private void shutdown() {
        ending = true;
        started = false;
        stopGuard();
        closeMenus();

        if (!cleaned) {
            cleaned = true;
            RouletteRoom room = roomOrNull();
            Location exit = room == null ? null : room.exitLocation;
            List<PlayerExt> members = new ArrayList<>(getPlayers());
            for (PlayerExt ext : members) {
                Player player = ext.player();
                if (!player.isOnline()) {
                    continue;
                }
                // 只清理仍属于本局的玩家，避免误清大厅或其它游戏的背包
                if (new PlayerExt(player).getGame() != this) {
                    continue;
                }
                player.getInventory().clear();
                ext.resetState();
            }
            if (exit != null && !members.isEmpty()) {
                for (PlayerExt ext : members) {
                    Player player = ext.player();
                    if (!player.isOnline()) {
                        continue;
                    }
                    Bukkit.getScheduler().runTaskLater(McRoulette.getInstance(), () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (new PlayerExt(player).getGame() != null) {
                            return;
                        }
                        player.teleport(exit);
                    }, 100L);
                }
                sender.info("<gray>5 秒后返回集合点");
            }
        }
        ACTIVE.remove(this);
    }

    /** 插件被禁用时调用：不触发任何游戏状态转换，只释放资源。 */
    public void dispose() {
        ending = true;
        started = false;
        stopGuard();
        closeMenus();
        ACTIVE.remove(this);
    }

    private void closeMenus() {
        for (ChoiceMenu menu : new ArrayList<>(menus.values())) {
            menu.dispose();
        }
        menus.clear();
        awaitingChoice = false;
    }

    // ---------------------------------------------------------------- 物品

    private void giveLoadout(RoulettePlayer rp) {
        Player player = online(rp);
        if (player == null) {
            return;
        }
        player.getInventory().clear();
        player.getInventory().setItem(0, numberCard(rp));
        player.getInventory().setItem(1, LoadItem.INSTANCE.getItem());
        player.getInventory().setItem(2, ShootItem.INSTANCE.getItem());
        player.getInventory().setItem(3, ProvokeItem.INSTANCE.getItem());
        player.getInventory().setItem(4, DodgeItem.INSTANCE.getItem());
        player.getInventory().setItem(5, SabotageItem.INSTANCE.getItem());
        player.getInventory().setItem(8, magazine(rp));
    }

    private ItemStack numberCard(RoulettePlayer rp) {
        ItemStack item = NumberCardItem.INSTANCE.getItem();
        item.editMeta(meta -> meta.lore(List.of(
                text("你的序号是 <yellow>" + rp.getSeat()),
                text("生命 <red>" + Math.max(0, rp.getHealth()) + " <gray>/ " + maxHealth))));
        return item;
    }

    private ItemStack magazine(RoulettePlayer rp) {
        ItemStack item = MagazineItem.INSTANCE.getItem();
        item.editMeta(meta -> meta.lore(List.of(
                text("装填弹药数 <yellow>" + rp.getBullets() + "<gray>/" + MAGAZINE_SIZE))));
        return item;
    }

    private void refreshItems(RoulettePlayer rp) {
        Player player = online(rp);
        if (player == null) {
            return;
        }
        player.getInventory().setItem(0, numberCard(rp));
        player.getInventory().setItem(8, magazine(rp));
    }

    // ---------------------------------------------------------------- 查询 / 工具

    private boolean isRunning() {
        return getState() == GameState.RUNNING;
    }

    /** 对局进行中（含 onGameStart 所在的 STATING 阶段）。 */
    private boolean isActive() {
        GameState state = getState();
        return state == GameState.RUNNING || state == GameState.STATING;
    }

    private boolean isTurnable(RoulettePlayer rp) {
        return rp != null && rp.isAlive() && online(rp) != null;
    }

    private boolean isParticipant(RoulettePlayer rp) {
        Player player = Bukkit.getPlayer(rp.getUuid());
        if (player == null || !player.isOnline()) {
            return false;
        }
        return new PlayerExt(player).getGame() == this;
    }

    private Player online(RoulettePlayer rp) {
        if (rp == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(rp.getUuid());
        return player != null && player.isOnline() ? player : null;
    }

    private List<RoulettePlayer> alivePlayers() {
        List<RoulettePlayer> result = new ArrayList<>();
        for (RoulettePlayer rp : players.values()) {
            if (rp.isAlive()) {
                result.add(rp);
            }
        }
        return result;
    }

    private List<RoulettePlayer> sortedBySeat() {
        Map<Integer, RoulettePlayer> sorted = new java.util.TreeMap<>(seatMap);
        return new ArrayList<>(sorted.values());
    }

    private String seatList() {
        StringBuilder sb = new StringBuilder("<gray>序号：");
        for (RoulettePlayer rp : sortedBySeat()) {
            sb.append("<yellow>#").append(rp.getSeat()).append(" <white>").append(rp.getName())
                    .append(" <dark_gray>| ");
        }
        return sb.toString();
    }

    private void announceTeams() {
        StringBuilder odd = new StringBuilder("<gold>奇数队：");
        StringBuilder even = new StringBuilder("<gold>偶数队：");
        for (RoulettePlayer rp : sortedBySeat()) {
            if (rp.isOddTeam()) {
                odd.append("<white>#").append(rp.getSeat()).append(" ").append(rp.getName()).append(" ");
            } else {
                even.append("<white>#").append(rp.getSeat()).append(" ").append(rp.getName()).append(" ");
            }
        }
        sender.info(odd.toString());
        sender.info(even.toString());
    }

    private RouletteRoom roomOrNull() {
        if (getGameRoom() instanceof RouletteRoom room) {
            return room;
        }
        return null;
    }

    /** 供插件禁用时清理所有仍在进行中的对局。 */
    public static Set<RouletteGame> activeGames() {
        return Set.copyOf(ACTIVE);
    }

    // ---------------------------------------------------------------- 表现

    private void effectsLoad(RoulettePlayer rp) {
        Player player = online(rp);
        if (player == null) {
            return;
        }
        play(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 1.4f);
        player.getWorld().spawnParticle(Particle.SMOKE,
                player.getLocation().clone().add(0.0, 1.0, 0.0), 6, 0.2, 0.3, 0.2, 0.02);
    }

    private void effectsDodge(RoulettePlayer rp) {
        Player player = online(rp);
        if (player == null) {
            return;
        }
        play(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.3f);
        player.getWorld().spawnParticle(Particle.CLOUD,
                player.getLocation().clone().add(0.0, 1.0, 0.0), 12, 0.3, 0.4, 0.3, 0.03);
    }

    private void effectsShot(RoulettePlayer shooter, RoulettePlayer target) {
        Player from = online(shooter);
        Player to = online(target);
        if (from == null) {
            return;
        }
        play(from.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.2f);
        if (to != null) {
            trail(from.getEyeLocation(), to.getEyeLocation());
        }
    }

    private void trail(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        double distance = from.distance(to);
        if (distance < 0.1) {
            return;
        }
        Vector direction = to.toVector().subtract(from.toVector()).normalize().multiply(0.4);
        for (double step = 0; step < distance; step += 0.4) {
            Location point = from.clone().add(direction.clone().multiply(step / 0.4));
            from.getWorld().spawnParticle(Particle.CRIT, point, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void effectsMiss(RoulettePlayer target) {
        Player player = online(target);
        if (player == null) {
            return;
        }
        play(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
        player.getWorld().spawnParticle(Particle.SMOKE,
                player.getLocation().clone().add(0.0, 1.0, 0.0), 8, 0.3, 0.4, 0.3, 0.03);
    }

    private void effectsSabotage(RoulettePlayer target, boolean success) {
        Player player = online(target);
        if (player == null) {
            return;
        }
        if (success) {
            play(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.4f);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    player.getLocation().clone().add(0.0, 1.0, 0.0), 14, 0.3, 0.4, 0.3, 0.05);
        } else {
            play(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
            player.getWorld().spawnParticle(Particle.SMOKE,
                    player.getLocation().clone().add(0.0, 1.0, 0.0), 8, 0.3, 0.4, 0.3, 0.03);
        }
    }

    private void effectsWin(RoulettePlayer winner) {
        Player player = online(winner);
        if (player == null) {
            return;
        }
        play(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().clone().add(0.0, 1.0, 0.0), 60, 0.5, 1.0, 0.5, 0.2);
    }

    private void play(Location location, Sound sound, float volume, float pitch) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, sound, volume, pitch);
    }

    private static Component text(String mini) {
        return MiniMessage.miniMessage().deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }
}

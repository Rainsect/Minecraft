package com.rain.automc;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Baritone 走路的薄封装。
 *
 * <p>单独成类是为了「没有装 Baritone 时不炸」：只要不执行这里的代码，JVM 不会去加载
 * baritone.* 的类。{@link #available()} 用类名探测，探测失败就走 EFWalker 老实现。
 *
 * <p>参数（允许破坏 / 允许搭桥 / 疾跑）直接改 Baritone 的全局设置，开始时记录旧值，
 * 走完（或停止）后还原，不影响玩家之后手打的 {@code #goto}。
 */
@SideOnly(value = Side.CLIENT)
final class BaritoneWalker {
    private static Boolean oldBreak;
    private static Boolean oldPlace;
    private static Boolean oldSprint;

    private BaritoneWalker() {
    }

    static boolean available() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    /**
     * 寻路到某个方块（站在该方块上）。
     *
     * @return null 表示已开始；否则是给玩家看的原因
     */
    static String go(int x, int y, int z, boolean allowBreak, boolean allowPlace, boolean allowSprint) {
        try {
            Settings s = BaritoneAPI.getSettings();
            if (oldBreak == null) {
                oldBreak = s.allowBreak.value;
                oldPlace = s.allowPlace.value;
                oldSprint = s.allowSprint.value;
            }
            s.allowBreak.value = Boolean.valueOf(allowBreak);
            s.allowPlace.value = Boolean.valueOf(allowPlace);
            s.allowSprint.value = Boolean.valueOf(allowSprint);
            IBaritone b = baritone();
            if (b == null) {
                return "§cBaritone 还没有初始化";
            }
            b.getCustomGoalProcess().setGoalAndPath(new GoalBlock(x, y, z));
            return null;
        } catch (Throwable t) {
            return "§cBaritone 寻路启动失败：" + t.getClass().getSimpleName();
        }
    }

    static boolean isPathing() {
        try {
            IBaritone b = baritone();
            return b != null && b.getPathingBehavior().isPathing();
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isActive() {
        try {
            IBaritone b = baritone();
            return b != null && b.getCustomGoalProcess().isActive();
        } catch (Throwable t) {
            return false;
        }
    }

    static void cancel() {
        try {
            IBaritone b = baritone();
            if (b != null) {
                b.getPathingBehavior().cancelEverything();
            }
        } catch (Throwable ignored) {
            // 没有 Baritone 或已卸载，忽略
        }
    }

    static void restore() {
        if (oldBreak == null) {
            return;
        }
        try {
            Settings s = BaritoneAPI.getSettings();
            s.allowBreak.value = oldBreak;
            s.allowPlace.value = oldPlace;
            s.allowSprint.value = oldSprint;
        } catch (Throwable ignored) {
            // 忽略
        }
        oldBreak = null;
        oldPlace = null;
        oldSprint = null;
    }
}

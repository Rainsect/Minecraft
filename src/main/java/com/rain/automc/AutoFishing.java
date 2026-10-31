package com.rain.automc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/*
 * 自动钓鱼：抛竿 → 等鱼漂下沉 → 收竿 → 重抛。
 *
 * 判定参照「自动钓鱼 MOD」的 AutoFish：鱼漂在水里、水平速度几乎为 0 且竖直速度明显向下
 * （motionY < -0.05）视为咬钩；另外用「相对水面基准下落超过阈值」作兜底，
 * 避免某些情况客户端拿不到速度变化。全部走游戏内 API，不依赖窗口焦点。
 */
@SideOnly(value=Side.CLIENT)
public final class AutoFishing {
    /** 抛竿后先等这么久再判定，避开飞行与落水阶段。 */
    private static final int BITE_SETTLE_MS = 300;
    /** 鱼漂消失 / 没落到水里时的重抛间隔。 */
    private static final int RECAST_MS = 2500;
    /** 两次右键之间的最小间隔，防止一帧内连点。 */
    private static final int MIN_USE_GAP_MS = 250;
    /** 剩余耐久低于此值就换竿（没有再就停手），避免把鱼竿用断。 */
    private static final int MIN_DURABILITY = 2;
    /** 鱼漂相对基准下落超过这个距离，也当作咬钩。 */
    private static final double DIP_THRESHOLD = 0.25;

    private static boolean active;
    private static boolean single;
    private static int reelDelayMs = 800;
    private static int timeoutMs = 30000;
    /** 开始钓鱼时选中的快捷栏槽位；玩家自己切走后立刻停手，不再抢槽位 */
    private static int rodSlot = -1;

    /** 0=待抛竿 1=已抛竿等咬钩 2=收竿后等重抛。 */
    private static int state;
    private static long stateAt;
    private static long castAt;
    private static long lastUseAt;
    private static double floatY;

    private AutoFishing() {
    }

    public static boolean isBusy() {
        return active;
    }

    /** 脚本动作入口。返回 null 表示已接管（由 tick 推进），否则返回立即失败原因。 */
    public static String start(String mode, int reelDelay, int timeoutSec) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (active) {
            return "§e自动钓鱼已在运行";
        }
        if (p == null || mc.world == null) {
            return "§c没有进入世界";
        }
        if (!ensureRodOnStart(p)) {
            return "§e快捷栏里没有可用的鱼竿";
        }
        single = !"持续".equals(mode);
        reelDelayMs = Math.max(200, reelDelay);
        timeoutMs = Math.max(5000, timeoutSec * 1000);
        state = 0;
        stateAt = 0L;
        castAt = 0L;
        lastUseAt = 0L;
        floatY = 0.0;
        active = true;
        return null;
    }

    public static void stop() {
        if (active) {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP p = mc.player;
            if (p != null && mc.world != null && p.fishEntity != null && holdingRod(p)) {
                // 停止时把鱼线收回来，否则会把鱼漂留在水里，影响后续动作
                // 只在还手持鱼竿时收线，否则右键会变成放方块
                ClickActions.useItem();
            }
        }
        active = false;
        rodSlot = -1;
        state = 0;
        stateAt = 0L;
        castAt = 0L;
        lastUseAt = 0L;
        floatY = 0.0;
    }

    public static void tick() {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null || mc.world == null) {
            stop();
            return;
        }
        if (p.inventory.currentItem != rodSlot) {
            // 玩家自己切了快捷栏（数字键 / 滚轮）：直接停手，绝不每 tick 抢回槽位发换槽包
            finish("§e已切换物品栏，自动钓鱼停止");
            return;
        }
        if (!holdingRod(p)) {
            // 竿子用完了：还能换一根就换，否则停手
            if (!switchRod(p)) {
                finish("§e没有可用的鱼竿，自动钓鱼停止");
                return;
            }
        }
        long now = System.currentTimeMillis();
        EntityFishHook hook = p.fishEntity;

        if (state == 2) {
            if (now - stateAt >= (long)reelDelayMs) {
                state = 0;
            }
            return;
        }
        if (state == 0) {
            if (hook == null) {
                if (now - lastUseAt < (long)MIN_USE_GAP_MS) {
                    return;
                }
                ClickActions.useItem();
                lastUseAt = now;
                castAt = now;
            } else {
                castAt = now;
            }
            state = 1;
            stateAt = now;
            floatY = hook == null ? 0.0 : hook.posY;
            return;
        }
        // state == 1：已抛竿，等咬钩 / 处理异常
        if (hook == null) {
            // 鱼漂没了（实体被清除、被人收走等），等一会重抛
            if (now - castAt > (long)RECAST_MS) {
                state = 2;
                stateAt = now - (long)reelDelayMs;
            }
            return;
        }
        if (hook.isInWater()) {
            floatY = Math.max(floatY, hook.posY);
        }
        if (now - castAt < (long)BITE_SETTLE_MS) {
            return;
        }
        if (biting(hook)) {
            if (now - lastUseAt < (long)MIN_USE_GAP_MS) {
                return;
            }
            ClickActions.useItem(); // 收竿
            lastUseAt = now;
            if (single) {
                finish("§a钓到一条，自动钓鱼结束");
                return;
            }
            state = 2;
            stateAt = now;
            return;
        }
        boolean stuck = now - castAt > (long)RECAST_MS && !hook.isInWater();
        boolean timeout = now - castAt > (long)timeoutMs;
        if (stuck || timeout) {
            if (now - lastUseAt < (long)MIN_USE_GAP_MS) {
                return;
            }
            ClickActions.useItem(); // 收竿重抛
            lastUseAt = now;
            state = 2;
            stateAt = now;
        }
    }

    /** 鱼漂咬钩：水平几乎静止且竖直速度向下，或相对水面基准明显下沉。 */
    private static boolean biting(EntityFishHook hook) {
        if (!hook.isInWater()) {
            return false;
        }
        boolean dip = Math.abs(hook.motionX) < 0.02 && Math.abs(hook.motionZ) < 0.02
                && hook.motionY < -0.05;
        boolean sunk = floatY - hook.posY > DIP_THRESHOLD;
        return dip || sunk;
    }

    /** 开局先确认手上有鱼竿；没有就看快捷栏能不能换到。 */
    private static boolean ensureRodOnStart(EntityPlayerSP p) {
        if (holdingRod(p)) {
            rodSlot = p.inventory.currentItem;
            return true;
        }
        return switchRod(p);
    }

    private static boolean holdingRod(EntityPlayerSP p) {
        ItemStack s = p.getHeldItemMainhand();
        return !s.isEmpty() && s.getItem() instanceof ItemFishingRod && durabilityLeft(s) > MIN_DURABILITY;
    }

    private static boolean switchRod(EntityPlayerSP p) {
        for (int i = 0; i < 9; ++i) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() instanceof ItemFishingRod && durabilityLeft(s) > MIN_DURABILITY) {
                ClickActions.selectHotbar(i + 1);
                rodSlot = i;
                return true;
            }
        }
        return false;
    }

    private static int durabilityLeft(ItemStack s) {
        return s.getMaxDamage() - s.getItemDamage();
    }

    private static void finish(String msg) {
        stop();
        PlayerActions.chatSelf(msg);
    }
}

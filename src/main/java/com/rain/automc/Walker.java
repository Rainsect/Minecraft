package com.rain.automc;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 走路总入口：优先用 Baritone（与 {@code #goto x y z} 同一套寻路，真实行走、能爬台阶绕障碍），
 * 没装 Baritone 时回退到 EFWalker 的老实现。
 *
 * <p>「捡掉落物」仍走 EFWalker：它要逐个小跑追实体，用不到 Baritone 的远距离寻路。
 */
@SideOnly(value = Side.CLIENT)
public final class Walker {
    private static final long TIMEOUT_MS = 300000L;
    private static final double ARRIVE = 1.5;

    private static boolean baritoneBusy;
    private static long startedAt;
    private static double goalX;
    private static double goalY;
    private static double goalZ;
    private static boolean checked;
    private static boolean hasBaritone;
    private static String lastMessage = "";

    private Walker() {
    }

    public static boolean isBusy() {
        return baritoneBusy || EFWalker.isBusy();
    }

    public static String status() {
        if (baritoneBusy) {
            return "Baritone 寻路 " + (int)goalX + "," + (int)goalY + "," + (int)goalZ;
        }
        return EFWalker.status();
    }

    public static String lastMessage() {
        String m = lastMessage;
        lastMessage = "";
        return m;
    }

    static boolean baritoneReady() {
        if (!checked) {
            checked = true;
            try {
                Class.forName("baritone.api.BaritoneAPI");
                hasBaritone = true;
            } catch (Throwable t) {
                hasBaritone = false;
            }
        }
        return hasBaritone;
    }

    public static String startCoordWalk(EntityPlayerSP p, double x, double y, double z, double tol,
                                        boolean allowBreak, boolean allowPlace, boolean allowSprint) {
        double tol2 = Math.max(0.3, tol);
        double dx = x - p.posX;
        double dz = z - p.posZ;
        if (Math.sqrt(dx * dx + dz * dz) <= tol2) {
            return "§a已在目标坐标附近";
        }
        if (baritoneReady()) {
            String r = BaritoneWalker.go((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z),
                    allowBreak, allowPlace, allowSprint);
            if (r == null) {
                begin(x, y, z);
                return null;
            }
            lastMessage = r;
        }
        return EFWalker.startCoordWalk(p, x, y, z, tol);
    }

    public static String startBlockWalk(EntityPlayerSP p, String blockName, int rx, int ry, int rz,
                                        boolean allowBreak, boolean allowPlace, boolean allowSprint) {
        if (!baritoneReady()) {
            return EFWalker.startTargetWalk(p, blockName, rx, ry, rz);
        }
        net.minecraft.block.Block block = EFWalker.resolveBlock(blockName);
        if (block == null) {
            return "§c方块名无效：" + blockName;
        }
        BlockPos pos = EFWalker.scanTargets(p, block, rx, ry, rz);
        if (pos == null) {
            return "§e" + rx + "×" + ry + "×" + rz + " 范围内没有 " + blockName;
        }
        if (EFWalker.targetDistXZ(p, pos) <= 0.3) {
            return "§a已站在目标方块上";
        }
        double d = (double)(pos.getY() + 1) - p.posY;
        if (d > 1.3) {
            return "§c目标方块顶部比当前位置高 " + String.format("%.1f", d) + " 格，跳不上去，请靠近一点再试";
        }
        String r = BaritoneWalker.go(pos.getX(), pos.getY() + 1, pos.getZ(),
                allowBreak, allowPlace, allowSprint);
        if (r == null) {
            begin((double)pos.getX() + 0.5, (double)pos.getY() + 1.0, (double)pos.getZ() + 0.5);
            return null;
        }
        lastMessage = r;
        return EFWalker.startTargetWalk(p, blockName, rx, ry, rz);
    }

    public static String startItemWalk(EntityPlayerSP p, int rx, int ry, int rz) {
        return EFWalker.startItemWalk(p, rx, ry, rz);
    }

    private static void begin(double x, double y, double z) {
        baritoneBusy = true;
        startedAt = System.currentTimeMillis();
        goalX = x;
        goalY = y;
        goalZ = z;
        lastMessage = "";
    }

    /** 每个客户端 tick 调用：判断 Baritone 是否走完 / 超时。 */
    public static void tick() {
        if (!baritoneBusy) {
            return;
        }
        long now = System.currentTimeMillis();
        // Baritone 刚启动的头半秒可能还没进入寻路状态，别把它误判成已经走完
        if (now - startedAt < 500L) {
            return;
        }
        if (now - startedAt > TIMEOUT_MS) {
            stop();
            lastMessage = "§c寻路超时，已停止";
            PlayerActions.chatSelf(lastMessage);
            return;
        }
        EntityPlayerSP p = net.minecraft.client.Minecraft.getMinecraft().player;
        if (p == null) {
            stop();
            return;
        }
        double dx = goalX - p.posX;
        double dz = goalZ - p.posZ;
        boolean arrived = Math.sqrt(dx * dx + dz * dz) <= ARRIVE && Math.abs(goalY - p.posY) <= 2.0;
        if (arrived || (!BaritoneWalker.isPathing() && !BaritoneWalker.isActive())) {
            baritoneBusy = false;
            BaritoneWalker.restore();
            PlayerActions.stopMove();
            if (arrived) {
                lastMessage = "§a已到达目的地";
            }
        }
    }

    public static void stop() {
        if (baritoneBusy) {
            baritoneBusy = false;
            BaritoneWalker.cancel();
            BaritoneWalker.restore();
        }
        EFWalker.stopWalk();
        PlayerActions.stopMove();
    }
}

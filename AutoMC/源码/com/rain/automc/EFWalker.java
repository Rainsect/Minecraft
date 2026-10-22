package com.rain.automc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/*
 * 行走模块（移植自 endframe v2.2 / EndNav 方案，已验证手感）：
 * 到达判定=水平 0.3 格内；只靠撞墙跳；卡住 90/140 tick 放弃。
 * 目标方块由配置的方块名动态解析，不再硬编码末地门框架。
 */
@SideOnly(value=Side.CLIENT)
public class EFWalker {
    public static final EFWalker INSTANCE = new EFWalker();
    private static final int STATE_ITEM = 1;
    private static final int STATE_WAIT = 2;
    private static final int STATE_HOME = 3;
    private static final int STATE_TARGET = 4;
    private static final int STATE_COORD = 5;
    private static final int PICK_WAIT = 8;
    private static final int STUCK_LIMIT = 50;
    private static final int CLIMB_LIMIT = 100;
    private static final int TARGET_STUCK_LIMIT = 90;
    private static final int TARGET_CLIMB_LIMIT = 140;
    private static final int MAX_CYCLE = 600;
    private static final double REACH_ITEM = 0.8;
    private static final double REACH_HOME = 0.3;
    private static final double REACH_TARGET = 0.3;
    private static final double MAX_CLIMB = 1.3;
    private boolean running = false;
    private int state = 0;
    private double homeX;
    private double homeY;
    private double homeZ;
    private List<EntityItem> targets = null;
    private int idx = 0;
    private int waitTimer = 0;
    private int stuckTicks = 0;
    private int climbTicks = 0;
    private int cycleTicks = 0;
    private double lastDist = -1.0;
    private int lastJumpTick = -100;
    private BlockPos targetPos = null;
    private Block targetBlock = null;
    private String targetName = "";
    private double coordX;
    private double coordY;
    private double coordZ;
    private double coordTol = 0.5;

    public static boolean isBusy() {
        return EFWalker.INSTANCE.running;
    }

    public static String status() {
        if (!EFWalker.INSTANCE.running) {
            return "\u7a7a\u95f2";
        }
        switch (EFWalker.INSTANCE.state) {
            case STATE_ITEM: return "\u8ddf\u5411\u6389\u843d\u7269 " + (EFWalker.INSTANCE.idx + 1) + "/" + (EFWalker.INSTANCE.targets == null ? 0 : EFWalker.INSTANCE.targets.size());
            case STATE_WAIT: return "\u7b49\u5f85\u62fe\u53d6";
            case STATE_HOME: return "\u8fd4\u56de\u539f\u4f4d";
            case STATE_TARGET: return "\u8ddf\u5411\u76ee\u6807\u65b9\u5757 " + EFWalker.INSTANCE.targetName;
            case STATE_COORD: return "\u8d70\u5411\u5750\u6807 " + (int)EFWalker.INSTANCE.coordX + "," + (int)EFWalker.INSTANCE.coordY + "," + (int)EFWalker.INSTANCE.coordZ;
            default: return "\u7a7a\u95f2";
        }
    }

    public static String startItemWalk(EntityPlayerSP player, int rx, int ry, int rz) {
        if (INSTANCE.startItemCycle(player, rx, ry, rz)) {
            return null;
        }
        return "\u00a7e" + rx + "\u00d7" + ry + "\u00d7" + rz + " \u8303\u56f4\u5185\u6ca1\u6709\u6389\u843d\u7269";
    }

    public static String startTargetWalk(EntityPlayerSP player, String blockName, int rx, int ry, int rz) {
        Block block = EFWalker.resolveBlock(blockName);
        if (block == null) {
            return "\u00a7c\u65b9\u5757\u540d\u65e0\u6548\uff1a" + blockName;
        }
        BlockPos pos = EFWalker.scanTargets(player, block, rx, ry, rz);
        if (pos == null) {
            return "\u00a7e" + rx + "\u00d7" + ry + "\u00d7" + rz + " \u8303\u56f4\u5185\u6ca1\u6709 " + blockName;
        }
        if (EFWalker.targetDistXZ(player, pos) <= 0.3) {
            return "\u00a7a\u5df2\u7adf\u5728\u76ee\u6807\u65b9\u5757\u4e0a";
        }
        double d = (double)(pos.func_177956_o() + 1) - player.field_70163_u;
        if (d > 1.3) {
            return "\u00a7c\u76ee\u6807\u65b9\u5757\u9876\u90e8\u6bd4\u5f53\u524d\u4f4d\u7f6e\u9ad8 " + String.format("%.1f", d) + " \u683c\uff0c\u8df3\u4e0a\u4e0d\u53bb\uff0c\u8bf7\u9760\u8fd1\u4e00\u70b9\u518d\u8bd5";
        }
        INSTANCE.targetBlock = block;
        INSTANCE.targetName = blockName;
        INSTANCE.targetPos = pos;
        INSTANCE.state = STATE_TARGET;
        INSTANCE.running = true;
        INSTANCE.cycleTicks = 0;
        INSTANCE.lastDist = -1.0;
        INSTANCE.stuckTicks = 0;
        INSTANCE.climbTicks = 0;
        INSTANCE.lastJumpTick = -100;
        return "\u00a7a\u5f00\u59cb\u8ddf\u5411 " + blockName + " (" + pos.func_177958_n() + ", " + pos.func_177956_o() + ", " + pos.func_177952_p() + ")";
    }

    public static String startCoordWalk(EntityPlayerSP player, double x, double y, double z, double tol) {
        double dx = x - player.field_70165_t;
        double dz = z - player.field_70161_v;
        if (Math.sqrt(dx * dx + dz * dz) <= Math.max(0.3, tol)) {
            return "\u00a7a\u5df2\u5728\u76ee\u6807\u5750\u6807\u9644\u8fd1";
        }
        INSTANCE.coordX = x;
        INSTANCE.coordY = y;
        INSTANCE.coordZ = z;
        INSTANCE.coordTol = Math.max(0.3, tol);
        INSTANCE.state = STATE_COORD;
        INSTANCE.running = true;
        INSTANCE.cycleTicks = 0;
        INSTANCE.lastDist = -1.0;
        INSTANCE.stuckTicks = 0;
        INSTANCE.climbTicks = 0;
        INSTANCE.lastJumpTick = -100;
        return null;
    }

    public static Block resolveBlock(String name) {        if (name == null) {
            return null;
        }
        String n = name.trim();
        if (n.startsWith("minecraft:")) {
            n = n.substring("minecraft:".length());
        }
        if (n.isEmpty()) {
            return null;
        }
        return Block.func_149684_b(n);
    }

    public static void stopWalk() {
        EFWalker.INSTANCE.running = false;
        EFWalker.INSTANCE.state = 0;
        EFWalker.INSTANCE.targets = null;
        EFWalker.INSTANCE.targetPos = null;
        EFWalker.INSTANCE.lastDist = -1.0;
        EFWalker.INSTANCE.stuckTicks = 0;
        EFWalker.INSTANCE.climbTicks = 0;
        EFWalker.INSTANCE.cycleTicks = 0;
        EntityPlayerSP player = Minecraft.func_71410_x().field_71439_g;
        if (player != null) {
            EFWalker.stopMoving(player);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.func_71410_x();
        EntityPlayerSP player = mc.field_71439_g;
        if (player == null || mc.field_71441_e == null) {
            this.hardReset();
            return;
        }
        if (!this.running) {
            double d = player.field_70165_t - this.homeX;
            double d2 = player.field_70161_v - this.homeZ;
            if (d * d + d2 * d2 > 2.25) {
                this.homeX = player.field_70165_t;
                this.homeY = player.field_70163_u;
                this.homeZ = player.field_70161_v;
            }
            return;
        }
        if (player.func_110143_aJ() <= 0.0f) {
            EFWalker.stopWalk();
            return;
        }
        ++this.cycleTicks;
        if (this.cycleTicks > MAX_CYCLE) {
            if (this.state == STATE_ITEM || this.state == STATE_WAIT) {
                this.state = STATE_HOME;
                this.lastDist = -1.0;
                this.stuckTicks = 0;
                this.climbTicks = 0;
            } else {
                if (this.state == STATE_TARGET) {
                    EFWalker.tell(player, "\u00a7c\u8d70\u4e0d\u8fc7\u53bb\uff0c\u5df2\u505c\u6b62\uff08\u8d85\u65f6\uff09");
                }
                EFWalker.stopWalk();
            }
            return;
        }
        MovementInput movementInput = player.field_71158_b;
        if (movementInput != null) {
            movementInput.field_192832_b = 0.0f;
            movementInput.field_78902_a = 0.0f;
            movementInput.field_78901_c = false;
        }
        if (this.state == STATE_ITEM) {
            this.tickItem(player);
        } else if (this.state == STATE_WAIT) {
            EFWalker.stopMoving(player);
            --this.waitTimer;
            if (this.waitTimer <= 0) {
                ++this.idx;
                this.lastDist = -1.0;
                this.stuckTicks = 0;
                this.climbTicks = 0;
                if (this.targets == null || this.idx >= this.targets.size()) {
                    this.goHomeOrFinish(player);
                } else {
                    this.state = STATE_ITEM;
                }
            }
        } else if (this.state == STATE_HOME) {
            this.tickHome(player);
        } else if (this.state == STATE_TARGET) {
            this.tickTarget(player);
        } else if (this.state == STATE_COORD) {
            this.tickCoord(player);
        }
    }

    private boolean startItemCycle(EntityPlayerSP player, int rx, int ry, int rz) {
        AxisAlignedBB aabb = new AxisAlignedBB(player.field_70165_t - (double)rx, player.field_70163_u - (double)ry, player.field_70161_v - (double)rz, player.field_70165_t + (double)rx, player.field_70163_u + (double)ry, player.field_70161_v + (double)rz);
        List list = player.field_70170_p.func_72872_a(EntityItem.class, aabb);
        ArrayList<EntityItem> items = new ArrayList<EntityItem>();
        for (int i = 0; i < list.size(); ++i) {
            EntityItem entityItem = (EntityItem)list.get(i);
            if (entityItem == null || !entityItem.func_70089_S() || entityItem.func_174874_s()) continue;
            items.add(entityItem);
        }
        if (items.isEmpty()) {
            return false;
        }
        this.targets = items;
        this.idx = 0;
        this.state = STATE_ITEM;
        this.running = true;
        this.cycleTicks = 0;
        this.lastDist = -1.0;
        this.stuckTicks = 0;
        this.climbTicks = 0;
        this.lastJumpTick = -100;
        return true;
    }

    private void tickItem(EntityPlayerSP player) {
        if (this.targets == null || this.idx >= this.targets.size()) {
            this.goHomeOrFinish(player);
            return;
        }
        EntityItem item = this.targets.get(this.idx);
        if (item == null || !item.func_70089_S()) {
            ++this.idx;
            this.lastDist = -1.0;
            this.stuckTicks = 0;
            this.climbTicks = 0;
            return;
        }
        double d = this.walkTowards(player, item.field_70165_t, item.field_70163_u, item.field_70161_v, REACH_ITEM, 1.2, 2.0);
        boolean bl = this.lastDist >= 0.0 && this.lastDist - d >= 0.002;
        boolean onGround = player.field_70123_F;
        if (onGround) {
            ++this.climbTicks;
            if (this.climbTicks > CLIMB_LIMIT) {
                ++this.idx;
                this.lastDist = -1.0;
                this.stuckTicks = 0;
                this.climbTicks = 0;
                return;
            }
        } else {
            this.climbTicks = 0;
        }
        if (bl) {
            this.stuckTicks = 0;
        } else if (!onGround) {
            ++this.stuckTicks;
        }
        this.lastDist = d;
        if (this.stuckTicks > STUCK_LIMIT) {
            ++this.idx;
            this.lastDist = -1.0;
            this.stuckTicks = 0;
            this.climbTicks = 0;
            return;
        }
        if (d <= REACH_ITEM && Math.abs(item.field_70163_u - player.field_70163_u) < 1.8) {
            EFWalker.stopMoving(player);
            this.waitTimer = PICK_WAIT;
            this.state = STATE_WAIT;
        }
    }

    private void goHomeOrFinish(EntityPlayerSP player) {
        if (ConfigManager.get().returnHome) {
            this.state = STATE_HOME;
            this.lastDist = -1.0;
            this.stuckTicks = 0;
            this.climbTicks = 0;
        } else {
            EFWalker.stopWalk();
        }
    }

    private void tickHome(EntityPlayerSP player) {
        double d = this.walkTowards(player, this.homeX, this.homeY, this.homeZ, REACH_HOME, 1.2, 2.0);
        boolean bl = this.lastDist >= 0.0 && this.lastDist - d >= 0.002;
        boolean onGround = player.field_70123_F;
        if (onGround) {
            ++this.climbTicks;
            if (this.climbTicks > CLIMB_LIMIT) {
                EFWalker.stopWalk();
                return;
            }
        } else {
            this.climbTicks = 0;
        }
        if (bl) {
            this.stuckTicks = 0;
        } else if (!onGround) {
            ++this.stuckTicks;
        }
        this.lastDist = d;
        if (this.stuckTicks > STUCK_LIMIT) {
            EFWalker.stopWalk();
            return;
        }
        if (d <= REACH_HOME) {
            EFWalker.stopMoving(player);
            EFWalker.stopWalk();
        }
    }

    private void tickTarget(EntityPlayerSP player) {
        if (this.targetPos == null || this.targetBlock == null) {
            EFWalker.stopWalk();
            return;
        }
        if (player.field_70170_p.func_180495_p(this.targetPos).func_177230_c() != this.targetBlock) {
            EFWalker.tell(player, "\u00a7c\u76ee\u6807\u65b9\u5757\u4e0d\u89c1\u4e86\uff0c\u5df2\u505c\u6b62");
            EFWalker.stopWalk();
            return;
        }
        double tx = (double)this.targetPos.func_177958_n() + 0.5;
        double ty = (double)this.targetPos.func_177956_o() + 1.0;
        double tz = (double)this.targetPos.func_177952_p() + 0.5;
        double d = this.walkTowards(player, tx, ty, tz, REACH_TARGET, 1.2, 2.0);
        boolean bl = this.lastDist >= 0.0 && this.lastDist - d >= 0.002;
        boolean onGround = player.field_70123_F;
        if (onGround) {
            ++this.climbTicks;
            if (this.climbTicks > TARGET_CLIMB_LIMIT) {
                EFWalker.tell(player, "\u00a7c\u5361\u4f4f\u4e86\uff0c\u5df2\u505c\u6b62");
                EFWalker.stopWalk();
                return;
            }
        } else {
            this.climbTicks = 0;
        }
        if (bl) {
            this.stuckTicks = 0;
        } else if (!onGround) {
            ++this.stuckTicks;
        }
        this.lastDist = d;
        if (this.stuckTicks > TARGET_STUCK_LIMIT) {
            double dy = ty - player.field_70163_u;
            if (dy > 1.3) {
                EFWalker.tell(player, "\u00a7c\u8d70\u4e0d\u8fc7\u53bb\uff1a\u76ee\u6807\u65b9\u5757\u9876\u90e8\u6bd4\u5f53\u524d\u4f4d\u7f6e\u9ad8 " + String.format("%.1f", dy) + " \u683c");
            } else {
                EFWalker.tell(player, "\u00a7c\u5361\u4f4f\u4e86\uff0c\u5df2\u505c\u6b62");
            }
            EFWalker.stopWalk();
            return;
        }
        if (d <= REACH_TARGET) {
            EFWalker.stopMoving(player);
            EFWalker.tell(player, "\u00a7a\u5df2\u7adf\u4e0a\u76ee\u6807\u65b9\u5757\uff1a" + this.targetName);
            EFWalker.stopWalk();
        }
    }

    private void tickCoord(EntityPlayerSP player) {
        double d = this.walkTowards(player, this.coordX, this.coordY, this.coordZ, this.coordTol, 1.2, 2.0);
        boolean bl = this.lastDist >= 0.0 && this.lastDist - d >= 0.002;
        boolean onGround = player.field_70123_F;
        if (onGround) {
            ++this.climbTicks;
            if (this.climbTicks > TARGET_CLIMB_LIMIT) {
                EFWalker.tell(player, "\u00a7c\u5361\u4f4f\u4e86\uff0c\u5df2\u505c\u6b62");
                EFWalker.stopWalk();
                return;
            }
        } else {
            this.climbTicks = 0;
        }
        if (bl) {
            this.stuckTicks = 0;
        } else if (!onGround) {
            ++this.stuckTicks;
        }
        this.lastDist = d;
        if (this.stuckTicks > TARGET_STUCK_LIMIT) {
            EFWalker.tell(player, "\u00a7c\u8d70\u4e0d\u8fc7\u53bb\uff0c\u5df2\u505c\u6b62");
            EFWalker.stopWalk();
            return;
        }
        if (d <= this.coordTol) {
            EFWalker.stopMoving(player);
            EFWalker.tell(player, "\u00a7a\u5df2\u5230\u8fbe\u5750\u6807");
            EFWalker.stopWalk();
        }
    }

    private static BlockPos scanTargets(EntityPlayerSP player, Block block, int rx, int ry, int rz) {        World world = player.field_70170_p;
        int px = (int)Math.floor(player.field_70165_t);
        int py = (int)Math.floor(player.field_70163_u);
        int pz = (int)Math.floor(player.field_70161_v);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int y0 = Math.max(0, py - ry);
        int y1 = Math.min(255, py + ry);
        for (int x = px - rx; x <= px + rx; ++x) {
            for (int z = pz - rz; z <= pz + rz; ++z) {
                for (int y = y0; y <= y1; ++y) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.func_175667_e(pos)) continue;
                    if (world.func_180495_p(pos).func_177230_c() != block) continue;
                    double dx = (double)(x - px);
                    double dz = (double)(z - pz);
                    double d2 = dx * dx + dz * dz;
                    if (!(d2 < bestDist)) continue;
                    bestDist = d2;
                    best = pos;
                }
            }
        }
        return best;
    }

    private static double targetDistXZ(EntityPlayerSP player, BlockPos pos) {
        double dx = (double)pos.func_177958_n() + 0.5 - player.field_70165_t;
        double dz = (double)pos.func_177952_p() + 0.5 - player.field_70161_v;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double walkTowards(EntityPlayerSP player, double tx, double ty, double tz, double reach, double maxUp, double maxHoriz) {
        double dx = tx - player.field_70165_t;
        double dz = tz - player.field_70161_v;
        double dy = ty - player.field_70163_u;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz <= reach) {
            EFWalker.stopMoving(player);
            return horiz;
        }
        double inv = 1.0 / horiz;
        double speed = player.field_71075_bZ.func_75094_b();
        player.field_70159_w = dx * inv * speed;
        player.field_70179_y = dz * inv * speed;
        boolean inLiquid = player.func_70090_H() || player.func_180799_ab();
        if (inLiquid) {
            if ((dy > 0.5 || player.field_70123_F) && this.cycleTicks - this.lastJumpTick >= 5) {
                player.field_70181_x = 0.42;
                this.lastJumpTick = this.cycleTicks;
            }
            return horiz;
        }
        boolean onGround = player.field_70123_F;
        boolean needUp = dy > maxUp && horiz < maxHoriz;
        if (player.field_70122_E && (onGround || needUp) && this.cycleTicks - this.lastJumpTick >= 6) {
            player.field_70181_x = 0.42;
            this.lastJumpTick = this.cycleTicks;
        }
        return horiz;
    }

    private static void stopMoving(EntityPlayerSP player) {
        player.field_70159_w = 0.0;
        player.field_70179_y = 0.0;
        player.field_70181_x = 0.0;
    }

    private static void tell(EntityPlayerSP player, String msg) {
        player.func_146105_b((ITextComponent)new TextComponentString(msg), true);
    }

    private void hardReset() {
        this.running = false;
        this.state = 0;
        this.targets = null;
        this.targetPos = null;
        this.homeZ = 0.0;
        this.homeY = 0.0;
        this.homeX = 0.0;
        this.lastDist = -1.0;
        this.stuckTicks = 0;
        this.climbTicks = 0;
        this.cycleTicks = 0;
    }
}

package com.rain.automc;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/*
 * 动作执行原语：把 Minecraft 客户端那些零散的操作包成一行调用。
 * 全部走游戏内 API，不依赖窗口焦点。
 */
@SideOnly(value=Side.CLIENT)
public class PlayerActions {
    public static void stopMove() {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) {
            return;
        }
        MovementInput mi = p.movementInput;
        if (mi != null) {
            mi.moveForward = 0.0f;
            mi.moveStrafe = 0.0f;
            mi.jump = false;
        }
        p.motionX = 0.0;
        p.motionZ = 0.0;
        p.motionY = 0.0;
    }

    public static void sneak(boolean on) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p != null) {
            p.setSneaking(on);
        }
    }

    /** @return 这一下是否真的起跳（不在地上时不跳）。 */
    public static boolean jump() {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null || !p.onGround) {
            return false;
        }
        p.motionY = 0.42;
        return true;
    }

    public static void look(float yaw, float pitch) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) {
            return;
        }
        p.rotationYaw = yaw;
        p.prevRotationYaw = yaw;
        p.rotationPitch = Math.max(-90.0f, Math.min(90.0f, pitch));
        p.prevRotationPitch = p.rotationPitch;
    }

    public static boolean lookAtNearestPlayer() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null || mc.world == null) {
            return false;
        }
        Entity t = nearest(p, 64.0f, "玩家");
        if (!(t instanceof EntityPlayer)) {
            return false;
        }
        faceTo(p, t);
        return true;
    }

    public static void faceTo(EntityPlayerSP p, Entity e) {
        double dx = e.posX - p.posX;
        double dz = e.posZ - p.posZ;
        double dy = e.posY + (double)e.getEyeHeight() * 0.8 - (p.posY + (double)p.getEyeHeight());
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float)(-(Math.atan2(dy, horiz) * 180.0 / Math.PI));
        look(yaw, pitch);
    }

    public static boolean attackNearest(int range, String type) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null || mc.world == null) {
            return false;
        }
        Entity t = nearest(p, (float)range, type);
        if (t == null) {
            return false;
        }
        faceTo(p, t);
        mc.playerController.attackEntity(p, t);
        p.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        return true;
    }

    public static Entity nearest(EntityPlayerSP p, float range, String type) {
        Minecraft mc = Minecraft.getMinecraft();
        AxisAlignedBB box = new AxisAlignedBB(
                p.posX - (double)range, p.posY - (double)range, p.posZ - (double)range,
                p.posX + (double)range, p.posY + (double)range, p.posZ + (double)range);
        List<?> list = mc.world.getEntitiesWithinAABB(Entity.class, box);
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); ++i) {
            Object o = list.get(i);
            if (!(o instanceof Entity)) {
                continue;
            }
            Entity e = (Entity)o;
            if (e == p || !e.isEntityAlive()) {
                continue;
            }
            boolean wantPlayer = "玩家".equals(type);
            boolean wantMob = "怪物".equals(type);
            if (wantPlayer && !(e instanceof EntityPlayer)) {
                continue;
            }
            if (wantMob && (!(e instanceof EntityLivingBase) || e instanceof EntityPlayer)) {
                continue;
            }
            double d = p.getDistanceSq(e);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    public static void drop(boolean all) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p != null) {
            p.dropItem(all);
        }
    }

    public static void chat(String text) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p != null && text != null && !text.isEmpty()) {
            p.sendChatMessage(text);
        }
    }

    public static void chatSelf(String text) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p != null) {
            p.sendStatusMessage((net.minecraft.util.text.ITextComponent)new TextComponentString(text), false);
        }
    }

    public static void resetAll() {
        stopMove();
        sneak(false);
        KeyRouter.releaseAll();
    }
}

package com.rain.automc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
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
 * 需要跨 tick 维持的（按住前进、连按、按键松开）由 tick() 统一收尾。
 */
@SideOnly(value=Side.CLIENT)
public class PlayerActions {
    private static long forwardUntil = 0L;
    private static boolean forwardOn = false;
    private static final List<long[]> keyReleases = new ArrayList<long[]>();

    public static void setForward(float v) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null || p.movementInput == null) {
            return;
        }
        p.movementInput.moveForward = v;
    }

    public static void holdForward(int ms) {
        forwardOn = true;
        forwardUntil = System.currentTimeMillis() + Math.max(50L, (long)ms);
        setForward(1.0f);
    }

    public static void stopMove() {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        forwardOn = false;
        forwardUntil = 0L;
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

    public static void sprint(boolean on) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p != null) {
            p.setSprinting(on);
        }
    }

    public static void jump() {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p != null && p.onGround) {
            p.motionY = 0.42;
        }
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
        Entity t = nearest(p, 64.0f, "\u73a9\u5bb6");
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
            boolean wantPlayer = "\u73a9\u5bb6".equals(type);
            boolean wantMob = "\u602a\u7269".equals(type);
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

    public static void openInventory() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.displayGuiScreen((GuiScreen)new GuiInventory((EntityPlayer)mc.player));
        }
    }

    public static void closeScreen() {
        Minecraft.getMinecraft().displayGuiScreen((GuiScreen)null);
    }

    public static void clipboard(String text) {
        GuiScreen.setClipboardString(text);
    }

    public static void chatSelf(String text) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p != null) {
            p.sendStatusMessage((net.minecraft.util.text.ITextComponent)new TextComponentString(text), false);
        }
    }

    /* 按键：按 key.* 描述名匹配原版按键，按住 ms 后自动松开 */
    public static boolean pressKey(String name, int ms) {
        Minecraft mc = Minecraft.getMinecraft();
        KeyBinding[] all = mc.gameSettings.keyBindings;
        String want = name == null ? "" : name.trim().toLowerCase();
        if (want.isEmpty()) {
            return false;
        }
        for (int i = 0; i < all.length; ++i) {
            KeyBinding kb = all[i];
            if (kb == null) {
                continue;
            }
            String desc = kb.getKeyDescription();
            if (desc == null) {
                continue;
            }
            if (desc.toLowerCase().equals(want) || desc.toLowerCase().replace("key.", "").equals(want)) {
                int code = kb.getKeyCode();
                KeyBinding.setKeyBindState(code, true);
                keyReleases.add(new long[]{code, System.currentTimeMillis() + Math.max(50, ms)});
                return true;
            }
        }
        return false;
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (forwardOn) {
            if (now >= forwardUntil) {
                forwardOn = false;
                setForward(0.0f);
            } else {
                setForward(1.0f);
            }
        }
        for (int i = keyReleases.size() - 1; i >= 0; --i) {
            long[] r = keyReleases.get(i);
            if (now >= r[1]) {
                KeyBinding.setKeyBindState((int)r[0], false);
                keyReleases.remove(i);
            }
        }
    }

    public static void resetAll() {
        stopMove();
        sneak(false);
        sprint(false);
        for (int i = keyReleases.size() - 1; i >= 0; --i) {
            long[] r = keyReleases.get(i);
            KeyBinding.setKeyBindState((int)r[0], false);
            keyReleases.remove(i);
        }
    }
}

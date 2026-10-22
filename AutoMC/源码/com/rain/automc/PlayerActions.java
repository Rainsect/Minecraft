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
        EntityPlayerSP p = Minecraft.func_71410_x().field_71439_g;
        if (p == null || p.field_71158_b == null) {
            return;
        }
        p.field_71158_b.field_192832_b = v;
    }

    public static void holdForward(int ms) {
        forwardOn = true;
        forwardUntil = System.currentTimeMillis() + Math.max(50L, (long)ms);
        setForward(1.0f);
    }

    public static void stopMove() {
        EntityPlayerSP p = Minecraft.func_71410_x().field_71439_g;
        forwardOn = false;
        forwardUntil = 0L;
        if (p == null) {
            return;
        }
        MovementInput mi = p.field_71158_b;
        if (mi != null) {
            mi.field_192832_b = 0.0f;
            mi.field_78902_a = 0.0f;
            mi.field_78901_c = false;
        }
        p.field_70159_w = 0.0;
        p.field_70179_y = 0.0;
        p.field_70181_x = 0.0;
    }

    public static void sneak(boolean on) {
        EntityPlayerSP p = Minecraft.func_71410_x().field_71439_g;
        if (p != null) {
            p.func_70095_a(on);
        }
    }

    public static void sprint(boolean on) {
        EntityPlayerSP p = Minecraft.func_71410_x().field_71439_g;
        if (p != null) {
            p.func_70031_b(on);
        }
    }

    public static void jump() {
        EntityPlayerSP p = Minecraft.func_71410_x().field_71439_g;
        if (p != null && p.field_70122_E) {
            p.field_70181_x = 0.42;
        }
    }

    public static void look(float yaw, float pitch) {
        EntityPlayerSP p = Minecraft.func_71410_x().field_71439_g;
        if (p == null) {
            return;
        }
        p.field_70177_z = yaw;
        p.field_70126_B = yaw;
        p.field_70125_A = Math.max(-90.0f, Math.min(90.0f, pitch));
        p.field_70127_C = p.field_70125_A;
    }

    public static boolean lookAtNearestPlayer() {
        Minecraft mc = Minecraft.func_71410_x();
        EntityPlayerSP p = mc.field_71439_g;
        if (p == null || mc.field_71441_e == null) {
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
        double dx = e.field_70165_t - p.field_70165_t;
        double dz = e.field_70161_v - p.field_70161_v;
        double dy = e.field_70163_u + (double)e.func_70047_e() * 0.8 - (p.field_70163_u + (double)p.func_70047_e());
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float)(-(Math.atan2(dy, horiz) * 180.0 / Math.PI));
        look(yaw, pitch);
    }

    public static boolean attackNearest(int range, String type) {
        Minecraft mc = Minecraft.func_71410_x();
        EntityPlayerSP p = mc.field_71439_g;
        if (p == null || mc.field_71441_e == null) {
            return false;
        }
        Entity t = nearest(p, (float)range, type);
        if (t == null) {
            return false;
        }
        faceTo(p, t);
        mc.field_71442_b.func_78764_a(p, t);
        p.func_184609_a(net.minecraft.util.EnumHand.MAIN_HAND);
        return true;
    }

    public static Entity nearest(EntityPlayerSP p, float range, String type) {
        Minecraft mc = Minecraft.func_71410_x();
        AxisAlignedBB box = new AxisAlignedBB(
                p.field_70165_t - (double)range, p.field_70163_u - (double)range, p.field_70161_v - (double)range,
                p.field_70165_t + (double)range, p.field_70163_u + (double)range, p.field_70161_v + (double)range);
        List<?> list = mc.field_71441_e.func_72872_a(Entity.class, box);
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); ++i) {
            Object o = list.get(i);
            if (!(o instanceof Entity)) {
                continue;
            }
            Entity e = (Entity)o;
            if (e == p || !e.func_70089_S()) {
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
            double d = p.func_70068_e(e);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    public static void drop(boolean all) {
        EntityPlayerSP p = Minecraft.func_71410_x().field_71439_g;
        if (p != null) {
            p.func_71040_bB(all);
        }
    }

    public static void openInventory() {
        Minecraft mc = Minecraft.func_71410_x();
        if (mc.field_71439_g != null) {
            mc.func_147108_a((GuiScreen)new GuiInventory((EntityPlayer)mc.field_71439_g));
        }
    }

    public static void closeScreen() {
        Minecraft.func_71410_x().func_147108_a((GuiScreen)null);
    }

    public static void clipboard(String text) {
        GuiScreen.func_146275_d(text);
    }

    public static void chatSelf(String text) {
        EntityPlayerSP p = Minecraft.func_71410_x().field_71439_g;
        if (p != null) {
            p.func_146105_b((net.minecraft.util.text.ITextComponent)new TextComponentString(text), false);
        }
    }

    /* 按键：按 key.* 描述名匹配原版按键，按住 ms 后自动松开 */
    public static boolean pressKey(String name, int ms) {
        Minecraft mc = Minecraft.func_71410_x();
        KeyBinding[] all = mc.field_71474_y.field_74324_K;
        String want = name == null ? "" : name.trim().toLowerCase();
        if (want.isEmpty()) {
            return false;
        }
        for (int i = 0; i < all.length; ++i) {
            KeyBinding kb = all[i];
            if (kb == null) {
                continue;
            }
            String desc = kb.func_151464_g();
            if (desc == null) {
                continue;
            }
            if (desc.toLowerCase().equals(want) || desc.toLowerCase().replace("key.", "").equals(want)) {
                int code = kb.func_151463_i();
                KeyBinding.func_74510_a(code, true);
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
                KeyBinding.func_74510_a((int)r[0], false);
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
            KeyBinding.func_74510_a((int)r[0], false);
            keyReleases.remove(i);
        }
    }
}

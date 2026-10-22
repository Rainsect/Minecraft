package com.rain.automc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/*
 * 单发交互动作：右键 / 左键 / 切换快捷栏。
 * 逻辑复刻原版 clickMouse / rightClickMouse 的判定分支。
 */
@SideOnly(value=Side.CLIENT)
public class ClickActions {
    public static void leftClick() {
        Minecraft mc = Minecraft.func_71410_x();
        EntityPlayerSP player = mc.field_71439_g;
        if (player == null) {
            return;
        }
        RayTraceResult over = mc.field_71476_x;
        String hit = over == null ? "MISS" : over.field_72313_a.name();
        if (hit.equals("ENTITY")) {
            mc.field_71442_b.func_78764_a(player, over.field_72308_g);
        } else if (hit.equals("BLOCK")) {
            BlockPos pos = over.func_178782_a();
            mc.field_71442_b.func_180511_b(pos, over.field_178784_b);
        }
        player.func_184609_a(EnumHand.MAIN_HAND);
    }

    public static void rightClick() {
        Minecraft mc = Minecraft.func_71410_x();
        EntityPlayerSP player = mc.field_71439_g;
        if (player == null) {
            return;
        }
        EnumHand hand = EnumHand.MAIN_HAND;
        RayTraceResult over = mc.field_71476_x;
        String hit = over == null ? "MISS" : over.field_72313_a.name();
        if (hit.equals("ENTITY")) {
            mc.field_71442_b.func_187102_a(player, over.field_72308_g, over, hand);
            mc.field_71442_b.func_187097_a(player, over.field_72308_g, hand);
        } else if (hit.equals("BLOCK")) {
            BlockPos pos = over.func_178782_a();
            Vec3d hvec = over.field_72307_f;
            mc.field_71442_b.func_187099_a(player, mc.field_71441_e, pos, over.field_178784_b, hvec, hand);
        }
        ItemStack stack = player.func_184586_b(hand);
        if (!stack.func_190926_b()) {
            mc.field_71442_b.func_187101_a(player, mc.field_71441_e, hand);
        }
        player.func_184609_a(hand);
    }

    public static boolean selectHotbar(int slot) {
        if (slot < 1 || slot > 9) {
            return false;
        }
        Minecraft mc = Minecraft.func_71410_x();
        EntityPlayerSP player = mc.field_71439_g;
        if (player == null) {
            return false;
        }
        player.field_71071_by.field_70461_c = slot - 1;
        player.field_71174_a.func_147297_a(new CPacketHeldItemChange(slot - 1));
        return true;
    }
}

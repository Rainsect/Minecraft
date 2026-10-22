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
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) {
            return;
        }
        RayTraceResult over = mc.objectMouseOver;
        String hit = over == null ? "MISS" : over.typeOfHit.name();
        if (hit.equals("ENTITY")) {
            mc.playerController.attackEntity(player, over.entityHit);
        } else if (hit.equals("BLOCK")) {
            BlockPos pos = over.getBlockPos();
            mc.playerController.clickBlock(pos, over.sideHit);
        }
        player.swingArm(EnumHand.MAIN_HAND);
    }

    public static void rightClick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) {
            return;
        }
        EnumHand hand = EnumHand.MAIN_HAND;
        RayTraceResult over = mc.objectMouseOver;
        String hit = over == null ? "MISS" : over.typeOfHit.name();
        if (hit.equals("ENTITY")) {
            mc.playerController.interactWithEntity(player, over.entityHit, over, hand);
            mc.playerController.interactWithEntity(player, over.entityHit, hand);
        } else if (hit.equals("BLOCK")) {
            BlockPos pos = over.getBlockPos();
            Vec3d hvec = over.hitVec;
            mc.playerController.processRightClickBlock(player, mc.world, pos, over.sideHit, hvec, hand);
        }
        ItemStack stack = player.getHeldItem(hand);
        if (!stack.isEmpty()) {
            mc.playerController.processRightClick(player, mc.world, hand);
        }
        player.swingArm(hand);
    }

    public static boolean selectHotbar(int slot) {
        if (slot < 1 || slot > 9) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) {
            return false;
        }
        player.inventory.currentItem = slot - 1;
        player.connection.sendPacket(new CPacketHeldItemChange(slot - 1));
        return true;
    }
}

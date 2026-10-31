package com.rain.automc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 把「按住」的键真正喂给移动系统。
 *
 * <p>原版每帧由 {@code MovementInputFromOptions.updatePlayerMoveState()} 读
 * {@code KeyBinding.isKeyDown()} 算出前后左右/跳跃/潜行，而 {@code KeyBinding.pressed}
 * 又每帧被 {@code Keyboard} 的真实状态覆盖，所以光写按键缓冲或 {@code setKeyBindState}
 * 都会被冲掉。这里挂在 {@link InputUpdateEvent}（原版算完之后触发），直接把按住状态
 * 叠进 {@code MovementInput}，与窗口是否在前台无关。
 */
@SideOnly(value = Side.CLIENT)
public class AutoInput {
    private boolean sneakForced;
    private boolean sprintForced;

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        GameSettings gs = mc.gameSettings;
        MovementInput mi = event.getMovementInput();
        if (player == null || gs == null || mi == null || event.getEntityPlayer() != player) {
            return;
        }
        // 打开界面时原版本就不让走，这里保持一致
        if (mc.currentScreen != null) {
            return;
        }

        if (KeyRouter.held(gs.keyBindForward.getKeyCode())) {
            mi.moveForward = 1.0f;
        }
        if (KeyRouter.held(gs.keyBindBack.getKeyCode())) {
            mi.moveForward = -1.0f;
        }
        if (KeyRouter.held(gs.keyBindLeft.getKeyCode())) {
            mi.moveStrafe = 1.0f;
        }
        if (KeyRouter.held(gs.keyBindRight.getKeyCode())) {
            mi.moveStrafe = -1.0f;
        }
        if (KeyRouter.held(gs.keyBindJump.getKeyCode())) {
            mi.jump = true;
        }

        boolean sneak = KeyRouter.held(gs.keyBindSneak.getKeyCode());
        if (sneak) {
            mi.sneak = true;
            player.setSneaking(true);
            this.sneakForced = true;
        } else if (this.sneakForced) {
            player.setSneaking(false);
            this.sneakForced = false;
        }

        boolean sprint = KeyRouter.held(gs.keyBindSprint.getKeyCode());
        if (sprint) {
            player.setSprinting(true);
            this.sprintForced = true;
        } else if (this.sprintForced) {
            player.setSprinting(false);
            this.sprintForced = false;
        }
    }
}

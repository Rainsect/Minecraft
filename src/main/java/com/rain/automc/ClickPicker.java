package com.rain.automc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * 在编辑器里给「点击坐标」拾取坐标。
 *
 * <p>做法是并进 AutoMC 自己的编辑器：点动作面板上的「⌖ 拾取坐标」，编辑器收起，玩家在目标界面（背包 /
 * 箱子 / 村民交易）点一下，坐标就填回这条动作，编辑器自动回来。不额外提供独立界面。
 *
 * <p>存进去的是窗口像素（左上角 0,0），和「点击坐标」执行时用的是同一套坐标。
 */
@SideOnly(value = Side.CLIENT)
public final class ClickPicker {
    private static boolean active;
    private static int chainIndex = -1;
    private static int actionIndex = -1;

    public ClickPicker() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void begin(int chain, int action) {
        chainIndex = chain;
        actionIndex = action;
        active = true;
        overlay("\u00a7e\u5728\u754c\u9762\u4e0a\u70b9\u4e00\u4e0b\u76ee\u6807\u4f4d\u7f6e\uff0cESC \u53d6\u6d88");
    }

    public static void cancel() {
        active = false;
        chainIndex = -1;
        actionIndex = -1;
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!active) {
            return;
        }
        if (Mouse.getEventButton() < 0 || !Mouse.getEventButtonState()) {
            return;
        }
        GuiScreen gui = event.getGui();
        if (gui == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.displayWidth <= 0 || mc.displayHeight <= 0 || gui.width <= 0 || gui.height <= 0) {
            return;
        }
        // 界面坐标 -> 窗口像素，和不点击时「点击坐标」执行的坐标完全一致
        int guiX = Mouse.getEventX() * gui.width / mc.displayWidth;
        int guiY = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
        int pixelX = guiX * mc.displayWidth / gui.width;
        int pixelY = guiY * mc.displayHeight / gui.height;
        apply(pixelX, pixelY);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!active) {
            return;
        }
        if (Keyboard.getEventKeyState() && Keyboard.getEventKey() == Keyboard.KEY_ESCAPE) {
            cancel();
            overlay("\u00a7c\u5df2\u53d6\u6d88\u62fe\u53d6\u5750\u6807");
        }
    }

    private static void apply(int pixelX, int pixelY) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (chainIndex >= 0 && chainIndex < ConfigManager.get().chains.size()) {
                ScriptChain chain = ConfigManager.get().chains.get(chainIndex);
                if (actionIndex >= 0 && actionIndex < chain.actions.size()) {
                    ScriptAction action = chain.actions.get(actionIndex);
                    action.setParam(0, String.valueOf(pixelX));
                    action.setParam(1, String.valueOf(pixelY));
                    ConfigManager.save();
                }
            }
        } catch (Throwable ignored) {
            // 配置被外部改动之类，忽略即可
        }
        int chain = chainIndex;
        cancel();
        mc.displayGuiScreen(new GuiProject(chain));
        overlay("\u00a7a\u5df2\u8bb0\u5f55 " + pixelX + ", " + pixelY);
    }

    private static void overlay(String text) {
        try {
            Minecraft.getMinecraft().ingameGUI.setOverlayMessage(text, false);
        } catch (Throwable ignored) {
            // 界面还没建好时没有 HUD
        }
    }
}

package com.rain.automc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/*
 * 客户端主循环：F8 边沿打开项目列表（防按住连开），驱动动作引擎、事件侦测、
 * 按住类动作的收尾，以及聊天事件转发。
 */
@SideOnly(value=Side.CLIENT)
public class ClientTickHandler {
    private boolean keyLatch = false;
    private boolean[] hotDown = new boolean[0];
    private final EventScanner scanner = new EventScanner();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (AutoMCMod.keyOpenGui != null && AutoMCMod.keyOpenGui.isPressed()) {
            if (!this.keyLatch && mc.currentScreen == null) {
                this.keyLatch = true;
                if (mc.player != null) {
                    mc.displayGuiScreen((GuiScreen)new GuiAutoMC());
                }
            }
        } else {
            this.keyLatch = false;
        }
        this.tickHotkeys(mc);
        PlayerActions.tick();
        NpcTrader.tick();
        ChainEngine.tick();
        this.scanner.tick();
    }

    /* 项目绑定按键：只在没有界面打开时生效，按一次触发一次 */
    private void tickHotkeys(Minecraft mc) {
        java.util.List<ScriptChain> list = ConfigManager.get().chains;
        if (this.hotDown.length != list.size()) {
            this.hotDown = new boolean[list.size()];
        }
        if (mc.player == null || mc.currentScreen != null) {
            return;
        }
        for (int i = 0; i < list.size(); ++i) {
            String key = list.get(i).hotkey;
            boolean down = false;
            if (key != null && !key.isEmpty()) {
                int code = Keyboard.getKeyIndex(key);
                down = code != 0 && Keyboard.isKeyDown(code);
            }
            if (down && !this.hotDown[i]) {
                ChainEngine.run(i);
            }
            this.hotDown[i] = down;
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        this.scanner.onChat(event);
    }
}

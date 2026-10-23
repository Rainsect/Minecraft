package com.rain.automc;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/*
 * 备用入口：/amc run <序号|项目名> | stop | status | list | vars | npc | slot
 */
@SideOnly(value = Side.CLIENT)
public class AMCClientCommand extends CommandBase {
    public String getName() {
        return "amc";
    }

    public String getUsage(ICommandSender sender) {
        return "/amc run <\u5e8f\u53f7|\u9879\u76ee\u540d> | stop | status | list | vars | npc | slot";
    }

    public int getRequiredPermissionLevel() {
        return 0;
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0) {
            msg(sender, this.getUsage(sender));
            return;
        }
        String cmd = args[0].toLowerCase();
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (cmd.equals("gui")) {
            if (player == null) {
                msg(sender, "\u00a7c\u5148\u8fdb\u5165\u4e16\u754c");
                return;
            }
            mc.displayGuiScreen((net.minecraft.client.gui.GuiScreen)new GuiAutoMC());
            return;
        }
        if (cmd.equals("stop")) {
            ChainEngine.stop();
            msg(sender, "\u00a7a\u5df2\u505c\u6b62");
            return;
        }
        if (cmd.equals("status")) {
            String s = ChainEngine.status();
            if (EFWalker.isBusy()) {
                s = s + " | " + EFWalker.status();
            }
            if (!ChainEngine.lastMessage().isEmpty()) {
                s = s + " | " + ChainEngine.lastMessage();
            }
            msg(sender, s);
            return;
        }
        if (cmd.equals("list")) {
            List<ScriptChain> list = ConfigManager.get().chains;
            for (int i = 0; i < list.size(); ++i) {
                ScriptChain c = list.get(i);
                msg(sender, (i + 1) + ". " + c.name + " | " + c.eventSummary() + " | " + c.actionCount() + " \u52a8\u4f5c");
            }
            return;
        }
        if (cmd.equals("vars")) {
            List<String> vs = ChainEngine.describeVars();
            msg(sender, vs.isEmpty() ? "\u6ca1\u6709\u53d8\u91cf" : vs.toString());
            return;
        }
        if (cmd.equals("run")) {
            if (args.length < 2) {
                msg(sender, "\u7528\u6cd5: /amc run <\u5e8f\u53f7|\u9879\u76ee\u540d>");
                return;
            }
            String key = args[1];
            boolean ok;
            try {
                ok = ChainEngine.run(Integer.parseInt(key) - 1);
            } catch (NumberFormatException e) {
                ok = ChainEngine.runByName(key);
            }
            msg(sender, ok ? "\u00a7a\u5f00\u59cb\u6267\u884c" : ChainEngine.lastMessage());
            return;
        }
        if (cmd.equals("npc")) {
            if (player == null || mc.world == null) {
                msg(sender, "\u00a7c\u5f53\u524d\u6ca1\u6709\u4e16\u754c");
                return;
            }
            List<String> npcs = NpcTrader.nearbyNpcs(player);
            if (npcs.isEmpty()) {
                msg(sender, "\u00a7e8 \u683c\u8303\u56f4\u5185\u6ca1\u6709 NPC");
                return;
            }
            for (String s : npcs) {
                msg(sender, s);
            }
            return;
        }
        if (cmd.equals("slot")) {
            if (!(mc.currentScreen instanceof GuiContainer)) {
                msg(sender, "\u00a7c\u5f53\u524d\u6ca1\u6709\u6253\u5f00\u5bb9\u5668\u754c\u9762");
                return;
            }
            GuiContainer gui = (GuiContainer)mc.currentScreen;
            List<Slot> slots = player.openContainer.inventorySlots;
            int mx = org.lwjgl.input.Mouse.getX() * this.guiWidth(mc) / mc.displayWidth;
            int my = this.guiHeight(mc) - org.lwjgl.input.Mouse.getY() * this.guiHeight(mc) / mc.displayHeight - 1;
            for (int i = 0; i < slots.size(); ++i) {
                Slot s = slots.get(i);
                if (mx >= s.xPos && mx < s.xPos + 16 && my >= s.yPos && my < s.yPos + 16) {
                    ItemStack stack = s.getStack();
                    String item = stack == null || stack.isEmpty() ? "\u7a7a" : stack.getDisplayName() + " x" + stack.getCount();
                    msg(sender, "\u69fd\u4f4d " + i + ": " + item + "\uff08GUI \u5750\u6807 " + s.xPos + "," + s.yPos + "\uff09");
                    return;
                }
            }
            msg(sender, "\u00a7e\u9f20\u6807\u4e0b\u6ca1\u6709\u69fd\u4f4d");
            return;
        }
        msg(sender, this.getUsage(sender));
    }

    private int guiWidth(Minecraft mc) {
        return new net.minecraft.client.gui.ScaledResolution(mc).getScaledWidth();
    }

    private int guiHeight(Minecraft mc) {
        return new net.minecraft.client.gui.ScaledResolution(mc).getScaledHeight();
    }

    private static void msg(ICommandSender sender, String s) {
        sender.sendMessage((ITextComponent)new TextComponentString(s));
    }
}

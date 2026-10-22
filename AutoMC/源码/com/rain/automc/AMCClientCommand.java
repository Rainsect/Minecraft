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
    public String func_71517_b() {
        return "amc";
    }

    public String func_71518_a(ICommandSender sender) {
        return "/amc run <\u5e8f\u53f7|\u9879\u76ee\u540d> | stop | status | list | vars | npc | slot";
    }

    public int func_82362_a() {
        return 0;
    }

    public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0) {
            msg(sender, this.func_71518_a(sender));
            return;
        }
        String cmd = args[0].toLowerCase();
        Minecraft mc = Minecraft.func_71410_x();
        EntityPlayerSP player = mc.field_71439_g;
        if (cmd.equals("gui")) {
            if (player == null) {
                msg(sender, "\u00a7c\u5148\u8fdb\u5165\u4e16\u754c");
                return;
            }
            mc.func_147108_a((net.minecraft.client.gui.GuiScreen)new GuiAutoMC());
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
            if (NpcTrader.isBusy()) {
                s = s + " | " + NpcTrader.status();
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
            if (player == null || mc.field_71441_e == null) {
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
        if (cmd.equals("trade")) {
            if (args.length < 2) {
                msg(sender, "用法: /amc trade <物品|hand> [all|数量] [第几件]");
                return;
            }
            String mode = args.length >= 3 ? args[2] : "all";
            int idx = 1;
            if (args.length >= 4) {
                try {
                    idx = Integer.parseInt(args[3]);
                } catch (Exception e) {
                    idx = 1;
                }
            }
            String r = NpcTrader.start(args[1], mode, idx);
            msg(sender, r == null ? "§a开始交易" : r);
            return;
        }
        if (cmd.equals("slot")) {
            if (!(mc.field_71462_r instanceof GuiContainer)) {
                msg(sender, "\u00a7c\u5f53\u524d\u6ca1\u6709\u6253\u5f00\u5bb9\u5668\u754c\u9762");
                return;
            }
            GuiContainer gui = (GuiContainer)mc.field_71462_r;
            List<Slot> slots = player.field_71070_bA.field_75151_b;
            int mx = org.lwjgl.input.Mouse.getX() * this.guiWidth(mc) / mc.field_71443_c;
            int my = this.guiHeight(mc) - org.lwjgl.input.Mouse.getY() * this.guiHeight(mc) / mc.field_71440_d - 1;
            for (int i = 0; i < slots.size(); ++i) {
                Slot s = slots.get(i);
                if (mx >= s.field_75223_e && mx < s.field_75223_e + 16 && my >= s.field_75221_f && my < s.field_75221_f + 16) {
                    ItemStack stack = s.func_75211_c();
                    String item = stack == null || stack.func_190926_b() ? "\u7a7a" : stack.func_82833_r() + " x" + stack.func_190916_E();
                    msg(sender, "\u69fd\u4f4d " + i + ": " + item + "\uff08GUI \u5750\u6807 " + s.field_75223_e + "," + s.field_75221_f + "\uff09");
                    return;
                }
            }
            msg(sender, "\u00a7e\u9f20\u6807\u4e0b\u6ca1\u6709\u69fd\u4f4d");
            return;
        }
        msg(sender, this.func_71518_a(sender));
    }

    private int guiWidth(Minecraft mc) {
        return new net.minecraft.client.gui.ScaledResolution(mc).func_78326_a();
    }

    private int guiHeight(Minecraft mc) {
        return new net.minecraft.client.gui.ScaledResolution(mc).func_78328_b();
    }

    private static void msg(ICommandSender sender, String s) {
        sender.func_145747_a((ITextComponent)new TextComponentString(s));
    }
}

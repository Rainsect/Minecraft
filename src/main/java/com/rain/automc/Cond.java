package com.rain.automc;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/*
 * 条件表达式求值：一行字符串就能写判断，供「条件跳转 / 等待条件 / 事件侦测」共用。
 * 支持：
 *   血量<10      最大血量      饥饿<=6       经验>=30
 *   y<0          时间>=13000   物品:diamond>=64   变量:count>=3
 *   距离:100,64,200<=5        手持耐久<10
 * 比较符：> < >= <= = == !=
 */
@SideOnly(value=Side.CLIENT)
public class Cond {
    public static boolean eval(String expr, Map<String, String> vars) {
        if (expr == null) {
            return false;
        }
        String s = expr.trim();
        if (s.isEmpty()) {
            return false;
        }
        if (s.equals("\u771f") || s.equals("true")) {
            return true;
        }
        if (s.equals("\u5047") || s.equals("false")) {
            return false;
        }
        String op = null;
        int at = -1;
        String[] ops = {">=", "<=", "!=", "==", ">", "<", "="};
        for (int i = 0; i < ops.length; ++i) {
            int idx = s.indexOf(ops[i]);
            if (idx > 0) {
                op = ops[i];
                at = idx;
                break;
            }
        }
        if (op == null) {
            return false;
        }
        String left = s.substring(0, at).trim();
        String right = s.substring(at + op.length()).trim();
        double lv = value(left, vars);
        double rv = value(right, vars);
        if (op.equals(">")) {
            return lv > rv;
        }
        if (op.equals("<")) {
            return lv < rv;
        }
        if (op.equals(">=")) {
            return lv >= rv;
        }
        if (op.equals("<=")) {
            return lv <= rv;
        }
        if (op.equals("!=")) {
            return lv != rv;
        }
        return lv == rv;
    }

    private static double value(String token, Map<String, String> vars) {
        String t = token.trim();
        if (t.isEmpty()) {
            return 0.0;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (t.startsWith("\u53d8\u91cf:")) {
            return num(vars.get(t.substring(3).trim()), 0.0);
        }
        if (t.startsWith("\u7269\u54c1:")) {
            return p == null ? 0.0 : (double)NpcTrader.countItem(p, t.substring(3).trim());
        }
        if (t.startsWith("\u8ddd\u79bb:")) {
            if (p == null) {
                return 0.0;
            }
            String[] xyz = t.substring(3).split(",");
            if (xyz.length < 3) {
                return 0.0;
            }
            double dx = num(xyz[0], 0.0) - p.posX;
            double dz = num(xyz[2], 0.0) - p.posZ;
            return Math.sqrt(dx * dx + dz * dz);
        }
        if (p == null) {
            return 0.0;
        }
        if (t.equals("\u8840\u91cf")) {
            return p.getHealth();
        }
        if (t.equals("\u6700\u5927\u8840\u91cf")) {
            return p.getMaxHealth();
        }
        if (t.equals("\u9965\u997f")) {
            return p.getFoodStats().getFoodLevel();
        }
        if (t.equals("\u7ecf\u9a8c")) {
            return p.experienceLevel;
        }
        if (t.equalsIgnoreCase("y")) {
            return p.posY;
        }
        if (t.equalsIgnoreCase("x")) {
            return p.posX;
        }
        if (t.equalsIgnoreCase("z")) {
            return p.posZ;
        }
        if (t.equals("\u65f6\u95f4") && mc.world != null) {
            return mc.world.getWorldTime() % 24000L;
        }
        if (t.equals("\u624b\u6301\u8010\u4e45")) {
            ItemStack st = p.getHeldItemMainhand();
            if (st == null || st.isEmpty() || st.getMaxDamage() <= 0) {
                return 0.0;
            }
            return st.getMaxDamage() - st.getItemDamage();
        }
        if (t.startsWith("\u4f4d\u7f6e\u5757:")) {
            BlockPos pos = mc.objectMouseOver == null || mc.objectMouseOver.getBlockPos() == null
                    ? BlockPos.ORIGIN : mc.objectMouseOver.getBlockPos();
            return (double)pos.getY();
        }
        return num(t, 0.0);
    }

    public static double num(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}

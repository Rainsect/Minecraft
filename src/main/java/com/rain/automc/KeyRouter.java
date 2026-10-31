package com.rain.automc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

/**
 * 「按键」的真正实现：全部走游戏内 API，不碰系统键盘、不依赖窗口在前台。
 *
 * <p>按下 = 把 LWJGL 的按键状态缓冲置位 + {@link KeyBinding#setKeyBindState}。
 * 1.12.2 的移动输入读取 {@code Keyboard.isKeyDown}，所以置位缓冲后前后台都能真的走路、
 * 跳跃、潜行，且不会把按键打到别的程序里。
 *
 * <p>同一时刻可以多路并行按不同键（引用计数），松开时逐个减到 0 才真正释放。
 */
@SideOnly(value = Side.CLIENT)
public final class KeyRouter {
    private static final Map<String, String> ALIAS = new HashMap<String, String>();
    private static final Map<Integer, Integer> HELD = new HashMap<Integer, Integer>();
    private static final List<Pending> PENDING = new ArrayList<Pending>();
    private static java.lang.reflect.Field bufferField;
    private static boolean bufferBroken;

    /** 一批到点要松开的键。 */
    private static final class Pending {
        final int[] codes;
        final long at;

        Pending(int[] codes, long at) {
            this.codes = codes;
            this.at = at;
        }
    }

    private KeyRouter() {
    }

    /**
     * 按一次 / 按住 / 松开。
     *
     * <p>模式：点按 = 按下后自动松开（毫秒表示按住时长）；按住 = 一直按着，毫秒 &gt; 0 时自动松开，
     * 0 表示按到「松开」为止；松开 = 释放同名按键。
     *
     * @return null 表示已执行；否则是给玩家看的错误消息
     */
    public static String press(String name, String mode, int ms) {
        String m = mode == null || mode.trim().isEmpty() ? "点按" : mode.trim();
        if (KeyRouter.isMouse(name)) {
            int b = KeyRouter.mouseButton(name);
            if (b < 0) {
                return "§c无法识别鼠标键：" + name;
            }
            // 鼠标没有「持续按住」状态，松开模式视为已释放
            if (m.equals("松开")) {
                return null;
            }
            if (b == 0) {
                ClickActions.leftClick();
                return null;
            }
            if (b == 1) {
                ClickActions.rightClick();
                return null;
            }
            return "§e中键/侧键只能绑定热键，暂不支持作为「按键」动作";
        }
        int[] codes = resolve(name);
        if (codes == null) {
            return "§c无法识别按键：" + (name == null ? "" : name) + "（可填 SPACE / A / F1 / SHIFT+1 / 空格 / 鼠标左键）";
        }
        if (m.equals("松开")) {
            for (int i = 0; i < codes.length; ++i) {
                unhold(codes[i]);
            }
            return null;
        }
        for (int i = 0; i < codes.length; ++i) {
            hold(codes[i]);
        }
        long now = System.currentTimeMillis();
        if (m.equals("按住")) {
            if (ms > 0) {
                PENDING.add(new Pending(codes, now + (long)ms));
            }
        } else {
            PENDING.add(new Pending(codes, now + (long)Math.max(40, ms <= 0 ? 200 : ms)));
        }
        return null;
    }

    /** 松开一个键名（可能是 SHIFT+1 这样的组合）。 */
    public static void release(String name) {
        int[] codes = resolve(name);
        if (codes == null) {
            return;
        }
        for (int i = 0; i < codes.length; ++i) {
            unhold(codes[i]);
        }
    }

    /** 到点自动松开。 */
    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
            Pending p = it.next();
            if (now >= p.at) {
                for (int i = 0; i < p.codes.length; ++i) {
                    unhold(p.codes[i]);
                }
                it.remove();
            }
        }
    }

    /** 这个键码现在是否被脚本按住（移动输入每帧按它叠加）。 */
    public static boolean held(int code) {
        if (code <= 0) {
            return false;
        }
        Integer n = HELD.get(code);
        return n != null && n.intValue() > 0;
    }

    /** 当前按住的键，给 /amc status 看。 */
    public static String heldSummary() {
        if (HELD.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer code : HELD.keySet()) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            String name = Keyboard.getKeyName(code.intValue());
            sb.append(name == null || name.isEmpty() ? String.valueOf(code) : name);
        }
        return sb.toString();
    }

    /** 是否是鼠标键名（MOUSE0 / 鼠标左键 …）。 */
    public static boolean isMouse(String name) {
        if (name == null) {
            return false;
        }
        String s = name.trim().toUpperCase(Locale.ROOT);
        return s.startsWith("MOUSE") || s.equals("\u9f20\u6807\u5de6\u952e") || s.equals("\u9f20\u6807\u53f3\u952e")
                || s.equals("\u5de6\u952e") || s.equals("\u53f3\u952e") || s.equals("\u4e2d\u952e")
                || s.equals("\u4fa7\u952e") || s.equals("\u4fa7\u952e1") || s.equals("\u4fa7\u952e2");
    }

    /** 鼠标键名转 LWJGL 按钮号（0=左 1=右 2=中 3/4=侧键），认不出返回 -1。 */
    public static int mouseButton(String name) {
        if (name == null) {
            return -1;
        }
        String s = name.trim();
        String up = s.toUpperCase(Locale.ROOT);
        if (up.startsWith("MOUSE")) {
            try {
                return Integer.parseInt(up.substring(5).trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        if (s.equals("\u5de6\u952e") || s.equals("\u9f20\u6807\u5de6\u952e")) {
            return 0;
        }
        if (s.equals("\u53f3\u952e") || s.equals("\u9f20\u6807\u53f3\u952e")) {
            return 1;
        }
        if (s.equals("\u4e2d\u952e")) {
            return 2;
        }
        if (s.equals("\u4fa7\u952e") || s.equals("\u4fa7\u952e1")) {
            return 3;
        }
        if (s.equals("\u4fa7\u952e2")) {
            return 4;
        }
        return -1;
    }

    /** 界面捕获到鼠标点击时存进去的写法。 */
    public static String describeMouse(int button) {
        return "MOUSE" + button;
    }

    /** 给界面显示用的中文名；键盘键名原样返回。 */
    public static String label(String name) {
        int b = KeyRouter.mouseButton(name);
        if (b >= 0) {
            switch (b) {
                case 0:
                    return "\u9f20\u6807\u5de6\u952e";
                case 1:
                    return "\u9f20\u6807\u53f3\u952e";
                case 2:
                    return "\u9f20\u6807\u4e2d\u952e";
                default:
                    return "\u9f20\u6807\u4fa7\u952e" + b;
            }
        }
        return name == null ? "" : name;
    }

    /** 应急松开所有键（停止项目、切界面、异常时调用），避免按键卡住。 */
    public static void releaseAll() {
        PENDING.clear();
        List<Integer> codes = new ArrayList<Integer>(HELD.keySet());
        HELD.clear();
        for (int i = 0; i < codes.size(); ++i) {
            apply(codes.get(i).intValue(), false);
        }
    }

    /** 界面里捕获按键时用：把 LWJGL 键码转成 "SHIFT+1" 这种带修饰键的写法。 */
    public static String describe(int lwjglKeyCode) {
        String base = Keyboard.getKeyName(lwjglKeyCode);
        if (base == null || base.isEmpty() || "NONE".equalsIgnoreCase(base)) {
            return "";
        }
        boolean selfModifier = "LSHIFT".equals(base) || "RSHIFT".equals(base)
                || "LCONTROL".equals(base) || "RCONTROL".equals(base)
                || "LMENU".equals(base) || "RMENU".equals(base);
        if (selfModifier) {
            return base;
        }
        String prefix = "";
        try {
            if (Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54)) {
                prefix = "SHIFT+";
            } else if (Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157)) {
                prefix = "CTRL+";
            } else if (Keyboard.isKeyDown(56) || Keyboard.isKeyDown(184)) {
                prefix = "ALT+";
            }
        } catch (Throwable ignored) {
            // 键盘还没创建（极少见），按不带修饰键处理
        }
        return prefix + base;
    }

    private static void hold(int code) {
        Integer c = HELD.get(code);
        int n = c == null ? 0 : c.intValue();
        HELD.put(code, n + 1);
        if (n == 0) {
            apply(code, true);
        }
    }

    private static void unhold(int code) {
        Integer c = HELD.get(code);
        if (c == null) {
            apply(code, false);
            return;
        }
        int n = c.intValue() - 1;
        if (n <= 0) {
            HELD.remove(code);
            apply(code, false);
        } else {
            HELD.put(code, n);
        }
    }

    /** 真正把键状态写进游戏。 */
    private static void apply(int code, boolean down) {
        if (code <= 0) {
            return;
        }
        setBuffer(code, down);
        try {
            KeyBinding.setKeyBindState(code, down);
            if (down) {
                KeyBinding.onTick(code);
            }
        } catch (Throwable ignored) {
            // 键位表还没初始化时忽略
        }
    }

    /**
     * 直接改 LWJGL 的按键状态缓冲。
     * 窗口不在前台时 LWJGL 收不到真实键盘事件，但 {@code Keyboard.isKeyDown} 读的就是这块缓冲，
     * 所以写进去等价于「这个键一直按着」，且不会漏到别的程序。
     */
    private static void setBuffer(int code, boolean down) {
        if (bufferBroken) {
            return;
        }
        try {
            if (bufferField == null) {
                bufferField = Keyboard.class.getDeclaredField("keyDownBuffer");
                bufferField.setAccessible(true);
            }
            ByteBuffer buffer = (ByteBuffer)bufferField.get(null);
            if (buffer == null || code >= buffer.capacity()) {
                return;
            }
            buffer.put(code, (byte)(down ? 1 : 0));
        } catch (Throwable t) {
            bufferBroken = true;
        }
    }

    /**
     * 解析键名，返回按键序列：前面是修饰键，最后一个是主键。
     * 解析不出来返回 null。
     */
    private static int[] resolve(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        String[] parts;
        if ("+".equals(s)) {
            parts = new String[]{"+"};
        } else if (s.indexOf('+') >= 0) {
            parts = s.split("\\+");
        } else {
            parts = new String[]{s};
        }
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < parts.length; ++i) {
            String seg = parts[i].trim();
            if (seg.isEmpty()) {
                continue;
            }
            int code = lookup(seg);
            if (code == 0) {
                return null;
            }
            if (!out.contains(Integer.valueOf(code))) {
                out.add(Integer.valueOf(code));
            }
        }
        if (out.isEmpty()) {
            return null;
        }
        int[] codes = new int[out.size()];
        for (int i = 0; i < codes.length; ++i) {
            codes[i] = out.get(i).intValue();
        }
        return codes;
    }

    private static int lookup(String seg) {
        String up = seg.trim().toUpperCase(Locale.ROOT);
        if (up.isEmpty()) {
            return 0;
        }
        String alias = ALIAS.get(up);
        if (alias != null && !alias.equalsIgnoreCase(up)) {
            return lookup(alias);
        }
        try {
            int idx = Keyboard.getKeyIndex(up);
            if (idx != 0) {
                return idx;
            }
        } catch (Throwable ignored) {
            // 键盘还没创建，继续用键位表兜底
        }
        return bindingCode(seg);
    }

    /** 支持 key.jump 和 jump 两种写法。 */
    private static int bindingCode(String seg) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.gameSettings == null || mc.gameSettings.keyBindings == null) {
                return 0;
            }
            String want = seg.trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < mc.gameSettings.keyBindings.length; ++i) {
                KeyBinding kb = mc.gameSettings.keyBindings[i];
                if (kb == null || kb.getKeyDescription() == null) {
                    continue;
                }
                String d = kb.getKeyDescription().toLowerCase(Locale.ROOT);
                if (d.equals(want) || d.replace("key.", "").equals(want)) {
                    return kb.getKeyCode();
                }
            }
        } catch (Throwable ignored) {
            // 客户端还没起来时查不到键位表
        }
        return 0;
    }

    private static void alias(String name, String target) {
        ALIAS.put(name.toUpperCase(Locale.ROOT), target);
    }

    static {
        alias("跳跃", "SPACE");
        alias("跳", "SPACE");
        alias("空格", "SPACE");
        alias("潜行", "LSHIFT");
        alias("下蹲", "LSHIFT");
        alias("蹲", "LSHIFT");
        alias("疾跑", "LCTRL");
        alias("冲刺", "LCTRL");
        alias("跑", "LCTRL");
        alias("背包", "E");
        alias("物品栏", "E");
        alias("丢弃", "Q");
        alias("扔", "Q");
        alias("聊天", "T");
        alias("前进", "W");
        alias("后退", "S");
        alias("左移", "A");
        alias("右移", "D");
        alias("视角", "F5");
        alias("截图", "F2");
        alias("回车", "RETURN");
        alias("删除", "DELETE");
        alias("退格", "BACKSPACE");
        alias("关闭界面", "ESCAPE");
    }
}

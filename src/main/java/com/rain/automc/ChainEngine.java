package com.rain.automc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/*
 * 脚本解释器。动作按行号顺序执行，支持：
 *   延迟后继续、循环开始/结束、跳转、条件跳转、等待条件、变量
 * 走路/交易为异步子任务，执行期间引擎挂起，结束后再按该行「后延迟」推进。
 * 长按、连跳这类需要跨 tick 的动作由 pending 任务队列驱动。
 */
@SideOnly(value=Side.CLIENT)
public class ChainEngine {
    private static boolean running = false;
    private static boolean awaiting = false;
    private static int chainIndex = -1;
    private static int actionIndex = 0;
    private static long waitUntil = 0L;
    private static String lastClickError = "";
    private static String lastMessage = "";
    private static final Map<String, String> vars = new HashMap<String, String>();
    private static final Random RAND = new Random();

    private static final int MAX_DEPTH = 16;
    private static final int[] loopStart = new int[MAX_DEPTH];
    private static final int[] loopLeft = new int[MAX_DEPTH];
    private static int loopDepth = 0;

    private static int pendKind = 0;
    private static int pendLeft = 0;
    private static int pendInterval = 100;
    private static long pendAt = 0L;
    private static String pendButton = "";
    private static long condDeadline = 0L;
    private static int condRow = -1;
    private static int tickSteps = 0;

    public static boolean isRunning() {
        return running;
    }

    public static int runningChain() {
        return running ? chainIndex : -1;
    }

    public static int runningAction() {
        return running ? actionIndex : -1;
    }

    public static String lastMessage() {
        return lastMessage;
    }

    public static String status() {
        if (!running) {
            return "\u7a7a\u95f2";
        }
        List<ScriptChain> list = ConfigManager.get().chains;
        if (chainIndex < 0 || chainIndex >= list.size()) {
            return "\u7a7a\u95f2";
        }
        ScriptChain c = list.get(chainIndex);
        return "\u8fd0\u884c " + c.name + " " + (actionIndex + 1) + "/" + c.actions.size();
    }

    public static boolean run(int idx) {
        if (running) {
            lastMessage = "\u00a7c\u5df2\u6709\u9879\u76ee\u5728\u8fd0\u884c\uff0c\u5148\u505c\u6b62";
            return false;
        }
        List<ScriptChain> list = ConfigManager.get().chains;
        if (idx < 0 || idx >= list.size()) {
            return false;
        }
        if (EFWalker.isBusy()) {
            lastMessage = "\u00a7c\u8d70\u8def\u5b50\u4efb\u52a1\u672a\u7ed3\u675f";
            return false;
        }
        ScriptChain c = list.get(idx);
        if (!c.isEnabled()) {
            lastMessage = "\u00a7c\u9879\u76ee\u5df2\u7981\u7528\uff1a" + c.name;
            return false;
        }
        if (c.actions.isEmpty()) {
            lastMessage = "\u00a7c\u9879\u76ee\u4e3a\u7a7a";
            return false;
        }
        chainIndex = idx;
        actionIndex = 0;
        running = true;
        awaiting = false;
        waitUntil = 0L;
        loopDepth = 0;
        pendKind = 0;
        pendLeft = 0;
        condRow = -1;
        vars.clear();
        tickSteps = 0;
        lastMessage = "\u00a7a\u5f00\u59cb\uff1a" + c.name;
        return true;
    }

    public static boolean runByName(String name) {
        List<ScriptChain> list = ConfigManager.get().chains;
        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i).name != null && list.get(i).name.equalsIgnoreCase(name)) {
                return run(i);
            }
        }
        return false;
    }

    public static void stop() {
        running = false;
        awaiting = false;
        chainIndex = -1;
        actionIndex = 0;
        waitUntil = 0L;
        loopDepth = 0;
        pendKind = 0;
        pendLeft = 0;
        EFWalker.stopWalk();
        PlayerActions.resetAll();
    }

    public static Map<String, String> vars() {
        return vars;
    }

    public static void tick() {
        if (!running) {
            return;
        }
        tickSteps = 0;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null) {
            stop();
            return;
        }
        List<ScriptChain> list = ConfigManager.get().chains;
        if (chainIndex < 0 || chainIndex >= list.size()) {
            stop();
            return;
        }
        ScriptChain c = list.get(chainIndex);

        if (pendKind != 0) {
            thisTickPending(c, player);
            return;
        }
        if (awaiting) {
            if (EFWalker.isBusy()) {
                return;
            }
            awaiting = false;
            ScriptAction prev = blockAt(c, actionIndex);
            if (prev != null) {
                waitUntil = System.currentTimeMillis();
            }
            advance(c);
            return;
        }
        if (System.currentTimeMillis() < waitUntil) {
            return;
        }
        if (actionIndex >= c.actions.size()) {
            finish(c);
            return;
        }
        ScriptAction a = c.actions.get(actionIndex);
        if (!a.enabled) {
            advance(c);
            return;
        }
        execute(a, c, player);
    }

    private static void thisTickPending(ScriptChain c, EntityPlayerSP player) {
        long now = System.currentTimeMillis();
        if (now < pendAt) {
            return;
        }
        if (pendKind == 1) {
            if (pendButton.equals("\u5de6")) {
                ClickActions.leftClick();
            } else {
                ClickActions.rightClick();
            }
        } else if (pendKind == 2) {
            PlayerActions.jump();
        }
        --pendLeft;
        pendAt = now + (long)Math.max(20, pendInterval);
        if (pendLeft <= 0) {
            pendKind = 0;
            ScriptAction a = blockAt(c, actionIndex);
            waitUntil = now;
            advance(c);
        }
    }

    private static ScriptAction blockAt(ScriptChain c, int i) {
        return i < 0 || i >= c.actions.size() ? null : c.actions.get(i);
    }

    private static void execute(ScriptAction a, ScriptChain c, EntityPlayerSP player) {
        String t = a.type;
        long now = System.currentTimeMillis();

        if (a.isEvent()) {
            advance(c);
            return;
        }

        if (t.equals("\u53d1\u9001\u804a\u5929")) {
            String text = a.param(0);
            if (!text.isEmpty()) {
                player.sendChatMessage(text);
            }
            after(a, c);
        } else if (t.equals("\u53d1\u9001\u6307\u4ee4")) {
            String text = a.param(0);
            if (!text.isEmpty()) {
                player.sendChatMessage(text.startsWith("/") ? text : "/" + text);
            }
            after(a, c);
        } else if (t.equals("\u63d0\u793a\u81ea\u5df1")) {
            PlayerActions.chatSelf(a.param(0));
            after(a, c);
        } else if (t.equals("\u5ef6\u8fdf")) {
            waitUntil = now + Math.max(0, a.intParam(0, 0));
            advance(c);
        } else if (t.equals("\u968f\u673a\u5ef6\u8fdf")) {
            int lo = Math.max(0, a.intParam(0, 500));
            int hi = Math.max(lo, a.intParam(1, 1500));
            int ms = lo + (hi > lo ? RAND.nextInt(hi - lo + 1) : 0);
            waitUntil = now + (long)ms;
            advance(c);
        } else if (t.equals("\u5faa\u73af\u5f00\u59cb")) {
            int times = Math.max(1, a.intParam(0, 3));
            if (loopDepth < MAX_DEPTH) {
                loopStart[loopDepth] = actionIndex + 1;
                loopLeft[loopDepth] = times;
                ++loopDepth;
            }
            advance(c);
        } else if (t.equals("\u5faa\u73af\u7ed3\u675f")) {
            if (loopDepth > 0) {
                int d = loopDepth - 1;
                --loopLeft[d];
                if (loopLeft[d] > 0) {
                    actionIndex = loopStart[d];
                    condRow = -1;
                    stepGuard(c);
                    return;
                }
                loopDepth = d;
            }
            advance(c);
        } else if (t.equals("\u8df3\u8f6c")) {
            jumpTo(a.intParam(0, 1), c);
        } else if (t.equals("\u6761\u4ef6\u8df3\u8f6c")) {
            if (Cond.eval(a.param(0), vars)) {
                jumpTo(a.intParam(1, 1), c);
            } else {
                after(a, c);
            }
        } else if (t.equals("\u7b49\u5f85\u6761\u4ef6")) {
            boolean ok = Cond.eval(a.param(0), vars);
            if (ok) {
                condRow = -1;
                after(a, c);
                return;
            }
            if (condRow != actionIndex) {
                condRow = actionIndex;
                condDeadline = now + Math.max(0, a.intParam(1, 10000));
                return;
            }
            if (now >= condDeadline) {
                condRow = -1;
                int row = a.intParam(2, 0);
                if (row > 0) {
                    jumpTo(row, c);
                } else {
                    lastMessage = "\u00a7e\u7b49\u5f85\u8d85\u65f6\uff0c\u7ee7\u7eed";
                    after(a, c);
                }
            }
        } else if (t.equals("\u53d8\u91cf\u8bbe\u7f6e")) {
            vars.put(a.param(0).trim(), a.param(1));
            after(a, c);
        } else if (t.equals("\u53d8\u91cf\u8fd0\u7b97")) {
            String name = a.param(0).trim();
            double cur = Cond.num(vars.containsKey(name) ? vars.get(name) : "0", 0.0);
            double v = Cond.num(a.param(2), 0.0);
            String op = a.param(1).trim();
            double r = cur;
            if (op.equals("+")) {
                r = cur + v;
            } else if (op.equals("-")) {
                r = cur - v;
            } else if (op.equals("*")) {
                r = cur * v;
            } else if (op.equals("/")) {
                r = v == 0.0 ? cur : cur / v;
            } else if (op.equals("%")) {
                r = v == 0.0 ? 0.0 : cur % v;
            }
            vars.put(name, r == Math.floor(r) ? String.valueOf((long)r) : String.valueOf(r));
            after(a, c);
        } else if (t.equals("\u7ed3\u675f\u811a\u672c")) {
            lastMessage = "\u00a7a\u811a\u672c\u63d0\u524d\u7ed3\u675f\uff1a" + c.name;
            running = false;
            awaiting = false;
            chainIndex = -1;
            loopDepth = 0;
        } else if (t.equals("\u53f3\u952e") || t.equals("\u4f7f\u7528\u624b\u4e2d\u7269\u54c1")) {
            ClickActions.rightClick();
            after(a, c);
        } else if (t.equals("\u5de6\u952e")) {
            ClickActions.leftClick();
            after(a, c);
        } else if (t.equals("\u957f\u6309\u53f3\u952e") || t.equals("\u957f\u6309\u5de6\u952e")) {
            pendKind = 1;
            pendButton = t.equals("\u957f\u6309\u5de6\u952e") ? "\u5de6" : "\u53f3";
            pendLeft = Math.max(1, a.intParam(0, 10));
            pendInterval = Math.max(20, a.intParam(1, 100));
            pendAt = now;
            thisTickPending(c, player);
        } else if (t.equals("\u653b\u51fb\u6700\u8fd1\u5b9e\u4f53")) {
            if (!PlayerActions.attackNearest(a.intParam(0, 4), a.param(1))) {
                lastMessage = "\u00a7e\u9644\u8fd1\u6ca1\u6709\u53ef\u653b\u51fb\u76ee\u6807";
            }
            after(a, c);
        } else if (t.equals("\u70b9\u51fb\u5750\u6807")) {
            lastMessage = clickAt(a);
            after(a, c);
        } else if (t.equals("\u6361\u6389\u843d\u7269")) {
            String r = EFWalker.startItemWalk(player, a.intParam(0, 8), a.intParam(1, 5), a.intParam(2, 8));
            if (r == null) {
                awaiting = true;
            } else {
                lastMessage = r;
                after(a, c);
            }
        } else if (t.equals("\u8d70\u5230\u65b9\u5757")) {
            String r = EFWalker.startTargetWalk(player, a.param(0), a.intParam(1, 24), a.intParam(2, 8), a.intParam(3, 24));
            if (r == null) {
                awaiting = true;
            } else {
                lastMessage = r;
                after(a, c);
            }
        } else if (t.equals("\u8d70\u5230\u5750\u6807")) {
            String r = EFWalker.startCoordWalk(player, a.doubleParam(0, 0.0), a.doubleParam(1, 64.0),
                    a.doubleParam(2, 0.0), a.doubleParam(3, 0.5));
            if (r == null) {
                awaiting = true;
            } else {
                lastMessage = r;
                after(a, c);
            }
        } else if (t.equals("\u524d\u8fdb")) {
            PlayerActions.holdForward(a.intParam(0, 500));
            after(a, c);
        } else if (t.equals("\u505c\u6b62\u79fb\u52a8")) {
            PlayerActions.stopMove();
            after(a, c);
        } else if (t.equals("\u8df3\u8dc3")) {
            pendKind = 2;
            pendLeft = Math.max(1, a.intParam(0, 1));
            pendInterval = 120;
            pendAt = now;
            thisTickPending(c, player);
        } else if (t.equals("\u6f5c\u884c")) {
            PlayerActions.sneak(!a.param(0).equals("\u5173"));
            after(a, c);
        } else if (t.equals("\u75be\u8dd1")) {
            PlayerActions.sprint(!a.param(0).equals("\u5173"));
            after(a, c);
        } else if (t.equals("\u5207\u6362\u5feb\u6377\u680f")) {
            ClickActions.selectHotbar(a.intParam(0, 1));
            after(a, c);
        } else if (t.equals("\u4e22\u5f03\u624b\u4e2d")) {
            PlayerActions.drop(a.param(0).equals("\u5168\u90e8"));
            after(a, c);
        } else if (t.equals("\u8f6c\u5411")) {
            PlayerActions.look(a.doubleParam(0, 0.0) == 0.0 ? 0.0f : (float)a.doubleParam(0, 0.0),
                    (float)a.doubleParam(1, 0.0));
            after(a, c);
        } else if (t.equals("\u770b\u5411\u6700\u8fd1\u73a9\u5bb6")) {
            if (!PlayerActions.lookAtNearestPlayer()) {
                lastMessage = "\u00a7e\u9644\u8fd1\u6ca1\u6709\u5176\u4ed6\u73a9\u5bb6";
            }
            after(a, c);
        } else if (t.equals("\u6253\u5f00\u80cc\u5305")) {
            PlayerActions.openInventory();
            after(a, c);
        } else if (t.equals("\u5173\u95ed\u754c\u9762")) {
            PlayerActions.closeScreen();
            after(a, c);
        } else if (t.equals("\u6309\u952e")) {
            if (!PlayerActions.pressKey(a.param(0), a.intParam(1, 200))) {
                lastMessage = "\u00a7e\u627e\u4e0d\u5230\u6309\u952e\uff1a" + a.param(0);
            }
            after(a, c);
        } else if (t.equals("\u590d\u5236\u5230\u526a\u8d34\u677f")) {
            PlayerActions.clipboard(a.param(0));
            after(a, c);
        } else {
            after(a, c);
        }
    }

    private static void jumpTo(int row1based, ScriptChain c) {
        int target = Math.max(1, row1based) - 1;
        if (target >= c.actions.size()) {
            finish(c);
            return;
        }
        actionIndex = target;
        condRow = -1;
        stepGuard(c);
    }

    private static void after(ScriptAction a, ScriptChain c) {
        waitUntil = System.currentTimeMillis();
        advance(c);
    }

    private static void advance(ScriptChain c) {
        ++actionIndex;
        while (actionIndex < c.actions.size() && !c.actions.get(actionIndex).enabled) {
            ++actionIndex;
            ++tickSteps;
        }
        if (actionIndex >= c.actions.size()) {
            finish(c);
            return;
        }
        stepGuard(c);
    }

    /* 防止 0 延迟跳转把主线程卡死：单 tick 步数超限就延后到下一 tick */
    private static void stepGuard(ScriptChain c) {
        if (++tickSteps > 150) {
            waitUntil = System.currentTimeMillis() + 50L;
        }
    }

    private static void finish(ScriptChain c) {
        lastMessage = "\u00a7a\u5b8c\u6210\uff1a" + c.name;
        running = false;
        awaiting = false;
        chainIndex = -1;
        actionIndex = 0;
        waitUntil = 0L;
        loopDepth = 0;
        pendKind = 0;
    }

    public static List<String> describeVars() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String> en : vars.entrySet()) {
            out.add(en.getKey() + "=" + en.getValue());
        }
        return out;
    }

    /* 在屏幕任意位置按真实鼠标点一下（坐标为窗口像素，左上角为 0,0） */
    private static String clickAt(ScriptAction a) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return "§c没有玩家";
        }
        int x = a.intParam(0, 0);
        int y = a.intParam(1, 0);
        String btn = a.param(2) == null ? "" : a.param(2);
        int button = btn.contains("右") ? 1 : 0;
        String lower = btn.toLowerCase();
        boolean shift = lower.contains("shift") || lower.contains("快速");
        // 逻辑层优先：槽位与标准按钮都能命中，不受窗口是否前台影响。
        // 命中不了（世界内、纯自绘界面）再退回真鼠标，由它处理需要焦点的情况。
        String logical = ScreenClicker.clickLogical(x, y, button, shift);
        if (logical == null) {
            lastClickError = "";
            return null;
        }
        String error = ScreenClicker.click(x, y, button, shift);
        if (error == null) {
            lastClickError = "";
            return null;
        }
        if (!error.equals(lastClickError)) {
            lastClickError = error;
            PlayerActions.chatSelf(error);
        }
        return error;
    }
}

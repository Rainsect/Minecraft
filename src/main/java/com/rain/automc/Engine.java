package com.rain.automc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 脚本引擎。一个项目 = 一条脚本（行号顺序执行），支持：
 * 延迟、循环、跳转、等待条件、并行段。
 *
 * <p>并行：一个项目可以同时跑多条 {@link Lane}。{@code 并行开始 … 并行结束} 之间的内容被
 * fork 成一条独立通道，主流程继续往下走，两边同时推进（例如同时按住 W 与跳跃）。
 *
 * <p>所有状态都在实例里，单线程 tick，不再有 static 全局状态。
 */
@SideOnly(value=Side.CLIENT)
public final class Engine {
    private static final int MAX_LANES = 16;
    private static final int MAX_DEPTH = 16;
    private static final int STEP_BUDGET = 120;
    private static final Random RAND = new Random();

    private static final Engine INSTANCE = new Engine();

    private final List<Lane> lanes = new ArrayList<Lane>();
    private int chainIndex = -1;
    private boolean running;
    private int tickSteps;
    private String lastMessage = "";

    private Engine() {
    }

    public static Engine get() {
        return INSTANCE;
    }

    /** 一条并行通道：行号 + 自己的等待/循环/条件状态。 */
    private static final class Lane {
        int pc;
        long waitUntil;
        int loopDepth;
        final int[] loopStart = new int[MAX_DEPTH];
        final int[] loopLeft = new int[MAX_DEPTH];
        int condRow = -1;
        long condDeadline;
        int jumpLeft;
        long jumpAt;
        boolean awaiting;
        boolean done;
        /** fork 出来的通道的结束行（不含），-1 表示到项目末尾。 */
        int end = -1;
    }

    public boolean isRunning() {
        return this.running;
    }

    public int runningChain() {
        return this.running ? this.chainIndex : -1;
    }

    public String lastMessage() {
        return this.lastMessage;
    }

    public String status() {
        if (!this.running) {
            return "空闲";
        }
        List<ScriptChain> list = ConfigManager.get().chains;
        if (this.chainIndex < 0 || this.chainIndex >= list.size()) {
            return "空闲";
        }
        ScriptChain c = list.get(this.chainIndex);
        String s = "运行 " + c.name;
        if (!this.lanes.isEmpty()) {
            s = s + " " + (this.lanes.get(0).pc + 1) + "/" + c.actions.size();
        }
        if (this.lanes.size() > 1) {
            s = s + " · 并行 " + this.lanes.size();
        }
        return s;
    }

    public boolean run(int idx) {
        if (this.running) {
            this.lastMessage = "§c已有项目在运行，先停止";
            return false;
        }
        List<ScriptChain> list = ConfigManager.get().chains;
        if (idx < 0 || idx >= list.size()) {
            return false;
        }
        if (Walker.isBusy()) {
            this.lastMessage = "§c走路子任务未结束";
            return false;
        }
        ScriptChain c = list.get(idx);
        if (!c.isEnabled()) {
            this.lastMessage = "§c项目已禁用：" + c.name;
            return false;
        }
        if (c.actions.isEmpty()) {
            this.lastMessage = "§c项目为空";
            return false;
        }
        this.chainIndex = idx;
        this.lanes.clear();
        Lane lane = new Lane();
        lane.pc = 0;
        lane.end = c.actions.size();
        this.lanes.add(lane);
        this.running = true;
        this.lastMessage = "§a开始：" + c.name;
        return true;
    }

    public boolean runByName(String name) {
        List<ScriptChain> list = ConfigManager.get().chains;
        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i).name != null && list.get(i).name.equalsIgnoreCase(name)) {
                return this.run(i);
            }
        }
        return false;
    }

    public void stop() {
        this.running = false;
        this.chainIndex = -1;
        this.lanes.clear();
        Walker.stop();
        AutoFishing.stop();
        PlayerActions.resetAll();
    }

    public void tick() {
        if (!this.running) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null) {
            this.stop();
            return;
        }
        List<ScriptChain> list = ConfigManager.get().chains;
        if (this.chainIndex < 0 || this.chainIndex >= list.size()) {
            this.stop();
            return;
        }
        ScriptChain c = list.get(this.chainIndex);
        this.tickSteps = 0;
        List<Lane> snapshot = new ArrayList<Lane>(this.lanes);
        for (int i = 0; i < snapshot.size(); ++i) {
            Lane l = snapshot.get(i);
            if (l.done) {
                continue;
            }
            if (this.tickSteps > STEP_BUDGET) {
                break;
            }
            this.runLane(l, c, player);
        }
        for (Iterator<Lane> it = this.lanes.iterator(); it.hasNext();) {
            if (it.next().done) {
                it.remove();
            }
        }
        if (this.lanes.isEmpty()) {
            if (this.running) {
                this.finish(c);
            }
        }
    }

    private int bound(Lane l, ScriptChain c) {
        int b = l.end >= 0 ? l.end : c.actions.size();
        return Math.min(Math.max(0, b), c.actions.size());
    }

    private void runLane(Lane l, ScriptChain c, EntityPlayerSP p) {
        if (!this.running) {
            l.done = true;
            return;
        }
        long now = System.currentTimeMillis();
        if (l.awaiting) {
            if (Walker.isBusy() || AutoFishing.isBusy()) {
                return;
            }
            l.awaiting = false;
            this.advance(l, c);
            return;
        }
        if (l.jumpLeft > 0) {
            if (now < l.jumpAt) {
                return;
            }
            if (PlayerActions.jump()) {
                --l.jumpLeft;
            }
            l.jumpAt = now + 120L;
            if (l.jumpLeft <= 0) {
                this.advance(l, c);
            }
            return;
        }
        if (now < l.waitUntil) {
            return;
        }
        while (!l.done) {
            if (++this.tickSteps > STEP_BUDGET) {
                l.waitUntil = System.currentTimeMillis() + 30L;
                return;
            }
            if (l.pc >= this.bound(l, c)) {
                l.done = true;
                return;
            }
            ScriptAction a = c.actions.get(l.pc);
            if (!a.enabled || a.isEvent()) {
                this.advance(l, c);
                continue;
            }
            this.exec(l, a, c, p);
            if (l.done || l.awaiting || l.jumpLeft > 0) {
                return;
            }
            if (System.currentTimeMillis() < l.waitUntil) {
                return;
            }
        }
    }

    private void advance(Lane l, ScriptChain c) {
        ++l.pc;
        int b = this.bound(l, c);
        while (l.pc < b && !c.actions.get(l.pc).enabled) {
            ++l.pc;
        }
        if (l.pc >= b) {
            l.done = true;
        }
    }

    private void jumpTo(Lane l, int row1based, ScriptChain c) {
        int target = Math.max(1, row1based) - 1;
        if (target >= this.bound(l, c)) {
            l.done = true;
            return;
        }
        l.pc = target;
        l.condRow = -1;
    }

    private void exec(Lane l, ScriptAction a, ScriptChain c, EntityPlayerSP p) {
        String t = a.type;
        long now = System.currentTimeMillis();

        if (t.equals("发送消息")) {
            String text = a.param(0);
            if (!text.isEmpty()) {
                if (a.param(1).equals("仅自己可见")) {
                    PlayerActions.chatSelf(text);
                } else {
                    PlayerActions.chat(text);
                }
            }
            this.advance(l, c);
        } else if (t.equals("按键")) {
            String mode = a.param(2);
            int ms = a.intParam(1, 200);
            String err = KeyRouter.press(a.param(0), mode, ms);
            if (err != null) {
                this.lastMessage = err;
            }
            this.advance(l, c);
            // 点按 / 按住 有毫秒数时，占住这一条通道，等按键真正松开再往下走
            if (ms > 0 && !mode.equals("松开")) {
                l.waitUntil = now + (long)ms;
            }
        } else if (t.equals("点击坐标")) {
            String btn = a.param(2);
            int button = btn.contains("右") ? 1 : 0;
            String lower = btn.toLowerCase();
            boolean shift = lower.contains("shift");
            String err = ScreenClicker.click(a.intParam(0, 0), a.intParam(1, 0), button, shift);
            if (err != null) {
                this.lastMessage = err;
            }
            this.advance(l, c);
        } else if (t.equals("攻击最近实体")) {
            if (!PlayerActions.attackNearest(a.intParam(0, 4), a.param(1))) {
                this.lastMessage = "§e附近没有可攻击目标";
            }
            this.advance(l, c);
        } else if (t.equals("切换快捷栏")) {
            if (!ClickActions.selectHotbar(a.intParam(0, 1))) {
                this.lastMessage = "§e快捷栏格子只能是 1-9";
            }
            this.advance(l, c);
        } else if (t.equals("丢弃手中")) {
            PlayerActions.drop(a.param(0).equals("全部"));
            this.advance(l, c);
        } else if (t.equals("使用手中物品")) {
            ClickActions.useItem();
            this.advance(l, c);
        } else if (t.equals("自动钓鱼")) {
            String r = AutoFishing.start(a.param(2), a.intParam(0, 800), a.intParam(1, 30));
            if (r == null) {
                l.awaiting = true;
            } else {
                this.lastMessage = r;
                this.advance(l, c);
            }
        } else if (t.equals("延迟")) {
            l.waitUntil = now + (long)delayMs(a);
            ++l.pc;
        } else if (t.equals("循环开始")) {
            if (l.loopDepth < MAX_DEPTH) {
                l.loopStart[l.loopDepth] = l.pc + 1;
                l.loopLeft[l.loopDepth] = Math.max(1, a.intParam(0, 3));
                ++l.loopDepth;
            }
            ++l.pc;
        } else if (t.equals("循环结束")) {
            if (l.loopDepth > 0) {
                int d = l.loopDepth - 1;
                --l.loopLeft[d];
                l.condRow = -1;
                if (l.loopLeft[d] > 0) {
                    l.pc = l.loopStart[d];
                    return;
                }
                l.loopDepth = d;
            }
            ++l.pc;
        } else if (t.equals("跳转")) {
            String cond = a.param(0);
            if (cond.isEmpty() || Cond.eval(cond)) {
                this.jumpTo(l, a.intParam(1, 1), c);
            } else {
                this.advance(l, c);
            }
        } else if (t.equals("等待条件")) {
            if (Cond.eval(a.param(0))) {
                l.condRow = -1;
                this.advance(l, c);
                return;
            }
            if (l.condRow != l.pc) {
                l.condRow = l.pc;
                l.condDeadline = now + (long)Math.max(0, a.intParam(1, 10000));
                l.waitUntil = now + 50L;
                return;
            }
            if (now >= l.condDeadline) {
                l.condRow = -1;
                int row = a.intParam(2, 0);
                if (row > 0) {
                    this.jumpTo(l, row, c);
                } else {
                    this.advance(l, c);
                }
            }
        } else if (t.equals("结束脚本")) {
            this.lastMessage = "§a脚本提前结束：" + c.name;
            l.done = true;
            this.stop();
        } else if (t.equals("并行开始")) {
            this.fork(l, c);
        } else if (t.equals("并行结束")) {
            l.done = true;
        } else if (t.equals("走到坐标")) {
            String r = Walker.startCoordWalk(p, a.doubleParam(0, 0.0), a.doubleParam(1, 64.0),
                    a.doubleParam(2, 0.0), a.doubleParam(3, 0.5),
                    a.param(4).equals("开"), a.param(5).equals("开"), a.param(6).equals("开"));
            this.walkResult(l, r, c);
        } else if (t.equals("走到方块")) {
            String r = Walker.startBlockWalk(p, a.param(0), a.intParam(1, 24), a.intParam(2, 8),
                    a.intParam(3, 24), a.param(4).equals("开"), a.param(5).equals("开"), a.param(6).equals("开"));
            this.walkResult(l, r, c);
        } else if (t.equals("捡掉落物")) {
            String r = Walker.startItemWalk(p, a.intParam(0, 8), a.intParam(1, 5), a.intParam(2, 8));
            this.walkResult(l, r, c);
        } else if (t.equals("停止移动")) {
            Walker.stop();
            this.advance(l, c);
        } else if (t.equals("跳跃")) {
            l.jumpLeft = Math.max(1, a.intParam(0, 1));
            l.jumpAt = now;
        } else if (t.equals("潜行")) {
            PlayerActions.sneak(!a.param(0).equals("关"));
            this.advance(l, c);
        } else if (t.equals("转向")) {
            PlayerActions.look((float)a.doubleParam(0, 0.0), (float)a.doubleParam(1, 0.0));
            this.advance(l, c);
        } else if (t.equals("看向最近玩家")) {
            if (!PlayerActions.lookAtNearestPlayer()) {
                this.lastMessage = "§e附近没有其他玩家";
            }
            this.advance(l, c);
        } else {
            this.advance(l, c);
        }
    }

    private void walkResult(Lane l, String r, ScriptChain c) {
        if (r == null) {
            l.awaiting = true;
            return;
        }
        this.lastMessage = r;
        this.advance(l, c);
    }

    /** 把「并行开始 … 并行结束」之间的内容 fork 成独立通道，主流程跳到结束之后。 */
    private void fork(Lane l, ScriptChain c) {
        int b = this.bound(l, c);
        int end = this.findParallelEnd(c, l.pc + 1, b);
        if (end < 0) {
            end = b;
        }
        if (end > l.pc + 1 && this.lanes.size() < MAX_LANES) {
            Lane child = new Lane();
            child.pc = l.pc + 1;
            child.end = end;
            this.lanes.add(child);
        }
        l.pc = Math.min(end + 1, b);
        if (l.pc >= b) {
            l.done = true;
        }
    }

    private int findParallelEnd(ScriptChain c, int from, int to) {
        int depth = 0;
        for (int i = from; i < to; ++i) {
            String t = c.actions.get(i).type;
            if (t.equals("并行开始")) {
                ++depth;
            } else if (t.equals("并行结束")) {
                if (depth == 0) {
                    return i;
                }
                --depth;
            }
        }
        return -1;
    }

    private static int delayMs(ScriptAction a) {
        if (a.param(3).equals("随机")) {
            int lo = Math.max(0, a.intParam(1, 500));
            int hi = Math.max(lo, a.intParam(2, 1500));
            return lo + (hi > lo ? RAND.nextInt(hi - lo + 1) : 0);
        }
        return Math.max(0, a.intParam(0, 1000));
    }

    private void finish(ScriptChain c) {
        this.lastMessage = "§a完成：" + c.name;
        this.running = false;
        this.chainIndex = -1;
        this.lanes.clear();
        AutoFishing.stop();
        PlayerActions.resetAll();
    }
}

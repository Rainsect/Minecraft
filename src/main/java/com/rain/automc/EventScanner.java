package com.rain.automc;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/*
 * 事件侦测：把一个项目里所有「事件」积木逐个求值，命中就启动该项目。
 * 每秒轮询两次重条件，进服/死亡/复活/受伤/切维度/聊天走即时分支。
 */
@SideOnly(value=Side.CLIENT)
public class EventScanner {
    private EntityPlayerSP prevPlayer = null;
    private boolean deathFired = false;
    private boolean hurtArmed = true;
    private float lastHealth = 20.0f;
    private int lastDim = Integer.MIN_VALUE;
    private String lastHeld = "";
    private long scanAcc = 0L;
    private long lastChatAt = 0L;
    private String lastChat = "";
    private long[] nextFire = new long[0];
    private long[] timerAcc = new long[0];

    public void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null) {
            this.prevPlayer = null;
            this.deathFired = false;
            this.lastDim = Integer.MIN_VALUE;
            this.lastHeld = "";
            return;
        }
        int n = ConfigManager.get().chains.size();
        if (this.nextFire.length != n) {
            long[] a = new long[n];
            long[] b = new long[n];
            for (int i = 0; i < Math.min(n, this.nextFire.length); ++i) {
                a[i] = this.nextFire[i];
                b[i] = this.timerAcc[i];
            }
            this.nextFire = a;
            this.timerAcc = b;
        }
        long now = System.currentTimeMillis();

        if (player != this.prevPlayer) {
            boolean joined = this.prevPlayer == null;
            this.prevPlayer = player;
            this.lastHealth = player.getHealth();
            if (joined) {
                this.deathFired = false;
                this.hurtArmed = true;
                this.fire("\u8fdb\u670d", now, null);
            }
        }

        float hp = player.getHealth();
        if (hp <= 0.0f) {
            if (!this.deathFired) {
                this.deathFired = true;
                this.fire("\u6b7b\u4ea1", now, null);
            }
            this.hurtArmed = false;
        } else {
            if (this.deathFired) {
                this.deathFired = false;
                this.fire("\u590d\u6d3b", now, null);
            }
            if (this.hurtArmed && hp < this.lastHealth - 0.01f) {
                this.fire("\u53d7\u4f24", now, null);
            }
            this.hurtArmed = true;
        }
        this.lastHealth = hp;

        int dim = player.dimension;
        if (this.lastDim == Integer.MIN_VALUE) {
            this.lastDim = dim;
        } else if (dim != this.lastDim) {
            this.lastDim = dim;
            this.fire("\u5207\u6362\u7ef4\u5ea6", now, null);
        }

        this.scanAcc += 50L;
        if (this.scanAcc >= 500L) {
            this.scanAcc = 0L;
            this.scan(player, mc, now);
        }

        if (!this.lastChat.isEmpty() && now - this.lastChatAt > 600L) {
            this.lastChat = "";
        }
    }

    private void scan(EntityPlayerSP player, Minecraft mc, long now) {
        String held = this.heldName(player);
        if (!held.equals(this.lastHeld)) {
            if (!this.lastHeld.isEmpty()) {
                this.fire("\u624b\u6301\u53d8\u5316", now, null);
            }
            this.lastHeld = held;
        }
        List<ScriptChain> list = ConfigManager.get().chains;
        for (int i = 0; i < list.size(); ++i) {
            ScriptChain c = list.get(i);
            if (!c.auto() || !c.isEnabled()) {
                continue;
            }
            if (now < this.nextFire[i]) {
                continue;
            }
            boolean hit = false;
            for (int k = 0; k < c.actions.size() && !hit; ++k) {
                ScriptAction a = c.actions.get(k);
                if (!a.enabled || !a.isEvent() || a.type.equals("\u624b\u52a8")) {
                    continue;
                }
                hit = this.match(a, player, mc, now, i);
            }
            if (hit) {
                this.start(i, now);
            }
        }
    }

    private boolean match(ScriptAction a, EntityPlayerSP p, Minecraft mc, long now, int idx) {
        String t = a.type;
        if (t.equals("\u5b9a\u65f6")) {
            int sec = Math.max(1, a.intParam(0, 60));
            this.timerAcc[idx] += 500L;
            if (this.timerAcc[idx] >= (long)sec * 1000L) {
                this.timerAcc[idx] = 0L;
                return true;
            }
            return false;
        }
        if (t.equals("\u8840\u91cf\u4f4e\u4e8e")) {
            return p.getHealth() > 0.0f && p.getHealth() <= (float)a.intParam(0, 10);
        }
        if (t.equals("\u9965\u997f\u4f4e\u4e8e")) {
            return p.getFoodStats().getFoodLevel() <= a.intParam(0, 6);
        }
        if (t.equals("\u80cc\u5305\u7269\u54c1")) {
            return NpcTrader.countItem(p, a.param(0)) >= Math.max(1, a.intParam(1, 64));
        }
        if (t.equals("\u80cc\u5305\u6ee1")) {
            return NpcTrader.inventoryFull(p);
        }
        if (t.equals("\u7ecf\u9a8c\u7b49\u7ea7")) {
            return p.experienceLevel >= a.intParam(0, 30);
        }
        if (t.equals("\u9644\u8fd1\u6389\u843d\u7269")) {
            int r = Math.max(1, a.intParam(0, 8));
            AxisAlignedBB box = new AxisAlignedBB(p.posX - (double)r, p.posY - (double)r,
                    p.posZ - (double)r, p.posX + (double)r, p.posY + (double)r,
                    p.posZ + (double)r);
            return !mc.world.getEntitiesWithinAABB(EntityItem.class, box).isEmpty();
        }
        if (t.equals("\u9644\u8fd1\u73a9\u5bb6")) {
            int r = Math.max(1, a.intParam(0, 16));
            List<EntityPlayer> ps = mc.world.playerEntities;
            for (int i = 0; i < ps.size(); ++i) {
                EntityPlayer e = ps.get(i);
                if (e != p && e.isEntityAlive() && p.getDistanceSq((Entity)e) <= (double)(r * r)) {
                    return true;
                }
            }
            return false;
        }
        if (t.equals("\u5230\u8fbe\u5750\u6807")) {
            double dx = a.doubleParam(0, 0.0) - p.posX;
            double dz = a.doubleParam(2, 0.0) - p.posZ;
            double r = Math.max(0.5, a.doubleParam(3, 3.0));
            return dx * dx + dz * dz <= r * r;
        }
        if (t.equals("Y\u4f4e\u4e8e")) {
            return p.posY < a.doubleParam(0, 0.0);
        }
        if (t.equals("\u6e38\u620f\u65f6\u95f4")) {
            long time = mc.world.getWorldTime() % 24000L;
            int target = a.intParam(0, 13000);
            int tol = Math.max(10, a.intParam(1, 200));
            return Math.abs(time - (long)target) <= (long)tol || Math.abs(time - (long)target + 24000L) <= (long)tol;
        }
        if (t.equals("\u8010\u4e45\u504f\u4f4e")) {
            ItemStack st = p.getHeldItemMainhand();
            if (st == null || st.isEmpty() || st.getMaxDamage() <= 0) {
                return false;
            }
            double pct = 100.0 * (double)(st.getMaxDamage() - st.getItemDamage()) / (double)st.getMaxDamage();
            return pct <= (double)a.intParam(0, 20);
        }
        if (t.equals("\u804a\u5929\u542b\u5173\u952e\u8bcd")) {
            String kw = a.param(0);
            return !kw.isEmpty() && !this.lastChat.isEmpty() && this.lastChat.contains(kw);
        }
        return false;
    }

    private String heldName(EntityPlayerSP p) {
        ItemStack st = p.getHeldItemMainhand();
        if (st == null || st.isEmpty()) {
            return "\u7a7a";
        }
        return st.getDisplayName();
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        ITextComponent msg = event.getMessage();
        if (msg == null) {
            return;
        }
        this.lastChat = msg.getUnformattedText();
        this.lastChatAt = System.currentTimeMillis();
        this.fire("\u804a\u5929\u542b\u5173\u952e\u8bcd", this.lastChatAt, this.lastChat);
    }

    /* 立即求值某一类事件，命中则启动对应项目 */
    private void fire(String eventType, long now, String chatText) {
        List<ScriptChain> list = ConfigManager.get().chains;
        if (this.nextFire.length != list.size()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null) {
            return;
        }
        for (int i = 0; i < list.size(); ++i) {
            ScriptChain c = list.get(i);
            if (!c.isEnabled()) {
                continue;
            }
            for (int k = 0; k < c.actions.size(); ++k) {
                ScriptAction a = c.actions.get(k);
                if (!a.enabled || !a.isEvent() || !a.type.equals(eventType)) {
                    continue;
                }
                if (eventType.equals("\u804a\u5929\u542b\u5173\u952e\u8bcd")) {
                    String kw = a.param(0);
                    if (kw.isEmpty() || chatText == null || !chatText.contains(kw)) {
                        continue;
                    }
                } else if (eventType.equals("\u8fdb\u670d") && !p.world.isBlockLoaded(p.getPosition())) {
                    continue;
                }
                if (now < this.nextFire[i]) {
                    break;
                }
                this.start(i, now);
                break;
            }
        }
    }

    private void start(int idx, long now) {
        ScriptChain c = ConfigManager.get().chains.get(idx);
        int cd = Math.max(1, c.cooldownSec);
        this.nextFire[idx] = now + (long)cd * 1000L;
        if (ChainEngine.isRunning()) {
            return;
        }
        if (!ChainEngine.run(idx)) {
            ChainEngine.lastMessage();
        }
    }
}

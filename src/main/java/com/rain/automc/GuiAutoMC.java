package com.rain.automc;

import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

/*
 * 主界面：项目卡片列表。
 * 每张卡片：左侧色条（自动/手动）、名称与摘要、右侧「开/关」「运行」「编辑」。
 * 复制/删除不放在卡片上，统一收进底部右侧「⋯」菜单，只对当前选中的项目生效。
 */
public class GuiAutoMC extends GuiBase {
    private int scroll = 0;
    private int sel = 0;
    private int confirmDel = -1;
    private long confirmAt = 0L;
    /* 卡片拖动排序：按下卡片先「预备」，拖动超过阈值才真正开始 */
    private int dragFrom = -1;
    private int dragArmed = -1;
    private int dragStartY = 0;
    private int mouseX = 0;
    private int mouseY = 0;

    public void initGui() {
        this.scroll = 0;
        if (this.sel >= ConfigManager.get().chains.size()) {
            this.sel = Math.max(0, ConfigManager.get().chains.size() - 1);
        }
    }

    private boolean compact() {
        return this.width < 560 || this.height < 320;
    }

    private int cardH() {
        return this.compact() ? 46 : 64;
    }

    private int gap() {
        return this.compact() ? 4 : 8;
    }

    private int headerH() {
        return this.compact() ? 44 : 60;
    }

    private int footerH() {
        return this.compact() ? 30 : 42;
    }

    private int listW() {
        return Math.min(660, this.width - (this.compact() ? 20 : 48));
    }

    private int listX() {
        return (this.width - this.listW()) / 2;
    }

    private int listTop() {
        return this.headerH() + (this.compact() ? 8 : 26);
    }

    private int listBottom() {
        return this.height - this.footerH() - (this.compact() ? 8 : 20);
    }

    private int visibleCards() {
        return Math.max(1, (this.listBottom() - this.listTop() + this.gap()) / (this.cardH() + this.gap()));
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.tickDrag();
        int prev = this.hitTest(mouseX, mouseY);
        this.hots.clear();
        this.hover = prev;
        this.backdrop();
        this.fill(0, 0, this.width, this.headerH(), 0xF00F151D);
        this.drawHeader();
        this.drawList();
        this.fill(0, this.height - this.footerH() - 2, this.width, this.footerH() + 2, 0xF00F151D);
        this.drawFooter();
    }

    private void drawHeader() {
        int y = this.compact() ? 8 : 20;
        this.text("AutoMC", 24, y, TEXT);
        int tw = this.textWidth("AutoMC");
        this.fill(24 + tw + 8, y + 2, 1, 11, 0x44FFFFFF);
        String ver = "v" + AutoMCMod.VERSION;
        int vx = 24 + tw + 15;
        this.text(ver, vx, y, accentText());
        if (!this.compact()) {
            this.text("\u9879\u76ee\u5f0f\u81ea\u52a8\u5316  \u00b7  " + Catalog.count(false) + " \u52a8\u4f5c / "
                    + Catalog.count(true) + " \u4e8b\u4ef6", 24, y + 16, SUB);
        } else {
            // 版本号宽度是会变的，计数跟着它走，不要写死偏移
            this.text(Catalog.count(false) + " \u52a8\u4f5c / " + Catalog.count(true) + " \u4e8b\u4ef6",
                    vx + this.textWidth(ver) + 10, y, DIM);
        }

        String status = Engine.get().isRunning() ? "\u8fd0\u884c\u4e2d \u00b7 " + Engine.get().status() : "\u7a7a\u95f2";
        int color = Engine.get().isRunning() ? GREEN : DIM;
        int right = this.width - 24;
        this.textRight(status, right, y, color);
        if (!this.compact()) {
            this.textRight(this.openKeyName() + " \u6253\u5f00\u754c\u9762  \u00b7  ESC \u4fdd\u5b58\u9000\u51fa", right, y + 16, DIM);
        }
        this.divider(24, this.headerH() - 4, this.width - 48);
    }

    /* 右上角提示用实际绑定键，改了「控制」里的键也能对上 */
    private String openKeyName() {
        if (AutoMCMod.keyOpenGui == null) {
            return "\u672a\u7ed1\u5b9a";
        }
        String n = org.lwjgl.input.Keyboard.getKeyName(AutoMCMod.keyOpenGui.getKeyCode());
        return n == null || n.isEmpty() || "NONE".equalsIgnoreCase(n) ? "\u672a\u7ed1\u5b9a" : n;
    }

    private void drawList() {
        List<ScriptChain> list = ConfigManager.get().chains;
        if (list.isEmpty()) {
            this.textCenter("\u8fd8\u6ca1\u6709\u9879\u76ee\uff0c\u70b9\u5de6\u4e0b\u89d2\u300c\u65b0\u5efa\u9879\u76ee\u300d", this.width / 2, this.listTop() + 20, DIM);
            return;
        }
        int x = this.listX();
        int w = this.listW();
        int vis = this.visibleCards();
        if (this.scroll > list.size() - vis) {
            this.scroll = Math.max(0, list.size() - vis);
        }
        if (this.scroll < 0) {
            this.scroll = 0;
        }
        int y = this.listTop();
        for (int i = this.scroll; i < list.size() && i < this.scroll + vis; ++i) {
            if (i == this.dragFrom) {
                this.drawDropSlot(x, y, w);
            } else {
                this.drawCard(i, list.get(i), x, y, w);
            }
            y += this.cardH() + this.gap();
        }
        if (this.dragFrom >= 0 && this.dragFrom < list.size()) {
            int to = this.cardIndexAt(this.mouseY);
            if (to >= 0) {
                int base = this.listTop() + (to - this.scroll) * (this.cardH() + this.gap());
                int lineY = (to >= this.dragFrom ? base + this.cardH() + this.gap() : base) - 1;
                this.fill(x, lineY, w, 3, this.accentFill());
                this.ghostCard(list.get(this.dragFrom), x, this.mouseY - this.cardH() / 2, w);
            }
        }
        this.scrollbar(x + w + 5, this.listTop(), this.listBottom() - this.listTop(), list.size(), vis, this.scroll);
    }

    /* 拖动时原位置留一个空槽，让人一眼看出卡片被拿起来了 */
    private void drawDropSlot(int x, int y, int w) {
        int h = this.cardH();
        this.card(x, y, w, h, 0x2A10161D, 0x44FFFFFF);
    }

    /* 跟着鼠标走的半透明卡片，拖动过程有实感 */
    private void ghostCard(ScriptChain c, int x, int y, int w) {
        int h = this.cardH();
        this.fill(x + 3, y + 5, w, h, 0x55000000);
        this.card(x, y, w, h, 0xF0222C39, this.accentAlpha(0xCC));
        this.round(x + (this.compact() ? 7 : 9), y + 8, 3, h - 16, 2, this.accentFill());
        int tw = w - (this.compact() ? 34 : 40);
        this.text(this.clip(c.name, tw), x + 20, y + (h - 8) / 2, TEXT);
    }

    /* 鼠标落在第几张卡片（不拖到底部时返回最后一张） */
    private int cardIndexAt(int my) {
        List<ScriptChain> list = ConfigManager.get().chains;
        if (list.isEmpty() || my < this.listTop() || my >= this.listBottom()) {
            return -1;
        }
        int idx = this.scroll + (my - this.listTop()) / (this.cardH() + this.gap());
        return Math.max(0, Math.min(idx, list.size() - 1));
    }

    private void drawCard(int i, ScriptChain c, int x, int y, int w) {
        int h = this.cardH();
        boolean hov = this.isHover(1000 + i) || this.dragArmed == i;
        boolean chosen = this.sel == i;
        boolean running = Engine.get().runningChain() == i;
        boolean on = c.isEnabled();
        int fill = running ? 0xFF16281F : (chosen ? PANEL_SEL : (hov ? PANEL_HI : (on ? PANEL : 0xFF11161D)));
        int border = running ? 0x8841D391 : (chosen ? this.accentAlpha(0x66) : (hov ? 0x55FFFFFF : LINE));
        this.card(x, y, w, h, fill, border);
        this.round(x + (this.compact() ? 7 : 9), y + 8, 3, h - 16, 2, !on ? DIM : (c.auto() ? GREEN : 0xFF8A94A6));

        int bw = this.compact() ? 38 : 48;
        int bh = this.compact() ? 18 : 24;
        int by = y + (h - bh) / 2;
        int right = x + w - (this.compact() ? 8 : 14);
        int swW = this.compact() ? 26 : 30;
        int runX = right - bw;
        int swX = runX - 8 - swW;
        int textW = swX - (x + 18) - 6;

        this.hot(1000 + i, x, y, Math.max(40, textW), h, null);
        int nameColor = on ? TEXT : SUB;
        int subColor = on ? SUB : DIM;
        if (this.compact()) {
            this.text(this.clip(c.name, textW), x + 18, y + 8, nameColor);
            this.text(this.clip(c.eventSummary() + " \u00b7 " + c.actionCount() + " \u52a8\u4f5c", textW), x + 18, y + 22, subColor);
            this.text(on ? "\u5df2\u5f00\u542f" : "\u5df2\u7981\u7528",
                    x + 18, y + 33, on ? DIM : AMBER);
        } else {
            this.text(this.clip(c.name, textW), x + 20, y + 12, nameColor);
            this.text(this.clip(c.eventSummary() + "  \u00b7  " + c.actionCount() + " \u4e2a\u52a8\u4f5c", textW), x + 20, y + 30, subColor);
            this.text(on ? "\u5df2\u5f00\u542f" : "\u5df2\u7981\u7528",
                    x + 20, y + 45, on ? DIM : AMBER);
        }

        this.switchBtn(swX, by + (bh - 14) / 2, swW, on, 4000 + i);
        this.miniButton(runX, by, bw, bh, running ? "\u505c\u6b62" : "\u8fd0\u884c", running ? RED : TEXT, 2000 + i);
    }

    private void switchBtn(int x, int y, int w, boolean on, int id) {
        boolean hov = this.isHover(id);
        this.round(x, y, w, 14, 7, on ? (hov ? 0xFF3FA075 : 0xFF2E7D5C) : (hov ? 0xFF3A4351 : 0xFF2A313C));
        int knob = on ? x + w - 12 : x + 2;
        this.round(knob, y + 2, 10, 10, 5, on ? 0xFFEAF7F1 : 0xFF8B96A6);
        this.hot(id, x, y, w, 14, null);
    }

    private void miniButton(int x, int y, int w, int h, String label, int color, int id) {
        boolean hov = this.isHover(id);
        this.round(x, y, w, h, 4, hov ? 0x33FFFFFF : 0x1AFFFFFF);
        this.outline(x, y, w, h, hov ? 0x55FFFFFF : 0x22FFFFFF);
        this.textCenter(label, x + w / 2, y + (h - 8) / 2, hov ? 0xFFFFFFFF : color);
        this.hot(id, x, y, w, h, null);
    }

    private void drawFooter() {
        int h = this.compact() ? 24 : 28;
        int y = this.height - h - (this.compact() ? 6 : 14);
        int w = this.listW();
        int x = this.listX();
        this.divider(24, y - (this.compact() ? 5 : 10), this.width - 48);

        int bw = this.compact() ? 64 : 84;
        boolean hov = this.isHover(9001);
        this.round(x, y, bw, h, 5, hov ? this.accentHover() : this.accentFill());
        this.outline(x, y, bw, h, 0x66FFFFFF);
        this.textCenter("\uff0b " + (this.compact() ? "\u65b0\u5efa" : "\u65b0\u5efa\u9879\u76ee"), x + bw / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        this.hot(9001, x, y, bw, h, null);

        boolean eh = this.isHover(9003);
        this.round(x + bw + 6, y, bw, h, 5, eh ? 0xFF2C3949 : 0xFF1F2833);
        this.outline(x + bw + 6, y, bw, h, 0x33FFFFFF);
        this.textCenter("\u7f16\u8f91", x + bw + 6 + bw / 2, y + (h - 8) / 2, eh ? 0xFFFFFFFF : TEXT);
        this.hot(9003, x + bw + 6, y, bw, h, null);

        int cw = this.compact() ? 74 : 88;
        int bx = x + w - cw;
        boolean h2 = this.isHover(9002);
        this.round(bx, y, cw, h, 5, h2 ? 0xFF2A3646 : 0xFF1F2833);
        this.outline(bx, y, cw, h, 0x33FFFFFF);
        this.textCenter(this.compact() ? "\u9000\u51fa" : "\u4fdd\u5b58\u5e76\u9000\u51fa", bx + cw / 2, y + (h - 8) / 2, TEXT);
        this.hot(9002, bx, y, cw, h, null);

        int mw = 26;
        int gx = bx - 6 - mw;
        boolean gh = this.isHover(9200);
        this.round(gx, y, mw, h, 5, gh ? 0xFF2A3646 : 0xFF1F2833);
        this.outline(gx, y, mw, h, 0x33FFFFFF);
        this.textCenter("⚙", gx + mw / 2, y + (h - 8) / 2, gh ? 0xFFFFFFFF : SUB);
        this.hot(9200, gx, y, mw, h, null);

        int tx = gx - 6 - mw;
        boolean th = this.isHover(9300);
        boolean armed = this.confirmDel == this.sel && System.currentTimeMillis() - this.confirmAt < 2500L;
        this.round(tx, y, mw, h, 5, th ? 0xFF2A3646 : 0xFF1F2833);
        this.outline(tx, y, mw, h, armed ? 0xAAFF6B6B : 0x33FFFFFF);
        int ix = tx + mw / 2;
        int iy = y + (h - 8) / 2 - 1;
        int ic = armed ? 0xFFFF8A8A : RED;
        this.fill(ix - 6, iy, 12, 2, ic);
        this.fill(ix - 3, iy - 2, 6, 2, ic);
        this.fill(ix - 5, iy + 3, 10, 8, ic);
        this.fill(ix - 2, iy + 5, 1, 4, 0xFF2A0F12);
        this.fill(ix + 1, iy + 5, 1, 4, 0xFF2A0F12);
        this.hot(9300, tx, y, mw, h, null);

        if (armed) {
            this.textRight("再点一次删除 " + this.selName(), this.width - 24, y - 12, AMBER);
        }
    }

    private String selName() {
        List<ScriptChain> list = ConfigManager.get().chains;
        return this.sel >= 0 && this.sel < list.size() ? list.get(this.sel).name : "";
    }

    protected void onClick(int id, int mx, int my) {
        List<ScriptChain> list = ConfigManager.get().chains;
        if (id >= 1000 && id < 2000) {
            int i = id - 1000;
            if (i >= 0 && i < list.size()) {
                this.sel = i;
                    }
            return;
        }
        if (id >= 2000 && id < 3000) {
            int i = id - 2000;
            if (i >= 0 && i < list.size()) {
                this.sel = i;
                if (Engine.get().runningChain() == i) {
                    Engine.get().stop();
                } else if (!Engine.get().run(i)) {
                    PlayerActions.chatSelf(Engine.get().lastMessage());
                }
            }
            return;
        }
        if (id == 9003) {
            if (this.sel >= 0 && this.sel < list.size()) {
                this.mc.displayGuiScreen((GuiScreen)new GuiProject(this.sel));
            }
            return;
        }
        if (id >= 4000 && id < 5000) {
            int i = id - 4000;
            if (i >= 0 && i < list.size()) {
                ScriptChain c = list.get(i);
                c.setEnabled(!c.isEnabled());
                if (!c.isEnabled() && Engine.get().runningChain() == i) {
                    Engine.get().stop();
                }
                ConfigManager.save();
            }
            return;
        }
        if (id == 9001) {
            ScriptChain c = new ScriptChain("\u9879\u76ee " + (list.size() + 1));
            int at = ConfigManager.add(c);
            if (at < 0) {
                PlayerActions.chatSelf("\u00a7c\u9879\u76ee\u6570\u8fbe\u4e0a\u9650");
                return;
            }
            this.sel = at;
            ConfigManager.save();
            this.scroll = Math.max(0, list.size() - this.visibleCards());
            this.mc.displayGuiScreen((GuiScreen)new GuiProject(at));
            return;
        }
        if (id == 9002) {
            ConfigManager.save();
            this.mc.displayGuiScreen((GuiScreen)null);
            return;
        }
        if (id == 9200) {
            this.mc.displayGuiScreen((GuiScreen)new GuiSettings());
            return;
        }
        if (id == 9300) {
            if (this.sel >= 0 && this.sel < list.size()) {
                if (this.confirmDel == this.sel && System.currentTimeMillis() - this.confirmAt < 2500L) {
                    list.remove(this.sel);
                    this.sel = Math.max(0, Math.min(this.sel, list.size() - 1));
                    this.confirmDel = -1;
                    ConfigManager.save();
                } else {
                    this.confirmDel = this.sel;
                    this.confirmAt = System.currentTimeMillis();
                }
            }
            return;
        }
    }

    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            this.scroll += dw > 0 ? -1 : 1;
            if (this.scroll < 0) {
                this.scroll = 0;
            }
        }
    }

    /* 鼠标移动超过阈值才真正进入拖动，单纯点击不会误触发 */
    private void tickDrag() {
        if (this.dragArmed >= 0 && this.dragFrom < 0
                && Math.abs(this.mouseY - this.dragStartY) >= 4) {
            this.dragFrom = this.dragArmed;
        }
    }

    /* 按下卡片先「预备」，拖动超过 4px 才开始；松手时按位移判断是否真的拖过 */
    @Override
    protected void mouseClicked(int mx, int my, int button) throws java.io.IOException {
        this.mouseX = mx;
        this.mouseY = my;
        if (button == 0) {
            int id = this.hitTest(mx, my);
            if (id >= 1000 && id < 2000) {
                this.dragArmed = id - 1000;
                this.dragFrom = -1;
                this.dragStartY = my;
            }
        }
        super.mouseClicked(mx, my, button);
    }

    @Override
    protected void mouseClickMove(int mx, int my, int clickedButton, long timeSinceLastClick) {
        this.mouseX = mx;
        this.mouseY = my;
        if (this.dragArmed >= 0 && this.dragFrom < 0 && clickedButton == 0
                && Math.abs(my - this.dragStartY) >= 4) {
            this.dragFrom = this.dragArmed;
        }
        super.mouseClickMove(mx, my, clickedButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        this.mouseX = mx;
        this.mouseY = my;
        if (button == 0) {
            boolean moved = this.dragArmed >= 0 && Math.abs(my - this.dragStartY) >= 4;
            int from = this.dragFrom >= 0 ? this.dragFrom : (moved ? this.dragArmed : -1);
            int to = from >= 0 ? this.cardIndexAt(my) : -1;
            this.dragFrom = -1;
            this.dragArmed = -1;
            List<ScriptChain> list = ConfigManager.get().chains;
            if (to >= 0 && to != from && from < list.size()) {
                ScriptChain c = list.remove(from);
                list.add(Math.min(to, list.size()), c);
                this.sel = Math.min(to, list.size() - 1);
                ConfigManager.save();
            }
        }
        super.mouseReleased(mx, my, button);
    }

    public void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
            ConfigManager.save();
            this.mc.displayGuiScreen((GuiScreen)null);
            return;
        }
        if (keyCode == 28) {
            if (this.sel >= 0 && this.sel < ConfigManager.get().chains.size()) {
                this.mc.displayGuiScreen((GuiScreen)new GuiProject(this.sel));
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}

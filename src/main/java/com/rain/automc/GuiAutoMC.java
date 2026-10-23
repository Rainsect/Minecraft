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
        this.text("v1.2.3", 24 + tw + 15, y, accentText());
        if (!this.compact()) {
            this.text("\u9879\u76ee\u5f0f\u81ea\u52a8\u5316  \u00b7  " + Catalog.count(false) + " \u52a8\u4f5c / "
                    + Catalog.count(true) + " \u4e8b\u4ef6", 24, y + 16, SUB);
        } else {
            this.text(Catalog.count(false) + " \u52a8\u4f5c / " + Catalog.count(true) + " \u4e8b\u4ef6", 24 + tw + 44, y, DIM);
        }

        String status = ChainEngine.isRunning() ? "\u8fd0\u884c\u4e2d \u00b7 " + ChainEngine.status() : "\u7a7a\u95f2";
        int color = ChainEngine.isRunning() ? GREEN : DIM;
        int right = this.width - 24;
        this.textRight(status, right, y, color);
        if (!this.compact()) {
            this.textRight("F8 \u5f00\u5173\u754c\u9762  \u00b7  ESC \u4fdd\u5b58\u9000\u51fa", right, y + 16, DIM);
        }
        this.divider(24, this.headerH() - 4, this.width - 48);
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
            this.drawCard(i, list.get(i), x, y, w);
            y += this.cardH() + this.gap();
        }
        this.scrollbar(x + w + 5, this.listTop(), this.listBottom() - this.listTop(), list.size(), vis, this.scroll);
    }

    private void drawCard(int i, ScriptChain c, int x, int y, int w) {
        int h = this.cardH();
        boolean hov = this.isHover(1000 + i);
        boolean chosen = this.sel == i;
        boolean running = ChainEngine.runningChain() == i;
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
                if (ChainEngine.runningChain() == i) {
                    ChainEngine.stop();
                } else if (!ChainEngine.run(i)) {
                    PlayerActions.chatSelf(ChainEngine.lastMessage());
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
                if (!c.isEnabled() && ChainEngine.runningChain() == i) {
                    ChainEngine.stop();
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

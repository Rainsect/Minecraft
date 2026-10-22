package com.rain.automc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

/* 积木选择器：分类页签 + 列表，点一下就把积木插进当前项目。 */
public class GuiPicker extends GuiBase {
    private final int projectIndex;
    private final int insertAfter;
    private final ScriptChain work;
    private int tab = 0;
    private int scroll = 0;
    private final List<Catalog.Def> shown = new ArrayList<Catalog.Def>();

    public GuiPicker(int projectIndex, ScriptChain work, int insertAfter) {
        this.projectIndex = projectIndex;
        this.work = work;
        this.insertAfter = insertAfter;
    }

    private int panelW() {
        return Math.min(560, this.width - 40);
    }

    private int panelH() {
        return Math.min(344, this.height - 40);
    }

    private int panelX() {
        return (this.width - this.panelW()) / 2;
    }

    private int panelY() {
        return (this.height - this.panelH()) / 2;
    }

    private int rowsVisible() {
        return Math.max(1, (this.panelH() - 70 - 24) / 26);
    }

    private void refresh() {
        this.shown.clear();
        String cat = this.tab == 0 ? null : Catalog.CATS[this.tab - 1];
        boolean onlyEvent = this.tab == 1;
        for (int i = 0; i < Catalog.ALL.size(); ++i) {
            Catalog.Def d = Catalog.ALL.get(i);
            if (cat != null && !d.cat.equals(cat)) {
                continue;
            }
            if (onlyEvent && !d.event) {
                continue;
            }
            this.shown.add(d);
        }
        if (this.scroll > this.shown.size() - this.rowsVisible()) {
            this.scroll = Math.max(0, this.shown.size() - this.rowsVisible());
        }
    }

    public void initGui() {
        this.refresh();
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int prev = this.hitTest(mouseX, mouseY);
        this.hots.clear();
        this.hover = prev;
        this.backdrop();
        this.fill(0, 0, this.width, this.height, 0x99000000);

        int x = this.panelX();
        int y = this.panelY();
        int w = this.panelW();
        int h = this.panelH();
        this.card(x, y, w, h, 0xFF111820, 0x3AFFFFFF);
        this.text("\u6dfb\u52a0\u79ef\u6728", x + 14, y + 14, TEXT);
        this.textRight("ESC \u53d6\u6d88", x + w - 14, y + 14, DIM);
        this.divider(x + 12, y + 34, w - 24);

        this.drawTabs(x, y + 42, w);
        this.drawRows(x, y + 70, w, h);
        this.textRight("\u4e8b\u4ef6\u53ef\u52fe\u591a\u4e2a\uff1a\u4efb\u4e00\u547d\u4e2d\u5373\u8fd0\u884c\u9879\u76ee", x + w - 14, y + h - 18, DIM);
    }

    private void drawTabs(int x, int y, int w) {
        int count = Catalog.CATS.length + 1;
        int tw = (w - 24) / count;
        for (int i = 0; i < count; ++i) {
            String label = i == 0 ? "\u5168\u90e8" : Catalog.CATS[i - 1];
            int tx = x + 12 + i * tw;
            boolean on = this.tab == i;
            boolean hov = this.isHover(900 + i);
            if (on) {
                this.round(tx, y, tw - 4, 20, 4, 0xFF24344A);
                this.outline(tx, y, tw - 4, 20, this.accentAlpha(0x66));
            } else if (hov) {
                this.round(tx, y, tw - 4, 20, 4, 0x1EFFFFFF);
            }
            int color = on ? TEXT : (hov ? SUB : DIM);
            if (i > 0 && i <= Catalog.CATS.length) {
                color = on ? Catalog.CAT_COLORS[i - 1] : (hov ? SUB : DIM);
            }
            this.textCenter(label, tx + (tw - 4) / 2, y + 6, color);
            this.hot(900 + i, tx, y, tw - 4, 20, null);
        }
    }

    private void drawRows(int x, int y, int w, int h) {
        int vis = this.rowsVisible();
        int listBottom = y + (this.panelH() - 70 - 24);
        int yy = y;
        for (int i = this.scroll; i < this.shown.size() && i < this.scroll + vis; ++i) {
            Catalog.Def d = this.shown.get(i);
            int id = 1000 + i;
            boolean hov = this.isHover(id);
            if (hov) {
                this.round(x + 10, yy, w - 20, 24, 4, 0xFF1C2634);
            }
            this.round(x + 16, yy + 9, 3, 8, 1, d.event ? AMBER : Catalog.colorOf(d.cat));
            this.text(d.name, x + 26, yy + 7, hov ? 0xFFFFFFFF : TEXT);
            int nameW = this.textWidth(d.name);
            this.text(this.clip(d.hint, w - nameW - 130), x + 26 + nameW + 12, yy + 7, DIM);
            this.textRight(d.event ? "\u4e8b\u4ef6" : "\u52a8\u4f5c", x + w - 16, yy + 7, d.event ? AMBER : Catalog.colorOf(d.cat));
            this.hot(id, x + 10, yy, w - 20, 24, null);
            yy += 26;
        }
        this.scrollbar(x + w - 8, y, listBottom - y, this.shown.size(), vis, this.scroll);
        if (this.shown.isEmpty()) {
            this.textCenter("\u8fd9\u4e2a\u5206\u7c7b\u4e0b\u6ca1\u4e1c\u897f", x + w / 2, y + 20, DIM);
        }
    }

    protected void onClick(int id, int mx, int my) {
        if (id >= 900 && id < 920) {
            this.tab = id - 900;
            this.scroll = 0;
            this.refresh();
            return;
        }
        if (id >= 1000 && id < 2000) {
            int i = id - 1000;
            if (i < 0 || i >= this.shown.size()) {
                return;
            }
            Catalog.Def d = this.shown.get(i);
            ScriptChain c = this.work;
            if (c == null) {
                return;
            }
            ScriptAction a = new ScriptAction(d.name);
            a.fillDefaults();
            int at;
            if (d.event) {
                at = 0;
                for (int k = 0; k < c.actions.size(); ++k) {
                    if (c.actions.get(k).isEvent()) {
                        at = k + 1;
                    }
                }
            } else {
                at = c.actions.size();
                if (this.insertAfter >= 0 && this.insertAfter < c.actions.size() && !c.actions.get(this.insertAfter).isEvent()) {
                    at = this.insertAfter + 1;
                }
            }
            c.actions.add(at, a);
            GuiProject back = new GuiProject(this.projectIndex, this.work);
            this.mc.displayGuiScreen((GuiScreen)back);
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
            this.mc.displayGuiScreen((GuiScreen)new GuiProject(this.projectIndex, this.work));
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}

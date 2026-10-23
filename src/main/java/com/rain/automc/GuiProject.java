package com.rain.automc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Mouse;

/*
 * 项目编辑器：一列积木块（事件在上、动作在下），选中块在底部面板改参数。
 * 每行右侧自带 ↑ ↓ 排序与启用开关；参数表完全由 Catalog 驱动。
 */
public class GuiProject extends GuiBase {
    private final int index;
    private int selected = -1;
    private int scroll = 0;
    private int paramRows = 1;
    private boolean awaitKey = false;
    private ScriptChain work;
    private int rowTop = 0;
    private int delayY = 0;
    private GuiTextField nameField;
    private GuiTextField delayField;
    private final List<GuiTextField> paramFields = new ArrayList<GuiTextField>();
    private final List<String> paramLabels = new ArrayList<String>();
    private final List<Integer> paramIndexes = new ArrayList<Integer>();
    private final List<int[]> paramRects = new ArrayList<int[]>();
    private final List<int[]> choiceRects = new ArrayList<int[]>();
    private final List<int[]> keyRects = new ArrayList<int[]>();
    private int awaitKeyParam = -1;
    private int mouseGuiX = 0;
    private int mouseGuiY = 0;

    public GuiProject(int index) {
        this.index = index;
        ScriptChain real = this.real();
        this.work = real == null ? null : real.copy();
    }

    /* 从选择器返回时带着同一份草稿，避免丢失未保存的修改 */
    public GuiProject(int index, ScriptChain work) {
        this.index = index;
        this.work = work;
    }

    private ScriptChain real() {
        List<ScriptChain> list = ConfigManager.get().chains;
        return this.index < 0 || this.index >= list.size() ? null : list.get(this.index);
    }

    /* ESC：直接丢弃草稿 */
    private void discard() {
        this.work = null;
    }

    /* 完成：草稿写回真实配置并落盘 */
    private void commit() {
        this.collect();
        ScriptChain real = this.real();
        if (real != null && this.work != null) {
            real.name = this.work.name;
            real.enabled = this.work.enabled;
            real.hotkey = this.work.hotkey;
            real.actions.clear();
            for (int i = 0; i < this.work.actions.size(); ++i) {
                real.actions.add(this.work.actions.get(i).copy());
            }
        }
        ConfigManager.save();
    }

    /* 编辑器内部一切读写都走草稿 */
    private ScriptChain project() {
        return this.work;
    }

    private boolean compact() {
        return this.width < 560 || this.height < 340;
    }

    private int rowH() {
        return this.compact() ? 22 : 30;
    }

    private int headerH() {
        return this.compact() ? 44 : 62;
    }

    private int panelH() {
        return this.compact() ? 74 + (this.paramRows - 1) * 38 : 108;
    }

    private int toolbarH() {
        return this.compact() ? 24 : 28;
    }

    private int toolbarY() {
        return this.height - this.toolbarH() - (this.compact() ? 6 : 16);
    }

    private int panelY() {
        return this.toolbarY() - this.panelH() - (this.compact() ? 6 : 8);
    }

    private int listTop() {
        return this.headerH() + (this.compact() ? 2 : 10);
    }

    private int listBottom() {
        return this.panelY() - 4;
    }

    private int listW() {
        return Math.min(680, this.width - (this.compact() ? 20 : 40));
    }

    private int listX() {
        return (this.width - this.listW()) / 2;
    }

    private int rowsVisible() {
        return Math.max(1, (this.listBottom() - this.listTop()) / this.rowH());
    }

    private int nameFieldX() {
        return this.compact() ? 44 : 62;
    }

    private int nameFieldY() {
        return this.compact() ? 9 : 22;
    }

    private int nameFieldW() {
        return this.compact() ? Math.max(80, this.width - 200) : 210;
    }

    public void initGui() {
        this.build();
    }

    private void build() {
        ScriptChain c = this.project();
        if (c == null) {
            return;
        }
        this.nameField = this.field(1, this.nameFieldX(), this.nameFieldY(), this.nameFieldW(), 28, c.name);
        this.delayField = null;
        this.paramFields.clear();
        this.paramLabels.clear();
        this.paramIndexes.clear();
        this.paramRects.clear();
        this.choiceRects.clear();
        this.keyRects.clear();
        this.awaitKeyParam = -1;
        this.activeField = this.nameField;
        this.paramRows = 1;
        if (this.selected >= 0 && this.selected < c.actions.size()) {
            this.buildParams(c.actions.get(this.selected));
        }
    }

    /* NPC交易：物品ID 只在「指定ID」时出现，数量只在「指定数量」时出现 */
    private boolean hiddenParam(ScriptAction a, int index) {
        if (!"NPC\u4ea4\u6613".equals(a.type)) {
            return false;
        }
        if (index == 1) {
            return !a.param(0).contains("ID");
        }
        if (index == 3) {
            return !a.param(2).contains("\u6307\u5b9a");
        }
        return false;
    }

    private void buildParams(ScriptAction a) {
        Catalog.Def d = Catalog.find(a.type);
        if (d == null) {
            this.paramRows = 1;
            return;
        }
        int avail = this.listW() - 20;
        int used = 0;
        int rows = 1;
        for (int i = 0; i < d.labels.length; ++i) {
            int w = Math.min(d.widths[i], 200);
            if (used > 0 && used + w + 12 > avail) {
                ++rows;
                used = 0;
            }
            used += w + 12;
        }
        this.paramRows = Math.max(1, rows);

        int x = this.listX() + 10;
        this.rowTop = this.panelY() + this.panelH() - 44 - (this.paramRows - 1) * 38;
        int y = this.rowTop + 13;
        int maxRight = this.listX() + this.listW() - 82;
        int maxY = this.panelY() + this.panelH() - 31;
        for (int i = 0; i < d.labels.length; ++i) {
            if (this.hiddenParam(a, i)) {
                continue;
            }
            int w = Math.min(d.widths[i], 200);
            if (x + w > maxRight && x > this.listX() + 10) {
                x = this.listX() + 10;
                y += 38;
            }
            if (y > maxY) {
                break;
            }
            String def = d.defaults[i];
            if (def != null && def.equals("!")) {
                this.keyRects.add(new int[]{i, x, y, w, 16});
                x += w + 12;
                continue;
            }
            if (def != null && def.startsWith("@")) {
                this.choiceRects.add(new int[]{i, x, y, w, 16});
                x += w + 12;
                continue;
            }
            GuiTextField f = this.field(300 + i, x, y, w, 128, a.param(i));
            this.paramFields.add(f);
            this.paramLabels.add(d.labels[i]);
            this.paramIndexes.add(Integer.valueOf(i));
            this.paramRects.add(new int[]{x, y, w});
            x += w + 12;
        }
        int dx = this.listX() + this.listW() - 66;
        this.activeField = this.paramFields.isEmpty() ? this.nameField : this.paramFields.get(0);
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ScriptChain c = this.project();
        if (c == null) {
            this.mc.displayGuiScreen((GuiScreen)new GuiAutoMC());
            return;
        }
        this.mouseGuiX = mouseX;
        this.mouseGuiY = mouseY;
        int prev = this.hitTest(mouseX, mouseY);
        this.hots.clear();
        this.hover = prev;
        this.backdrop();
        this.fill(0, 0, this.width, this.headerH(), 0xF00F151D);
        this.drawHeader(c);
        this.drawRows(c);
        this.drawPanel(c);
        this.fill(0, this.toolbarY() - 6, this.width, this.toolbarH() + 12, 0xF00F151D);
        this.drawToolbar(c);
    }

    private void drawHeader(ScriptChain c) {
        boolean hov = this.isHover(5000);
        int bh = this.compact() ? 20 : 26;
        int by = this.compact() ? 9 : 18;
        this.round(10, by, bh + 4, bh, 5, hov ? 0xFF2C3949 : 0xFF1F2833);
        this.outline(10, by, bh + 4, bh, 0x33FFFFFF);
        this.textCenter("\u2190", 10 + (bh + 4) / 2, by + (bh - 8) / 2, hov ? 0xFFFFFFFF : SUB);
        this.hot(5000, 10, by, bh + 4, bh, null);

        this.drawField(this.nameField, this.nameFieldX(), this.nameFieldY(), this.nameFieldW(),
                this.activeField == this.nameField);
        String info = "\u4e8b\u4ef6 " + c.eventCount() + " \u00b7 \u52a8\u4f5c " + c.actionCount();
        this.text(info, this.nameFieldX(), this.nameFieldY() + (this.compact() ? 23 : 24), DIM);

        int hkW = this.compact() ? 52 : 64;
        int hkX = this.width - 12 - (this.compact() ? 50 : 58) - 8 - hkW;
        int hkH = this.compact() ? 18 : 20;
        int hkY = this.compact() ? 10 : 22;
        boolean hkHover = this.isHover(5110);
        this.round(hkX, hkY, hkW, hkH, 5, this.awaitKey ? this.accentAlpha(0x88) : (hkHover ? 0xFF2C3949 : 0xFF1F2833));
        this.outline(hkX, hkY, hkW, hkH, 0x33FFFFFF);
        String hkLabel = this.awaitKey ? "按任意键" : (c.hotkey == null || c.hotkey.isEmpty() ? "绑定按键" : "按键 " + c.hotkey);
        this.textCenter(this.clip(hkLabel, hkW - 6), hkX + hkW / 2, hkY + (hkH - 8) / 2, hkHover || this.awaitKey ? 0xFFFFFFFF : SUB);
        this.hot(5110, hkX, hkY, hkW, hkH, null);

        int toggleW = this.compact() ? 50 : 58;
        int tx0 = this.width - 12 - toggleW;
        int tbH = this.compact() ? 18 : 20;
        int tbY = this.compact() ? 10 : 22;
        boolean eh = this.isHover(5100);
        this.round(tx0, tbY, toggleW, tbH, 5, c.isEnabled() ? (eh ? 0xFF33A472 : 0xFF245C43) : (eh ? 0xFF6A4A2A : 0xFF3E2E20));
        this.outline(tx0, tbY, toggleW, tbH, 0x33FFFFFF);
        this.textCenter(c.isEnabled() ? "\u5df2\u5f00\u542f" : "\u5df2\u7981\u7528", tx0 + toggleW / 2, tbY + (tbH - 8) / 2,
                c.isEnabled() ? 0xFFB9F5D6 : 0xFFF3C98B);
        this.hot(5100, tx0, tbY, toggleW, tbH, null);

        if (ChainEngine.runningChain() == this.index) {
            this.textRight("\u8fd0\u884c\u4e2d " + ChainEngine.status(), tx0 - 8, this.compact() ? 13 : 26, GREEN);
        }
        this.divider(10, this.headerH() - 4, this.width - 20);
    }

    private void drawRows(ScriptChain c) {
        int x = this.listX();
        int w = this.listW();
        int rh = this.rowH();
        int vis = this.rowsVisible();
        if (this.scroll > c.actions.size() - vis) {
            this.scroll = Math.max(0, c.actions.size() - vis);
        }
        if (this.scroll < 0) {
            this.scroll = 0;
        }
        if (c.actions.isEmpty()) {
            this.textCenter("\u7a7a\u9879\u76ee\uff1a\u70b9\u4e0b\u65b9\u300c\uff0b \u6dfb\u52a0\u300d\uff0c\u5148\u9009\u4e8b\u4ef6\u518d\u52a0\u52a8\u4f5c",
                    this.width / 2, this.listTop() + 14, DIM);
            return;
        }
        int y = this.listTop();
        for (int i = this.scroll; i < c.actions.size() && i < this.scroll + vis; ++i) {
            this.drawRow(i, c.actions.get(i), x, y, w, rh, c.actions.size());
            y += rh;
        }
        this.scrollbar(x + w + 4, this.listTop(), this.listBottom() - this.listTop(), c.actions.size(), vis, this.scroll);
    }

    private void drawRow(int i, ScriptAction a, int x, int y, int w, int rh, int total) {
        boolean sel = i == this.selected;
        boolean hov = this.isHover(5200 + i);
        this.round(x, y + 1, w, rh - 2, 4, sel ? PANEL_SEL : (hov ? PANEL_HI : PANEL));
        if (sel) {
            this.outline(x, y + 1, w, rh - 2, this.accentAlpha(0x66));
        }
        this.round(x + 5, y + 4, 3, rh - 8, 1, a.isEvent() ? AMBER : Catalog.colorOf(a.cat()));

        int tw = this.compact() ? 22 : 26;
        int aw = this.compact() ? 13 : 15;
        int ty2 = y + (rh - 14) / 2;
        int toggleX = x + w - tw - 6;
        int downX = toggleX - 4 - aw;
        int upX = downX - 3 - aw;
        this.hot(5200 + i, x, y, Math.max(60, upX - x - 6), rh - 2, null);

        int ty = y + (rh - 8) / 2;
        this.text(String.valueOf(i + 1), x + 11, ty, a.enabled ? DIM : 0x44FFFFFF);
        this.text(this.clip(a.type, this.compact() ? 68 : 112), x + 26, ty, a.enabled ? TEXT : DIM);
        int sumX;
        if (a.isEvent()) {
            this.chip("\u4e8b\u4ef6", x + (this.compact() ? 98 : 148), ty - 3, AMBER);
            sumX = x + (this.compact() ? 136 : 186);
        } else {
            sumX = x + (this.compact() ? 98 : 148);
        }
        this.text(this.clip(a.summary(), Math.max(10, upX - sumX - 6)), sumX, ty, a.enabled ? SUB : DIM);

        this.arrow(upX, ty2, aw, i > 0, true, 5400 + i);
        this.arrow(downX, ty2, aw, i < total - 1, false, 5500 + i);

        boolean eh = this.isHover(5300 + i);
        this.round(toggleX, ty2, tw, 14, 3, eh ? 0x44FFFFFF : 0x22FFFFFF);
        this.textCenter(a.enabled ? "\u5f00" : "\u5173", toggleX + tw / 2, ty2 + 3, a.enabled ? GREEN : DIM);
        this.hot(5300 + i, toggleX, ty2, tw, 14, null);
    }

    /* 箭头默认无底色，只有启用且鼠标悬停时才给背景 */
    private void arrow(int x, int y, int w, boolean active, boolean up, int id) {
        boolean hov = active && this.isHover(id);
        if (hov) {
            this.round(x, y, w, 14, 3, this.accentAlpha(0x66));
        }
        this.textCenter(up ? "↑" : "↓", x + w / 2, y + 3,
                active ? (hov ? 0xFFFFFFFF : SUB) : 0x2AFFFFFF);
        if (active) {
            this.hot(id, x, y, w, 14, null);
        }
    }

    private void drawPanel(ScriptChain c) {
        int x = this.listX();
        int w = this.listW();
        int py = this.panelY();
        this.round(x, py, w, this.panelH(), 6, 0xFF10161E);
        this.outline(x, py, w, this.panelH(), 0x22FFFFFF);

        ScriptAction a = this.selected >= 0 && this.selected < c.actions.size() ? c.actions.get(this.selected) : null;
        if (a == null) {
            this.text("未选中动作", x + 10, py + 8, SUB);
            return;
        }
        Catalog.Def d = Catalog.find(a.type);
        String cat = d == null ? "\u7cfb\u7edf" : d.cat;
        int cy = py + (this.compact() ? 4 : 10);
        this.chip(cat, x + 10, cy, a.isEvent() ? AMBER : Catalog.colorOf(cat));
        int tx = x + 14 + this.textWidth(cat) + 8;
        this.text(a.type, tx, cy + 3, TEXT);
        if ("点击坐标".equals(a.type)) {
            int pbw = 74;
            int pbx = x + w - pbw - 10;
            boolean phov = this.isHover(5900);
            this.round(pbx, cy - 1, pbw, 18, 4, phov ? this.accentAlpha(0x55) : 0xFF1A222E);
            this.outline(pbx, cy - 1, pbw, 18, phov ? this.accentAlpha(0x99) : 0x22FFFFFF);
            this.textCenter("⌖ 拾取坐标", pbx + pbw / 2, cy + 5,
                    phov ? 0xFFFFFFFF : SUB);
            this.hot(5900, pbx, cy - 1, pbw, 18, null);
        }
        if (!this.compact() && d != null && !d.hint.isEmpty()) {
            this.text(this.clip(d.hint, w - (tx - x) - 190), tx + this.textWidth(a.type) + 10, cy + 3, DIM);
        }

        for (int i = 0; i < this.paramFields.size() && i < this.paramRects.size(); ++i) {
            GuiTextField f = this.paramFields.get(i);
            int[] r = this.paramRects.get(i);
            this.text(this.paramLabels.get(i), r[0], r[1] - 14, SUB);
            this.drawField(f, r[0], r[1], r[2], this.activeField == f);
        }
        for (int i = 0; i < this.keyRects.size(); ++i) {
            int[] r = this.keyRects.get(i);
            int pid = 8200 + r[0];
            boolean hov = this.isHover(pid);
            boolean waiting = this.awaitKeyParam == r[0];
            String key = a.param(r[0]);
            this.text("\u6309\u952e", r[1], r[2] - 14, SUB);
            this.round(r[1], r[2] - 1, r[3], 22, 4,
                    waiting ? this.accentAlpha(0x88) : (hov ? 0xFF2C3949 : 0xFF1A222E));
            this.outline(r[1], r[2] - 1, r[3], 22, hov || waiting ? this.accentAlpha(0x99) : 0x22FFFFFF);
            String label = waiting ? "\u6309\u4e0b\u4efb\u610f\u952e" : (key == null || key.isEmpty() ? "\u7ed1\u5b9a\u6309\u952e" : key);
            this.textCenter(this.clip(label, r[3] - 10), r[1] + r[3] / 2, r[2] + 7, 0xFFFFFFFF);
            this.hot(pid, r[1], r[2], r[3], r[4], null);
        }
        for (int i = 0; i < this.choiceRects.size(); ++i) {
            int[] r = this.choiceRects.get(i);
            int pid = 8000 + r[0];
            boolean hov = this.isHover(pid);
            this.round(r[1], r[2], r[3], r[4], 4, hov ? this.accentAlpha(0x55) : 0xFF1A222E);
            this.outline(r[1], r[2], r[3], r[4], hov ? this.accentAlpha(0x88) : 0x22FFFFFF);
            this.text(d == null ? "" : d.labels[r[0]], r[1], r[2] - 16, SUB);
            this.textCenter(this.clip(a.param(r[0]), r[3] - 10), r[1] + r[3] / 2, r[2] + 4, TEXT);
            this.textRight("\u25be", r[1] + r[3] - 2, r[2] + 4, DIM);
            this.hot(pid, r[1], r[2], r[3], r[4], null);
        }
        if (this.paramFields.isEmpty() && this.choiceRects.isEmpty()) {
            this.text("\u65e0\u53c2\u6570", x + 10, py + (this.compact() ? 44 : 60), DIM);
        }
    }

    private void drawToolbar(ScriptChain c) {
        int y = this.toolbarY();
        int h = this.toolbarH();
        int x = this.listX();
        this.divider(10, y - 4, this.width - 20);

        int bw = this.compact() ? 70 : 104;
        this.button(x, y, bw, h, "＋ " + (this.compact() ? "添加" : "添加动作"), accent(), 6100, true);
        int cx = x + bw + 4;
        int sw = this.compact() ? 44 : 56;
        this.button(cx, y, sw, h, "删除", RED, 6104, false);

        int dw = this.compact() ? 56 : 84;
        this.button(x + this.listW() - dw, y, dw, h, "\u5b8c\u6210", TEXT, 6106, false);
    }

    private void button(int x, int y, int w, int h, String label, int color, int id, boolean primary) {
        boolean hov = this.isHover(id);
        int fill = primary ? (hov ? this.accentHover() : this.accentFill()) : (hov ? 0xFF2C3949 : 0xFF1F2833);
        this.round(x, y, w, h, 5, fill);
        this.outline(x, y, w, h, primary ? 0x66FFFFFF : 0x33FFFFFF);
        this.textCenter(label, x + w / 2, y + (h - 8) / 2, primary ? 0xFFFFFFFF : color);
        this.hot(id, x, y, w, h, null);
    }

    /* ---------------- 交互 ---------------- */

    private void collect() {
        ScriptChain c = this.project();
        if (c == null) {
            return;
        }
        if (this.nameField != null) {
            String n = this.nameField.getText().trim();
            if (!n.isEmpty()) {
                c.name = n.length() > 28 ? n.substring(0, 28) : n;
            }
        }
        if (this.selected < 0 || this.selected >= c.actions.size()) {
            return;
        }
        ScriptAction a = c.actions.get(this.selected);
        for (int i = 0; i < this.paramFields.size(); ++i) {
            a.setParam(this.paramIndexes.get(i).intValue(), this.paramFields.get(i).getText());
        }
    }

    protected void onClick(int id, int mx, int my) {
        ScriptChain c = this.project();
        if (c == null) {
            return;
        }
        if (id == 5000 || id == 6106) {
            this.commit();
            this.mc.displayGuiScreen((GuiScreen)new GuiAutoMC());
            return;
        }
        if (id == 5110) {
            this.awaitKey = !this.awaitKey;
            return;
        }
        if (id == 5900) {
            this.collect();
            if (this.selected >= 0 && this.selected < c.actions.size()
                    && "点击坐标".equals(c.actions.get(this.selected).type)) {
                this.commit();
                ClickPicker.begin(this.index, this.selected);
                this.mc.displayGuiScreen((GuiScreen)null);
            }
            return;
        }
        if (id == 5100) {
            c.setEnabled(!c.isEnabled());
            if (!c.isEnabled() && ChainEngine.runningChain() == this.index) {
                ChainEngine.stop();
            }
            this.build();
            return;
        }
        if (id >= 5200 && id < 5300) {
            this.collect();
            this.selected = id - 5200;
            this.build();
            return;
        }
        if (id >= 5300 && id < 5400) {
            int i = id - 5300;
            if (i >= 0 && i < c.actions.size()) {
                c.actions.get(i).enabled = !c.actions.get(i).enabled;
            }
            return;
        }
        if (id >= 5400 && id < 5500) {
            this.collect();
            int i = id - 5400;
            if (i > 0 && i < c.actions.size()) {
                ScriptAction tmp = c.actions.get(i);
                c.actions.set(i, c.actions.get(i - 1));
                c.actions.set(i - 1, tmp);
                if (this.selected == i) {
                    this.selected = i - 1;
                } else if (this.selected == i - 1) {
                    this.selected = i;
                }
                this.build();
            }
            return;
        }
        if (id >= 5500 && id < 5600) {
            this.collect();
            int i = id - 5500;
            if (i >= 0 && i < c.actions.size() - 1) {
                ScriptAction tmp = c.actions.get(i);
                c.actions.set(i, c.actions.get(i + 1));
                c.actions.set(i + 1, tmp);
                if (this.selected == i) {
                    this.selected = i + 1;
                } else if (this.selected == i + 1) {
                    this.selected = i;
                }
                this.build();
            }
            return;
        }
        if (id >= 8200 && id < 8300) {
            this.awaitKeyParam = id - 8200;
            return;
        }
        if (id >= 8000 && id < 8100) {
            this.collect();
            if (this.selected >= 0 && this.selected < c.actions.size()) {
                ScriptAction a = c.actions.get(this.selected);
                Catalog.Def d = Catalog.find(a.type);
                int pi = id - 8000;
                if (d != null && pi < d.defaults.length && d.defaults[pi].startsWith("@")) {
                    String[] opts = d.defaults[pi].substring(1).split(",");
                    int cur = 0;
                    for (int k = 0; k < opts.length; ++k) {
                        if (opts[k].equals(a.param(pi))) {
                            cur = k;
                            break;
                        }
                    }
                    a.setParam(pi, opts[(cur + 1) % opts.length]);
                }
            }
            this.build();
            return;
        }
        if (id == 6100) {
            this.collect();
            this.mc.displayGuiScreen((GuiScreen)new GuiPicker(this.index, this.work, this.selected));
            return;
        }
        if (id == 6103) {
            this.collect();
            int sel = this.selected;
            if (sel >= 0 && sel < c.actions.size()) {
                c.actions.add(sel + 1, c.actions.get(sel).copy());
                this.selected = sel + 1;
                this.build();
            }
            return;
        }
        if (id == 6104) {
            this.collect();
            int sel = this.selected;
            if (sel >= 0 && sel < c.actions.size()) {
                c.actions.remove(sel);
                this.selected = Math.min(sel, c.actions.size() - 1);
                this.build();
            }
            return;
        }
        if (id == 6105) {
            this.collect();
            if (ChainEngine.runningChain() == this.index) {
                ChainEngine.stop();
            } else if (!ChainEngine.run(this.index)) {
                PlayerActions.chatSelf(ChainEngine.lastMessage());
            }
        }
    }

    protected void onClickRight(int id, int mx, int my) {
        if (id == 5110) {
            ScriptChain c = this.project();
            if (c != null) {
                c.hotkey = "";
            }
            this.awaitKey = false;
            return;
        }
        super.onClickRight(id, mx, my);
    }

    protected void onClickEmpty(int mx, int my, int button) {
        for (int i = 0; i < this.paramFields.size(); ++i) {
            GuiTextField f = this.paramFields.get(i);
            if (f.mouseClicked(mx, my, button)) {
                this.activeField = f;
                return;
            }
        }
        if (this.nameField != null && this.nameField.mouseClicked(mx, my, button)) {
            this.activeField = this.nameField;
            return;
        }
        this.activeField = null;
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
        if (this.awaitKeyParam >= 0) {
            int code = keyCode > 1 ? keyCode : org.lwjgl.input.Keyboard.getEventKey();
            if (code > 1) {
                ScriptChain pc = this.project();
                if (pc != null && this.selected >= 0 && this.selected < pc.actions.size()) {
                    String name = org.lwjgl.input.Keyboard.getKeyName(code);
                    pc.actions.get(this.selected).setParam(this.awaitKeyParam, "NONE".equalsIgnoreCase(name) ? "" : name);
                }
                this.awaitKeyParam = -1;
            } else if (keyCode == 1) {
                this.awaitKeyParam = -1;
            }
            return;
        }
        if (this.awaitKey) {
            int code = keyCode > 1 ? keyCode : org.lwjgl.input.Keyboard.getEventKey();
            if (code > 1) {
                ScriptChain pc = this.project();
                if (pc != null) {
                    String name = org.lwjgl.input.Keyboard.getKeyName(code);
                    pc.hotkey = "NONE".equalsIgnoreCase(name) ? "" : name;
                }
                this.awaitKey = false;
            } else if (keyCode == 1) {
                this.awaitKey = false;
            }
            return;
        }
        if (keyCode == 1) {
            this.discard();
            this.mc.displayGuiScreen((GuiScreen)new GuiAutoMC());
            return;
        }
        if (keyCode == 15) {
            this.collect();
            this.cycleFocus();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void cycleFocus() {
        List<GuiTextField> all = new ArrayList<GuiTextField>();
        all.add(this.nameField);
        all.addAll(this.paramFields);
        int idx = all.indexOf(this.activeField);
        this.activeField = all.get((idx + 1) % all.size());
    }
}

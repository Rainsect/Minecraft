package com.rain.automc;

import net.minecraft.client.gui.GuiScreen;

/* 设置：界面透明度 + 强调色。改完立刻生效，退出时写盘。 */
public class GuiSettings extends GuiBase {
    private boolean dragging = false;

    private int panelW() {
        return Math.min(400, this.width - 40);
    }

    private int panelH() {
        return Math.min(226, this.height - 24);
    }

    private int panelX() {
        return (this.width - this.panelW()) / 2;
    }

    private int panelY() {
        return (this.height - this.panelH()) / 2;
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int prev = this.hitTest(mouseX, mouseY);
        this.hots.clear();
        this.hover = prev;
        this.backdrop();

        int x = this.panelX();
        int y = this.panelY();
        int w = this.panelW();
        this.card(x, y, w, panelH(), PANEL, 0x44FFFFFF);
        this.text("\u754c\u9762\u8bbe\u7f6e", x + 14, y + 12, TEXT);
        this.textRight("\u6539\u5b8c\u7acb\u5373\u751f\u6548", x + w - 14, y + 12, DIM);
        this.divider(x + 12, y + 32, w - 24);

        /* 透明度 */
        int sy = y + 52;
        this.text("\u754c\u9762\u4e0d\u900f\u660e\u5ea6", x + 14, sy - 12, SUB);
        this.textRight(ConfigManager.get().uiOpacity >= 100 ? "100% 纯色" : ConfigManager.get().uiOpacity + "%", x + w - 14, sy - 12, TEXT);
        int tx = x + 14;
        int tw = w - 28;
        this.round(tx, sy, tw, 6, 3, 0x33000000);
        int fillW = tw * (ConfigManager.get().uiOpacity - 35) / 65;
        this.round(tx, sy, Math.max(6, fillW), 6, 3, this.accentFill());
        int kx = tx + Math.max(0, Math.min(tw - 8, fillW - 4));
        this.round(kx, sy - 5, 8, 16, 4, 0xFFEFF5FF);
        this.hot(9500, tx, sy - 8, tw, 22, null);

        /* 强调色 */
        int cy = y + 88;
        this.text("\u5f3a\u8c03\u8272", x + 14, cy - 12, SUB);
        for (int i = 0; i < ConfigManager.ACCENTS.length; ++i) {
            int sw = x + 14 + i * 32;
            boolean on = ConfigManager.get().uiAccent == i;
            boolean hov = this.isHover(9510 + i);
            this.round(sw, cy, 24, 24, 5, ConfigManager.ACCENTS[i]);
            if (on) {
                this.outline(sw - 2, cy - 2, 28, 28, 0xFFFFFFFF);
            } else if (hov) {
                this.outline(sw - 1, cy - 1, 26, 26, 0x88FFFFFF);
            }
            this.textCenter(ConfigManager.ACCENT_NAMES[i], sw + 12, cy + 27, on ? TEXT : DIM);
            this.hot(9510 + i, sw, cy, 24, 24, null);
        }

        /* 预览 */
        int py = cy + 52;
        this.text("\u9884\u89c8", x + 14, py + 4, DIM);
        this.card(x + 46, py, w - 60, 32, PANEL_HI, 0x33FFFFFF);
        this.round(x + 52, py + 5, 3, 22, 1, this.accentFill());
        this.text("\u9879\u76ee\u5361\u7247", x + 62, py + 4, TEXT);
        this.text("\u4e00\u5c42\u534a\u900f\u660e\u9762\u677f", x + 62, py + 16, SUB);

        /* 底部按钮 */
        int by = y + panelH() - 34;
        this.button(x + 14, by, 96, 24, "\u6062\u590d\u9ed8\u8ba4", SUB, 9520, false);
        int bw = 84;
        this.button(x + w - 14 - bw, by, bw, 24, "\u5b8c\u6210", TEXT, 9521, true);
    }

    private void button(int x, int y, int w, int h, String label, int color, int id, boolean primary) {
        boolean hov = this.isHover(id);
        int fill = primary ? (hov ? this.accentHover() : this.accentFill()) : (hov ? 0xFF2C3949 : 0xFF1F2833);
        this.round(x, y, w, h, 5, fill);
        this.outline(x, y, w, h, primary ? 0x66FFFFFF : 0x33FFFFFF);
        this.textCenter(label, x + w / 2, y + (h - 8) / 2, primary ? 0xFFFFFFFF : color);
        this.hot(id, x, y, w, h, null);
    }

    private void setFromMouse(int mx) {
        int tx = this.panelX() + 14;
        int tw = this.panelW() - 28;
        int v = 35 + (int)Math.round((mx - tx) * 65.0 / (double)Math.max(1, tw));
        if (v < 35) {
            v = 35;
        }
        if (v > 100) {
            v = 100;
        }
        ConfigManager.get().uiOpacity = v;
    }

    protected void onClick(int id, int mx, int my) {
        if (id == 9500) {
            this.dragging = true;
            this.setFromMouse(mx);
            ConfigManager.save();
            return;
        }
        if (id >= 9510 && id < 9510 + ConfigManager.ACCENTS.length) {
            ConfigManager.get().uiAccent = id - 9510;
            ConfigManager.save();
            return;
        }
        if (id == 9520) {
            ConfigManager.get().uiOpacity = 88;
            ConfigManager.get().uiAccent = 0;
            ConfigManager.save();
            return;
        }
        if (id == 9521) {
            ConfigManager.save();
            this.mc.displayGuiScreen((GuiScreen)new GuiAutoMC());
        }
    }

    public void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        if (this.dragging && mouseX > 0 && mouseY > 0) {
            this.setFromMouse(mouseX);
        }
    }

    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (this.hitTest(mouseX, mouseY) == -1) {
            this.dragging = false;
        }
    }

    protected void onEscape() {
        ConfigManager.save();
        this.mc.displayGuiScreen((GuiScreen)new GuiAutoMC());
    }

    public void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
            this.onEscape();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}

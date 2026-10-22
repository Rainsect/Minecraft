package com.rain.automc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/*
 * 界面基类：一套轻量的自绘控件（卡片、色条、圆角面板、悬停热区、滚动条），
 * 不用原版 GuiButton，方便统一深色风格与动效。
 */
public abstract class GuiBase extends GuiScreen {
    protected static final int BG0 = 0xB40A0E14;
    protected static final int BG1 = 0xA20E141C;
    protected static final int PANEL = 0xE0141B24;
    protected static final int PANEL_HI = 0xEA1B2431;
    protected static final int PANEL_SEL = 0xF01F2C3D;
    protected static final int LINE = 0x2EFFFFFF;
    protected static final int TEXT = 0xFFEAF0F8;
    protected static final int SUB = 0xFF93A1B5;
    protected static final int DIM = 0xFF5F6E84;
    protected static final int ACCENT = 0xFF5B9CFF;
    protected static final int GREEN = 0xFF41D391;
    protected static final int RED = 0xFFFF6B6B;
    protected static final int AMBER = 0xFFF3B544;

    protected final List<Hot> hots = new ArrayList<Hot>();
    protected int hover = -1;
    protected GuiTextField activeField = null;
    private int repeatKey = -1;
    private int repeatTicks = 0;

    protected static class Hot {
        public final int id;
        public final int x;
        public final int y;
        public final int w;
        public final int h;
        public final String tip;

        Hot(int id, int x, int y, int w, int h, String tip) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.tip = tip;
        }

        public boolean in(int mx, int my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    protected void hot(int id, int x, int y, int w, int h, String tip) {
        this.hots.add(new Hot(id, x, y, w, h, tip));
    }

    protected int hitTest(int mx, int my) {
        for (int i = this.hots.size() - 1; i >= 0; --i) {
            Hot h = this.hots.get(i);
            if (h.in(mx, my)) {
                return h.id;
            }
        }
        return -1;
    }

    protected boolean isHover(int id) {
        return this.hover == id;
    }

    /* ---------------- 绘制原语 ---------------- */

    /* 统一处理界面透明度与主题色：不透明深色表面按设置调 alpha 并向强调色偏色 */
    private int modulate(int color) {
        int a = color >>> 24;
        if (a < 0xE0) {
            return color;
        }
        int rgb = color & 0xFFFFFF;
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        if (lum < 96) {
            rgb = this.mix(rgb, this.accent() & 0xFFFFFF, 0.30);
        }
        int op = ConfigManager.get().opacity255();
        return (op << 24) | rgb;
    }

    private int mix(int base, int tint, double t) {
        int r = (int)(((base >> 16) & 255) * (1.0 - t) + ((tint >> 16) & 255) * t);
        int g = (int)(((base >> 8) & 255) * (1.0 - t) + ((tint >> 8) & 255) * t);
        int b = (int)((base & 255) * (1.0 - t) + (tint & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    protected int accent() {
        return ConfigManager.get().accentColor();
    }

    /* 太深的主题色（黑）做填充会看不见，提亮一点 */
    protected int accentFill() {
        int c = this.accent() & 0xFFFFFF;
        int r = (c >> 16) & 255;
        int g = (c >> 8) & 255;
        int b = c & 255;
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        return lum < 62 ? this.mix(c, 0xFFFFFF, 0.20) : c;
    }

    protected int accentHover() {
        return this.mix(this.accentFill(), 0xFFFFFF, 0.22);
    }

    protected int accentAlpha(int alpha) {
        return (alpha << 24) | (this.accentFill() & 0xFFFFFF);
    }

    /* 强调色用作文字时，深色（黑/灰）要提亮，否则看不清 */
    protected int accentText() {
        int c = this.accent();
        int r = (c >> 16) & 255;
        int g = (c >> 8) & 255;
        int b = c & 255;
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        return lum < 80 ? 0xFFC9D3E0 : c;
    }

    /* 用渐变接口画纯色，好处是会自动开关混合，半透明才真的透出后面的世界 */
    protected void fill(int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int c = this.modulate(color);
        this.drawGradientRect(x, y, x + w, y + h, c, c);
    }

    protected void round(int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int rr = Math.max(0, Math.min(r, Math.min(w / 2, h / 2)));
        if (rr == 0) {
            this.fill(x, y, w, h, color);
            return;
        }
        for (int i = 0; i < rr; ++i) {
            this.fill(x + rr - i, y + i, w - 2 * (rr - i), 1, color);
            this.fill(x + rr - i, y + h - 1 - i, w - 2 * (rr - i), 1, color);
        }
        this.fill(x, y + rr, w, h - 2 * rr, color);
    }

    protected void outline(int x, int y, int w, int h, int color) {
        this.fill(x, y, w, 1, color);
        this.fill(x, y + h - 1, w, 1, color);
        this.fill(x, y, 1, h, color);
        this.fill(x + w - 1, y, 1, h, color);
    }

    protected void card(int x, int y, int w, int h, int fillColor, int borderColor) {
        this.round(x + 1, y + 2, w, h, 5, 0x40000000);
        this.round(x, y, w, h, 5, fillColor);
        this.outline(x, y, w, h, borderColor);
    }

    protected void text(String s, int x, int y, int color) {
        if (s == null || s.isEmpty()) {
            return;
        }
        this.fontRenderer.drawStringWithShadow(s, (float)x, (float)y, color);
    }

    protected void textRight(String s, int right, int y, int color) {
        if (s == null || s.isEmpty()) {
            return;
        }
        this.text(s, right - this.fontRenderer.getStringWidth(s), y, color);
    }

    protected void textCenter(String s, int cx, int y, int color) {
        if (s == null || s.isEmpty()) {
            return;
        }
        this.text(s, cx - this.fontRenderer.getStringWidth(s) / 2, y, color);
    }

    protected int textWidth(String s) {
        return this.fontRenderer.getStringWidth(s == null ? "" : s);
    }

    /* 超宽文本裁剪成省略号 */
    protected String clip(String s, int width) {
        if (s == null) {
            return "";
        }
        if (this.textWidth(s) <= width) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            if (this.textWidth(sb.toString() + s.charAt(i) + "\u2026") > width) {
                break;
            }
            sb.append(s.charAt(i));
        }
        return sb.append("\u2026").toString();
    }

    protected void divider(int x, int y, int w) {
        this.fill(x, y, w, 1, 0x22FFFFFF);
    }

    protected void scrollbar(int x, int y, int h, int total, int visible, int offset) {
        if (total <= visible || visible <= 0) {
            return;
        }
        this.fill(x, y, 3, h, 0x1AFFFFFF);
        int barH = Math.max(18, h * visible / total);
        int maxOff = Math.max(1, total - visible);
        int barY = y + (h - barH) * Math.min(offset, maxOff) / maxOff;
        this.fill(x, barY, 3, barH, 0x88FFFFFF);
    }

    protected void chip(String label, int x, int y, int color) {
        int w = this.textWidth(label) + 10;
        this.round(x, y, w, 13, 3, (color & 0x00FFFFFF) | 0x33000000);
        this.text(label, x + 5, y + 3, color);
    }

    /*
     * 全屏底色：只负责压暗游戏画面，不参与主题变色。
     * 不透明度拉满时是纯色，看不到游戏画面。
     */
    protected void backdrop() {
        int op = ConfigManager.get().opacity255();
        if (op >= 250) {
            this.drawRect(0, 0, this.width, this.height, 0xFF0B0F15);
        } else {
            this.drawGradientRect(0, 0, this.width, this.height,
                    (op << 24) | 0x0B0F15, (Math.max(0, op - 24) << 24) | 0x090C11);
        }
    }

    /*
     * 关闭原版底框后 GuiTextField 不再自动居中文字（文本贴在 y 上），
     * 所以这里手动 +4/+6 偏移，让文字落在自绘框的垂直中心。
     */
    protected GuiTextField field(int id, int x, int y, int w, int maxLen, String value) {
        GuiTextField f = new GuiTextField(id, this.fontRenderer, x + 4, y + 6, Math.max(20, w - 8), 16);
        f.setMaxStringLength(maxLen);
        f.setText(value == null ? "" : value);
        f.setEnableBackgroundDrawing(false);
        return f;
    }

    protected void drawField(GuiTextField f, int x, int y, int w, boolean focused) {
        this.round(x - 3, y - 1, w + 6, 22, 4, focused ? 0xFF233247 : 0xFF1A222E);
        this.outline(x - 3, y - 1, w + 6, 22, focused ? this.accentAlpha(0x99) : 0x22FFFFFF);
        f.drawTextBox();
    }

    protected GuiTextField firstField(List<GuiTextField> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    /* ---------------- 输入 ---------------- */

    public void updateScreen() {
        if (this.activeField != null) {
            this.activeField.updateCursorCounter();
        }
        int k = Keyboard.isKeyDown(14) ? 14 : (Keyboard.isKeyDown(211) ? 211 : -1);
        if (k != this.repeatKey) {
            this.repeatKey = k;
            this.repeatTicks = 0;
        } else if (k != -1 && this.activeField != null) {
            ++this.repeatTicks;
            if (this.repeatTicks >= 6 && (this.repeatTicks - 6) % 2 == 0) {
                this.activeField.textboxKeyTyped((char)0, k);
            }
        }
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
            this.onEscape();
            return;
        }
        if (this.activeField != null) {
            this.activeField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    protected void cycleFocus() {
    }

    protected void onEscape() {
        this.mc.displayGuiScreen((GuiScreen)null);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        int id = this.hitTest(mouseX, mouseY);
        if (button == 0 && id != -1) {
            this.onClick(id, mouseX, mouseY);
            return;
        }
        if (button == 1 && id != -1) {
            this.onClickRight(id, mouseX, mouseY);
            return;
        }
        this.onClickEmpty(mouseX, mouseY, button);
    }

    protected void onClickEmpty(int mx, int my, int button) {
    }

    protected abstract void onClick(int id, int mx, int my);

    protected void onClickRight(int id, int mx, int my) {
    }

    public abstract void drawScreen(int var1, int var2, float var3);
}

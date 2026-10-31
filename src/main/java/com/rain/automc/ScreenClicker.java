package com.rain.automc;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 「点击坐标」的实现：全部走游戏内部，不发系统鼠标事件。
 *
 * <p>坐标 = 窗口像素，左上角为 (0,0)，不乘 GUI 缩放（换算成界面坐标时按当前窗口尺寸做比例）。
 *
 * <p>三条路径：
 * <ol>
 *     <li>世界内（没有打开界面）：直接走原版左/右键逻辑；</li>
 *     <li>容器界面落在槽位上：{@code windowClick}，Shift+左键是真的「快速移动」；</li>
 *     <li>其它界面 / 非槽位：反射调用界面自己的 {@code mouseClicked}，必要时伪造 Shift 键状态。</li>
 * </ol>
 *
 * <p>因为完全不碰系统键盘鼠标，窗口在前台还是后台行为一致，也不会影响其他程序。
 */
@SideOnly(value = Side.CLIENT)
public final class ScreenClicker {
    private static final int KEY_LSHIFT = 42;
    private static final int KEY_RSHIFT = 54;
    private static java.lang.reflect.Field keyDownField;
    private static byte savedShiftL;
    private static byte savedShiftR;
    private static boolean shiftPushed;
    private static final Object LOCK = new Object();

    private ScreenClicker() {
    }

    /**
     * 在窗口坐标 (x, y) 点一下。
     *
     * @return null 表示已发出点击；否则是给玩家看的原因
     */
    public static String click(int pixelX, int pixelY, int button, boolean shift) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return "§c没有玩家";
        }
        GuiScreen screen = mc.currentScreen;
        if (screen == null || screen instanceof GuiBase) {
            // 世界里（或在我们自己的界面上）没有可点的控件，按原版左/右键处理
            if (button == 1) {
                ClickActions.rightClick();
            } else {
                ClickActions.leftClick();
            }
            return null;
        }
        if (mc.displayWidth <= 0 || mc.displayHeight <= 0 || screen.width <= 0 || screen.height <= 0) {
            return "§c界面尺寸异常";
        }

        int guiX = pixelX * screen.width / mc.displayWidth;
        int guiY = pixelY * screen.height / mc.displayHeight;

        if (screen instanceof GuiContainer) {
            GuiContainer container = (GuiContainer)screen;
            int left = intField(container, "guiLeft", "field_147003_i");
            int top = intField(container, "guiTop", "field_147009_r");
            Slot slot = slotAt(container, left, top, guiX, guiY);
            if (slot != null) {
                ClickType type = shift ? ClickType.QUICK_MOVE : ClickType.PICKUP;
                mc.playerController.windowClick(mc.player.openContainer.windowId, slot.slotNumber,
                        button, type, mc.player);
                return null;
            }
        }

        try {
            invokeScreen(screen, new String[]{"mouseClicked", "func_73864_a"}, guiX, guiY, button, shift);
            invokeScreen(screen, new String[]{"mouseReleased", "func_146286_b"}, guiX, guiY, 0, shift);
            return null;
        } catch (Throwable t) {
            return "§c点击失败：界面 " + screen.getClass().getSimpleName()
                    + " 在 (" + guiX + "," + guiY + ") 既没有槽位，也无法调用 mouseClicked（"
                    + t.getClass().getSimpleName() + "）";
        }
    }

    /**
     * 按界面坐标找槽位。
     *
     * <p>优先交给界面自己的 {@code getSlotAtPosition}，只有反射拿不到这个方法时才自己算。
     */
    private static Slot slotAt(GuiContainer container, int left, int top, int guiX, int guiY) {
        for (String name : new String[]{"getSlotAtPosition", "func_146975_c"}) {
            try {
                java.lang.reflect.Method method =
                        GuiContainer.class.getDeclaredMethod(name, int.class, int.class);
                method.setAccessible(true);
                Object result = method.invoke(container, guiX, guiY);
                if (result instanceof Slot) {
                    return (Slot)result;
                }
            } catch (Throwable ignored) {
                // 方法不存在，试下一套名字
            }
        }
        int localX = guiX - left;
        int localY = guiY - top;
        List<Slot> slots = container.inventorySlots.inventorySlots;
        for (int i = 0; i < slots.size(); ++i) {
            Slot slot = slots.get(i);
            if (localX >= slot.xPos && localX < slot.xPos + 16
                    && localY >= slot.yPos && localY < slot.yPos + 16) {
                return slot;
            }
        }
        return null;
    }

    /** 读 GuiContainer 的 protected 字段：开发环境是 MCP 名，正式包里是 SRG 名。 */
    private static int intField(Object target, String mcpName, String srgName) {
        for (String name : new String[]{mcpName, srgName}) {
            try {
                java.lang.reflect.Field field = GuiContainer.class.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(target);
            } catch (Throwable ignored) {
                // 试下一个名字
            }
        }
        return 0;
    }

    /**
     * 临时让界面「以为 Shift 是按着的」。
     *
     * <p>{@code GuiScreen.mouseClicked} 不带修饰键，界面判断 Shift 走的是
     * {@code Keyboard.isKeyDown(42/54)}，读的是 LWJGL 的静态 keyDownBuffer。
     * 窗口不在前台时缓冲里 Shift 恒为 false，这里直接置位，调用结束后还原。
     */
    private static boolean pushShift() {
        synchronized (LOCK) {
            try {
                if (keyDownField == null) {
                    java.lang.reflect.Field field =
                            org.lwjgl.input.Keyboard.class.getDeclaredField("keyDownBuffer");
                    field.setAccessible(true);
                    keyDownField = field;
                }
                java.nio.ByteBuffer buffer = (java.nio.ByteBuffer)keyDownField.get(null);
                if (buffer == null) {
                    return false;
                }
                savedShiftL = buffer.get(KEY_LSHIFT);
                savedShiftR = buffer.get(KEY_RSHIFT);
                buffer.put(KEY_LSHIFT, (byte)1);
                buffer.put(KEY_RSHIFT, (byte)1);
                shiftPushed = true;
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    }

    private static void popShift() {
        synchronized (LOCK) {
            if (!shiftPushed) {
                return;
            }
            shiftPushed = false;
            try {
                java.nio.ByteBuffer buffer = (java.nio.ByteBuffer)keyDownField.get(null);
                if (buffer != null) {
                    buffer.put(KEY_LSHIFT, savedShiftL);
                    buffer.put(KEY_RSHIFT, savedShiftR);
                }
            } catch (Throwable ignored) {
                // 还原失败也不该把主流程带崩
            }
        }
    }

    private static void invokeScreen(Object screen, String[] names, int x, int y, int button,
                                     boolean shift) throws Exception {
        java.lang.reflect.Method method = null;
        for (int i = 0; i < names.length; ++i) {
            try {
                method = GuiScreen.class.getDeclaredMethod(names[i], int.class, int.class, int.class);
                break;
            } catch (NoSuchMethodException ignored) {
                // 试下一个名字
            }
        }
        if (method == null) {
            throw new NoSuchMethodException(names[0]);
        }
        method.setAccessible(true);
        if (shift) {
            pushShift();
        }
        try {
            method.invoke(screen, x, y, button);
        } finally {
            if (shift) {
                popShift();
            }
        }
    }
}

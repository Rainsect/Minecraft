package com.rain.automc;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 「点击坐标」的真正实现：系统级鼠标事件。
 *
 * <p>旧实现走的是 {@code playerController.windowClick} 和反射 {@code GuiScreen.mouseClicked}，三个问题：
 * <ul>
 *     <li>坐标按 GUI 缩放算，玩家按窗口像素填就会整体偏移（GUI scale 2 时差一倍）；</li>
 *     <li>只能点容器槽位和少量按钮，自定义控件、列表、HUD 全部点不动；</li>
 *     <li>没有 {@code currentScreen} 时直接返回「没有打开的界面」，世界里根本点不了。</li>
 * </ul>
 *
 * <p>更要紧的是 shift+左键：它的「快速移动」语义由 {@code Keyboard.isKeyDown(42)} 决定，那是原生键盘
 * 状态，Java 层伪造不了，只有真的把 Shift 按下去才算数。所以这里用 {@link Robot} 发真实事件。
 *
 * <p>坐标 = 窗口像素，左上角为 (0,0)，不乘 GUI 缩放。
 */
@SideOnly(value = Side.CLIENT)
public final class ScreenClicker {
    private static final Logger LOG = LogManager.getLogger("AutoMC");
    private static final int KEY_LSHIFT = 42;
    private static final int KEY_RSHIFT = 54;
    private static java.lang.reflect.Field keyDownField;
    private static byte savedShiftL;
    private static byte savedShiftR;
    private static boolean shiftPushed;
    private static final Object LOCK = new Object();

    private static Robot robot;
    private static int originX;
    private static int originY;

    private ScreenClicker() {
    }

    /** user32：只用到枚举窗口、换算客户区坐标、把窗口提到最前。 */
    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = (User32) Native.loadLibrary("user32", User32.class);

        boolean EnumWindows(WndEnumProc callback, Pointer lParam);

        boolean GetClientRect(Pointer hWnd, WinRect rect);

        boolean ClientToScreen(Pointer hWnd, WinPoint point);

        int GetWindowThreadProcessId(Pointer hWnd, IntByReference pid);

        Pointer GetForegroundWindow();

        boolean SetForegroundWindow(Pointer hWnd);

        boolean ShowWindow(Pointer hWnd, int cmd);

        boolean BringWindowToTop(Pointer hWnd);

        boolean SetWindowPos(Pointer hWnd, Pointer insertAfter, int x, int y, int cx, int cy, int flags);

        boolean AttachThreadInput(int attach, int attachTo, boolean attachFlag);

        boolean IsWindowVisible(Pointer hWnd);

        interface WndEnumProc extends StdCallLibrary.StdCallCallback {
            boolean callback(Pointer hWnd, Pointer lParam);
        }
    }

    public interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);

        int GetCurrentProcessId();

        int GetCurrentThreadId();
    }

    /** JNA 自带的 WinDef 在这个版本里没有 POINT，自己声明。 */
    public static class WinPoint extends Structure {
        public int x;
        public int y;

        public WinPoint() {
            super();
        }

        public WinPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("x", "y");
        }
    }

    public static class WinRect extends Structure {
        public int left;
        public int top;
        public int right;
        public int bottom;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("left", "top", "right", "bottom");
        }
    }

    /**
     * 在窗口坐标 (x, y) 用真实鼠标点一下。
     *
     * <p>必须异步：游戏是在主线程 tick 里处理鼠标事件的，如果在主线程里同步按住再松开 Shift，等游戏
     * 真正处理这次点击时 Shift 早就松开了，「shift+左键」就退化成普通左键。放到独立线程后主线程可以
     * 正常 tick，Shift 保持的这段时间足以让事件被消费。
     *
     * @return null 表示成功；否则是给玩家看的错误消息
     */
    public static String click(int x, int y, int button, boolean shift) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return "§c没有玩家";
        }
        if (!locate(mc.displayWidth, mc.displayHeight)) {
            return "§c游戏窗口不在前台，为了不点到别的程序已拒绝";
        }
        final int screenX = originX + x;
        final int screenY = originY + y;
        final int mask = button == 1 ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
        final boolean withShift = shift;
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    try {
                        Robot r = robot();
                        r.mouseMove(screenX, screenY);
                        Thread.sleep(40L);
                        if (withShift) {
                            r.keyPress(KeyEvent.VK_SHIFT);
                            Thread.sleep(70L);
                        }
                        r.mousePress(mask);
                        Thread.sleep(45L);
                        r.mouseRelease(mask);
                        if (withShift) {
                            Thread.sleep(110L);
                            r.keyRelease(KeyEvent.VK_SHIFT);
                        }
                    } catch (Throwable ignored) {
                        // 窗口被切走之类的情况，放弃这次点击即可
                    }
                }
            }
        }, "AutoMC-Click");
        worker.setDaemon(true);
        worker.start();
        return null;
    }

    public static synchronized Robot robot() {
        if (robot == null) {
            try {
                robot = new Robot();
                robot.setAutoDelay(0);
                robot.setAutoWaitForIdle(false);
            } catch (Throwable t) {
                return null;
            }
        }
        return robot;
    }

    /**
     * 找到本进程的游戏窗口并确保它在最前。
     *
     * <p>不能直接取「当前前台窗口」：玩家可能开着另一个 Minecraft，那样就会点进别人的游戏里。所以按
     * 进程 PID + 可见窗口筛选，再把它提到前台并确认确实在前台。
     */
    private static boolean locate(int expectedWidth, int expectedHeight) {
        try {
            Pointer hwnd = findWindow();
            if (hwnd == null) {
                return false;
            }
            if (!hwnd.equals(User32.INSTANCE.GetForegroundWindow())) {
                User32.INSTANCE.ShowWindow(hwnd, 9); // SW_RESTORE
                User32.INSTANCE.SetWindowPos(hwnd, new Pointer(-1L), 0, 0, 0, 0, 0x0043); // HWND_TOPMOST
                sleep(100L);
                // Windows 只允许持有前台窗口的线程改焦点，临时把输入状态借过来
                Pointer fg = User32.INSTANCE.GetForegroundWindow();
                int fgThread = fg == null ? 0 : User32.INSTANCE.GetWindowThreadProcessId(fg, new IntByReference());
                int thisThread = Kernel32.INSTANCE.GetCurrentThreadId();
                boolean attached = false;
                if (fgThread != 0 && fgThread != thisThread) {
                    attached = User32.INSTANCE.AttachThreadInput(thisThread, fgThread, true);
                }
                User32.INSTANCE.SetForegroundWindow(hwnd);
                User32.INSTANCE.BringWindowToTop(hwnd);
                if (attached) {
                    User32.INSTANCE.AttachThreadInput(thisThread, fgThread, false);
                }
                sleep(180L);
                User32.INSTANCE.SetWindowPos(hwnd, new Pointer(-2L), 0, 0, 0, 0, 0x0043); // HWND_NOTOPMOST
            }
            if (!hwnd.equals(User32.INSTANCE.GetForegroundWindow())) {
                return false;
            }
            WinPoint point = new WinPoint(0, 0);
            if (!User32.INSTANCE.ClientToScreen(hwnd, point)) {
                return false;
            }
            point.read();
            originX = point.x;
            originY = point.y;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 本进程里可见的顶层窗口。 */
    private static Pointer findWindow() {
        final List<Pointer> matches = new ArrayList<Pointer>();
        final int pid = Kernel32.INSTANCE.GetCurrentProcessId();
        User32.INSTANCE.EnumWindows(new User32.WndEnumProc() {
            @Override
            public boolean callback(Pointer hWnd, Pointer lParam) {
                IntByReference owner = new IntByReference();
                User32.INSTANCE.GetWindowThreadProcessId(hWnd, owner);
                if (owner.getValue() != pid || !User32.INSTANCE.IsWindowVisible(hWnd)) {
                    return true;
                }
                matches.add(hWnd);
                return true;
            }
        }, null);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public static synchronized int originX() {
        return originX;
    }

    public static synchronized int originY() {
        return originY;
    }

    // ---------------------------------------------------------------- 后台点击
    //
    // 窗口不在前台时，系统级点击会落到别的窗口上，所以换一条路：不碰鼠标，直接走游戏内部。
    // 覆盖范围是容器槽位 + 走 GuiScreen.mouseClicked 的按钮/列表；纯自绘控件和世界内交互仍需前台。

    /**
     * 纯后台点击：不需要窗口在前台，也不需要焦点。
     *
     * @return null 表示已发出点击；否则是给玩家看的原因
     */
    public static String clickLogical(int pixelX, int pixelY, int button, boolean shift) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return "§c没有玩家";
        }
        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            String hint = mc.gameSettings.pauseOnLostFocus
                    ? "；窗口失焦时原版会弹出暂停菜单顶掉界面，可在「选项」里关掉「失去焦点时暂停」"
                    : "";
            return "§e后台点击：当前没有打开的界面，世界里只能前台点" + hint;
        }
        if (mc.displayWidth <= 0 || mc.displayHeight <= 0 || screen.width <= 0 || screen.height <= 0) {
            return "§c界面尺寸异常";
        }

        int guiX = pixelX * screen.width / mc.displayWidth;
        int guiY = pixelY * screen.height / mc.displayHeight;

        if (screen instanceof GuiContainer) {
            GuiContainer container = (GuiContainer) screen;
            int left = intField(container, "guiLeft", "field_147003_i");
            int top = intField(container, "guiTop", "field_147009_r");
            Slot slot = slotAt(container, left, top, guiX, guiY);
            if (slot != null) {
                ClickType type = shift ? ClickType.QUICK_MOVE : ClickType.PICKUP;
                mc.playerController.windowClick(mc.player.openContainer.windowId, slot.slotNumber,
                        button, type, mc.player);
                LOG.info("[AutoMC] 后台点击 container={} screen=({},{}) slot={} type={}",
                        screen.getClass().getSimpleName(), guiX, guiY, slot.slotNumber, type);
                return null;
            }
            LOG.info("[AutoMC] 后台点击没打中槽位 container={} screen=({},{}) left/top={}/{} slots={}",
                    screen.getClass().getSimpleName(), guiX, guiY, left, top,
                    container.inventorySlots.inventorySlots.size());
        }

        try {
            invokeScreen(screen, new String[]{"mouseClicked", "func_73864_a"}, guiX, guiY, button, shift);
            invokeScreen(screen, new String[]{"mouseReleased", "func_146286_b"}, guiX, guiY, 0, shift);
            LOG.info("[AutoMC] 后台点击已转发给界面自身 screen={} at ({},{})",
                    screen.getClass().getSimpleName(), guiX, guiY);
            return null;
        } catch (Throwable t) {
            return "§c后台点击失败：界面 " + screen.getClass().getSimpleName()
                    + " 在 (" + guiX + "," + guiY + ") 既没有槽位，也无法调用 mouseClicked（"
                    + t.getClass().getSimpleName() + "）";
        }
    }

    /**
     * 按界面坐标找槽位。
     *
     * <p>优先交给界面自己的 {@code getSlotAtPosition}——它就是 xPos/yPos 加 guiLeft/guiTop 的官方判定，
     * 不用我们猜偏移。只有反射拿不到这个方法时才自己算。
     *
     * <p>这点很关键：一旦这里漏判，点击会落到 {@code mouseClicked} 分支，而那条路读不到 Shift
     * （键盘状态由 LWJGL 维护，窗口不在前台时根本不更新），Shift+左键就退化成普通左键。
     */
    private static Slot slotAt(GuiContainer container, int left, int top, int guiX, int guiY) {
        // getSlotAtPosition 的 SRG 名是 func_146975_c（func_146978_c 其实是 isPointInRegion）
        for (String name : new String[]{"getSlotAtPosition", "func_146975_c"}) {
            try {
                java.lang.reflect.Method method =
                        GuiContainer.class.getDeclaredMethod(name, int.class, int.class);
                method.setAccessible(true);
                Object result = method.invoke(container, guiX, guiY);
                if (result instanceof Slot) {
                    return (Slot) result;
                }
                // 返回 null 说明这一下确实没落在槽位上，但仍然交给下面的兜底再确认一次
            } catch (Throwable ignored) {
                // 方法不存在，试下一套名字
            }
        }
        int localX = guiX - left;
        int localY = guiY - top;
        for (Slot slot : container.inventorySlots.inventorySlots) {
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

    /** 反射调用界面的鼠标方法，MCP 与 SRG 两套名依次尝试。 */
    /**
     * 临时让界面「以为 Shift 是按着的」。
     *
     * <p>GuiScreen.mouseClicked 只有三个参数，不带修饰键；界面判断 Shift 走的是
     * Keyboard.isKeyDown(42/54)，读的是 LWJGL 的静态 keyDownBuffer。窗口不在前台时
     * LWJGL 收不到键盘事件，缓冲里 Shift 恒为 false，后台点击就会退化成普通点击。
     * 这里直接把对应 bit 置上，调用结束后还原。
     */
    private static boolean pushShift() {
        try {
            if (keyDownField == null) {
                java.lang.reflect.Field field =
                        org.lwjgl.input.Keyboard.class.getDeclaredField("keyDownBuffer");
                field.setAccessible(true);
                keyDownField = field;
            }
            java.nio.ByteBuffer buffer = (java.nio.ByteBuffer) keyDownField.get(null);
            if (buffer == null) {
                return false;
            }
            // 注意：LWJGL 2.9.4 的 keyDownBuffer 是「每个键一字节」，不是位图。
            // 索引直接用键码，值非 0 即按下。
            savedShiftL = buffer.get(KEY_LSHIFT);
            savedShiftR = buffer.get(KEY_RSHIFT);
            buffer.put(KEY_LSHIFT, (byte) 1);
            buffer.put(KEY_RSHIFT, (byte) 1);
            shiftPushed = true;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void popShift() {
        if (!shiftPushed) {
            return;
        }
        shiftPushed = false;
        try {
            java.nio.ByteBuffer buffer = (java.nio.ByteBuffer) keyDownField.get(null);
            if (buffer != null) {
                buffer.put(KEY_LSHIFT, savedShiftL);
                buffer.put(KEY_RSHIFT, savedShiftR);
            }
        } catch (Throwable ignored) {
            // 还原失败也不该把主流程带崩
        }
    }

    private static void invokeScreen(Object screen, String[] names, int x, int y, int button,
                                     boolean shift) throws Exception {
        java.lang.reflect.Method method = null;
        for (String name : names) {
            try {
                method = GuiScreen.class.getDeclaredMethod(name, int.class, int.class, int.class);
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
            boolean faked = pushShift();
            LOG.info("[AutoMC] 转发点击时 Shift 状态 shift={} 伪造={} 生效={}",
                    shift, faked, org.lwjgl.input.Keyboard.isKeyDown(KEY_LSHIFT));
        }
        try {
            method.invoke(screen, x, y, button);
        } finally {
            popShift();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.rain.automc;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

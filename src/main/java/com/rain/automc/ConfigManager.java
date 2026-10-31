package com.rain.automc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/*
 * 配置读写。chains 这个 JSON 字段名沿用 v1（旧配置可直接读入），
 * 但语义已升级为「项目列表」，数量不再固定 4 条。
 */
public class ConfigManager {
    public static final int MAX_PROJECTS = 64;
    public static final int[] ACCENTS = {
        0xFF5B9CFF, 0xFF41D391, 0xFFA98BFF, 0xFFF3B544, 0xFF2ED3E0, 0xFFF06BA8,
        0xFF39404D, 0xFF9AA7B8
    };
    public static final String[] ACCENT_NAMES = {
        "\u84dd", "\u7eff", "\u7d2b", "\u6a59", "\u9752", "\u7c89", "\u9ed1", "\u7070"
    };
    public List<ScriptChain> chains = new ArrayList<ScriptChain>();
    public boolean returnHome = true;
    public int uiOpacity = 88;
    public int uiAccent = 0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile = null;

    public static ConfigManager get() {
        return Holder.INSTANCE;
    }

    public static void init(File configDir) {
        configFile = new File(configDir, "automc.json");
        boolean existed = configFile.exists();
        ConfigManager.load();
        if (!existed) {
            ConfigManager.migrateOld(configDir);
        }
        ConfigManager.normalize();
        if (!existed) {
            ConfigManager.save();
        }
    }

    private static void load() {
        ConfigManager cfg = ConfigManager.get();
        cfg.chains.clear();
        if (configFile == null || !configFile.exists()) {
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8);
            ConfigManager loaded = GSON.fromJson(reader, ConfigManager.class);
            if (loaded == null || loaded.chains == null) {
                return;
            }
            cfg.returnHome = loaded.returnHome;
            cfg.uiOpacity = loaded.uiOpacity;
            cfg.uiAccent = loaded.uiAccent;
            for (int i = 0; i < loaded.chains.size(); ++i) {
                ScriptChain c = loaded.chains.get(i);
                if (c == null) {
                    continue;
                }
                if (c.actions == null) {
                    c.actions = new ArrayList<ScriptAction>();
                }
                cfg.chains.add(c);
            }
        } catch (Exception e) {
            System.err.println("[AutoMC] \u8bfb\u53d6\u914d\u7f6e\u5931\u8d25: " + e);
        } finally {
            ConfigManager.closeQuietly(reader);
        }
    }

    /* 兜底：空列表补一个项目；v1 的 trigger 字段升级成事件积木；参数顺序按内容自动修正 */
    private static void normalize() {
        ConfigManager cfg = ConfigManager.get();
        if (cfg.uiOpacity < 35) {
            cfg.uiOpacity = 88;
        }
        if (cfg.uiOpacity > 100) {
            cfg.uiOpacity = 100;
        }
        if (cfg.uiAccent < 0 || cfg.uiAccent >= ACCENTS.length) {
            cfg.uiAccent = 0;
        }
        if (cfg.chains.isEmpty()) {
            cfg.chains.add(ConfigManager.demoProject());
        }
        for (int i = 0; i < cfg.chains.size(); ++i) {
            ScriptChain c = cfg.chains.get(i);
            ConfigManager.upgradeTrigger(c);
            List<ScriptAction> kept = new ArrayList<ScriptAction>();
            for (int k = 0; k < c.actions.size(); ++k) {
                ScriptAction a = c.actions.get(k);
                if (ConfigManager.migrateAction(a)) {
                    ConfigManager.reorderParams(a);
                    kept.add(a);
                }
            }
            c.actions = kept;
        }
    }

    /*
     * 参数顺序从「模式在前」改成「变量在前」。
     * 靠内容判断（模式值出现在最前面就是旧布局），可重复执行，不依赖配置版本戳。
     */
    private static void reorderParams(ScriptAction a) {
        if (a == null) {
            return;
        }
        if ("按键".equals(a.type)) {
            if (a.p.size() >= 3 && ConfigManager.isMode(a.param(1))) {
                String mode = a.param(1);
                String ms = a.param(2);
                a.setParam(1, ConfigManager.isInt(ms) ? ms : "200");
                a.setParam(2, mode);
            }
        } else if ("延迟".equals(a.type)) {
            if (a.p.size() >= 2 && ConfigManager.isMode(a.param(0))) {
                String mode = a.param(0);
                String ms = a.param(1);
                String lo = a.param(2);
                String hi = a.param(3);
                a.setParam(0, ConfigManager.isInt(ms) ? ms : "1000");
                a.setParam(1, lo);
                a.setParam(2, hi);
                a.setParam(3, mode);
            }
        } else if ("自动钓鱼".equals(a.type)) {
            if (a.p.size() >= 3 && ConfigManager.isMode(a.param(0))) {
                String mode = a.param(0);
                String delay = a.param(1);
                String timeout = a.param(2);
                a.setParam(0, ConfigManager.isInt(delay) ? delay : "800");
                a.setParam(1, ConfigManager.isInt(timeout) ? timeout : "30");
                a.setParam(2, mode);
            }
        }
        a.fillDefaults();
    }

    private static boolean isMode(String s) {
        if (s == null) {
            return false;
        }
        String v = s.trim();
        return v.equals("\u70b9\u6309") || v.equals("\u6309\u4f4f") || v.equals("\u677e\u5f00")
                || v.equals("\u56fa\u5b9a") || v.equals("\u968f\u673a")
                || v.equals("\u5355\u6b21") || v.equals("\u6301\u7eed");
    }

    /**
     * 把旧版积木映射成新版；已被删除或无法映射的返回 false（丢弃）。
     *
     * <p>只在读取配置时跑一次，用户重新保存后就是新格式。
     */
    private static boolean migrateAction(ScriptAction a) {
        if (a == null || a.type == null) {
            return false;
        }
        String t = a.type;
        if (Catalog.find(t) != null) {
            // 仍然有效；顺带补齐新版新增的参数（例如走到坐标的允许破坏/搭桥）
            if ("按键".equals(t)) {
                String mode = a.param(1);
                if (a.p.size() == 2 && ConfigManager.isInt(mode)) {
                    // 更早的布局：0=键名 1=毫秒，补成 0=键名 1=点按 2=毫秒（随后再重排）
                    String key = a.param(0);
                    int ms = ScriptAction.toInt(mode, 200);
                    a.p.clear();
                    a.setParam(0, key.isEmpty() ? "SPACE" : key);
                    a.setParam(1, "\u70b9\u6309");
                    a.setParam(2, String.valueOf(ms));
                }
            } else if ("延迟".equals(t) && a.p.size() == 1 && ConfigManager.isInt(a.param(0))) {
                // 更早的延迟：0=毫秒，补成 0=固定 1=毫秒（随后再重排）
                int ms = ScriptAction.toInt(a.param(0), 1000);
                a.p.clear();
                a.setParam(0, "\u56fa\u5b9a");
                a.setParam(1, String.valueOf(ms));
            }
            if (a.enabled) {
                a.fillDefaults();
            }
            return true;
        }
        if (t.equals("\u53d1\u9001\u804a\u5929") || t.equals("\u53d1\u9001\u6307\u4ee4") || t.equals("\u63d0\u793a\u81ea\u5df1")) {
            a.type = "\u53d1\u9001\u6d88\u606f";
            a.fillDefaults();
            return true;
        }
        if (t.equals("\u7b49\u5f85")) {
            int ms = ScriptAction.toInt(a.param(0), 1000);
            a.type = "\u5ef6\u8fdf";
            a.p.clear();
            a.setParam(0, "\u56fa\u5b9a");
            a.setParam(1, String.valueOf(ms));
            a.fillDefaults();
            return true;
        }
        if (t.equals("\u968f\u673a\u5ef6\u8fdf")) {
            String lo = a.param(0);
            String hi = a.param(1);
            a.type = "\u5ef6\u8fdf";
            a.p.clear();
            a.setParam(0, "\u968f\u673a");
            a.setParam(1, "1000");
            a.setParam(2, lo);
            a.setParam(3, hi);
            a.fillDefaults();
            return true;
        }
        if (t.equals("\u6761\u4ef6\u8df3\u8f6c")) {
            a.type = "\u8df3\u8f6c";
            a.fillDefaults();
            return true;
        }
        if (t.equals("\u6253\u5f00\u80cc\u5305")) {
            return ConfigManager.retype(a, "\u6309\u952e", "E");
        }
        if (t.equals("\u5173\u95ed\u754c\u9762")) {
            return ConfigManager.retype(a, "\u6309\u952e", "ESCAPE");
        }
        if (t.equals("\u75be\u8dd1")) {
            return ConfigManager.retype(a, "\u6309\u952e", "LCTRL");
        }
        // 左键/右键/长按/复制到剪贴板/变量设置/变量运算/复活/背包满/NPC交易等已删除
        return false;
    }

    private static boolean retype(ScriptAction a, String type, String key) {
        a.type = type;
        a.p.clear();
        a.setParam(0, key);
        a.setParam(1, "200");
        a.setParam(2, "\u70b9\u6309");
        a.fillDefaults();
        return true;
    }

    private static boolean isInt(String s) {
        if (s == null || s.trim().isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void upgradeTrigger(ScriptChain c) {
        if (c.trigger == null || c.trigger.isEmpty() || c.trigger.equals("\u624b\u52a8")) {
            return;
        }
        ScriptAction ev = new ScriptAction();
        String trg = c.trigger;
        if (trg.equals("\u5165\u670d\u65f6")) {
            ev.type = "\u8fdb\u670d";
        } else if (trg.equals("\u6b7b\u4ea1\u540e")) {
            ev.type = "\u6b7b\u4ea1";
        } else if (trg.equals("\u5b9a\u65f6\u5faa\u73af")) {
            ev.type = "\u5b9a\u65f6";
            ev.setParam(0, String.valueOf(Math.max(1, c.loopSec)));
        } else if (trg.equals("\u80cc\u5305\u7269\u54c1")) {
            ev.type = "\u80cc\u5305\u7269\u54c1";
            ev.setParam(0, c.itemName == null ? "diamond" : c.itemName);
            ev.setParam(1, String.valueOf(Math.max(1, c.itemCount)));
        } else if (trg.equals("\u80cc\u5305\u6ee1\u4e86")) {
            ev.type = "\u80cc\u5305\u6ee1";
        } else {
            return;
        }
        ev.fillDefaults();
        c.actions.add(0, ev);
        c.trigger = "\u624b\u52a8";
    }

    public int opacity255() {
        int v = this.uiOpacity;
        if (v < 35) {
            v = 35;
        }
        if (v > 100) {
            v = 100;
        }
        return v * 255 / 100;
    }

    public int accentColor() {
        int i = this.uiAccent;
        if (i < 0 || i >= ACCENTS.length) {
            i = 0;
        }
        return ACCENTS[i];
    }

    public static ScriptChain demoProject() {
        ScriptChain c = new ScriptChain("\u793a\u4f8b\u9879\u76ee");
        ScriptAction ev = new ScriptAction("\u8fdb\u670d");
        ev.fillDefaults();
        c.actions.add(ev);
        ScriptAction wait = new ScriptAction("\u5ef6\u8fdf");
        wait.setParam(0, "2000");
        wait.fillDefaults();
        c.actions.add(wait);
        ScriptAction chat = new ScriptAction("\u53d1\u9001\u6d88\u606f");
        chat.setParam(0, "/spawn");
        chat.fillDefaults();
        c.actions.add(chat);
        return c;
    }

    public static void save() {
        if (configFile == null) {
            return;
        }
        configFile.getParentFile().mkdirs();
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8);
            GSON.toJson(ConfigManager.get(), writer);
        } catch (Exception e) {
            System.err.println("[AutoMC] \u4fdd\u5b58\u914d\u7f6e\u5931\u8d25: " + e);
        } finally {
            ConfigManager.closeQuietly(writer);
        }
    }

    public static int add(ScriptChain c) {
        if (ConfigManager.get().chains.size() >= MAX_PROJECTS) {
            return -1;
        }
        ConfigManager.get().chains.add(c);
        return ConfigManager.get().chains.size() - 1;
    }

    /*
     * 旧配置迁移：autocommandzh.json（入服/死亡/定时消息）与 endframe.json
     * （自动捡掉落物/自动走框架）迁移为独立项目。只在首次生成 automc.json 时执行。
     */
    private static void migrateOld(File configDir) {
        ConfigManager cfg = ConfigManager.get();
        boolean migrated = false;
        File oldAc = new File(configDir, "autocommandzh.json");
        if (oldAc.exists()) {
            Reader reader = null;
            try {
                reader = new InputStreamReader(new FileInputStream(oldAc), StandardCharsets.UTF_8);
                OldAuto old = GSON.fromJson(reader, OldAuto.class);
                if (old != null) {
                    cfg.chains.add(ConfigManager.fromMessages("\u5165\u670d\u6307\u4ee4", "\u8fdb\u670d", old.joinActions));
                    cfg.chains.add(ConfigManager.fromMessages("\u6b7b\u4ea1\u540e\u6307\u4ee4", "\u6b7b\u4ea1", old.deathActions));
                    if (old.timedActions != null && !old.timedActions.isEmpty()) {
                        ScriptChain c = new ScriptChain("\u5b9a\u65f6\u6307\u4ee4");
                        ScriptAction ev = new ScriptAction("\u5b9a\u65f6");
                        int min = Integer.MAX_VALUE;
                        for (int i = 0; i < old.timedActions.size(); ++i) {
                            OldMsg m = old.timedActions.get(i);
                            if (m.intervalSec > 0 && m.intervalSec < min) {
                                min = m.intervalSec;
                            }
                        }
                        ev.setParam(0, String.valueOf(min == Integer.MAX_VALUE ? 60 : Math.max(1, min)));
                        c.actions.add(ev);
                        for (int i = 0; i < old.timedActions.size(); ++i) {
                            OldMsg m = old.timedActions.get(i);
                            if (m.text == null || m.text.isEmpty()) {
                                continue;
                            }
                            ScriptAction a = new ScriptAction("\u53d1\u9001\u804a\u5929");
                            a.setParam(0, m.text);
                            a.enabled = m.enabled;
                            a.delayMs = Math.max(0, m.delayMs);
                            c.actions.add(a);
                        }
                        cfg.chains.add(c);
                    }
                    migrated = true;
                }
            } catch (Exception e) {
                System.err.println("[AutoMC] \u8fc1\u79fb autocommandzh.json \u5931\u8d25: " + e);
            } finally {
                ConfigManager.closeQuietly(reader);
            }
        }
        File oldEf = new File(configDir, "endframe.json");
        if (oldEf.exists()) {
            Reader reader = null;
            try {
                reader = new InputStreamReader(new FileInputStream(oldEf), StandardCharsets.UTF_8);
                OldEF old = GSON.fromJson(reader, OldEF.class);
                if (old != null) {
                    ScriptChain c = new ScriptChain("\u81ea\u52a8\u52a9\u624b");
                    ScriptAction ev = new ScriptAction("\u5b9a\u65f6");
                    ev.setParam(0, String.valueOf(old.intervalTicks == null ? 1 : Math.max(1, (old.intervalTicks + 19) / 20)));
                    c.actions.add(ev);
                    if (Boolean.TRUE.equals(old.autoItem)) {
                        ScriptAction a = new ScriptAction("\u6361\u6389\u843d\u7269");
                        a.setParam(0, String.valueOf(old.itemRangeX == null ? 8 : old.itemRangeX));
                        a.setParam(1, String.valueOf(old.itemRangeY == null ? 5 : old.itemRangeY));
                        a.setParam(2, String.valueOf(old.itemRangeZ == null ? 8 : old.itemRangeZ));
                        c.actions.add(a);
                    }
                    if (Boolean.TRUE.equals(old.autoFrame)) {
                        ScriptAction a = new ScriptAction("\u8d70\u5230\u65b9\u5757");
                        a.setParam(0, "end_portal_frame");
                        a.setParam(1, String.valueOf(old.frameRangeX == null ? 24 : old.frameRangeX));
                        a.setParam(2, String.valueOf(old.frameRangeY == null ? 8 : old.frameRangeY));
                        a.setParam(3, String.valueOf(old.frameRangeZ == null ? 24 : old.frameRangeZ));
                        c.actions.add(a);
                    }
                    cfg.chains.add(c);
                    migrated = true;
                }
            } catch (Exception e) {
                System.err.println("[AutoMC] \u8fc1\u79fb endframe.json \u5931\u8d25: " + e);
            } finally {
                ConfigManager.closeQuietly(reader);
            }
        }
        if (migrated) {
            System.out.println("[AutoMC] \u5df2\u4ece\u65e7\u914d\u7f6e\u8fc1\u79fb");
        }
    }

    private static ScriptChain fromMessages(String name, String event, List<OldMsg> list) {
        ScriptChain c = new ScriptChain(name);
        ScriptAction ev = new ScriptAction(event);
        ev.fillDefaults();
        c.actions.add(ev);
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                OldMsg m = list.get(i);
                if (m.text == null || m.text.isEmpty()) {
                    continue;
                }
                ScriptAction a = new ScriptAction("\u53d1\u9001\u804a\u5929");
                a.setParam(0, m.text);
                a.enabled = m.enabled;
                a.delayMs = Math.max(0, m.delayMs);
                c.actions.add(a);
            }
        }
        return c;
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception exception) {
            }
        }
    }

    private static class OldMsg {
        public String text = "";
        public int delayMs = 1000;
        public int intervalSec = 60;
        public boolean enabled = true;
    }

    private static class OldAuto {
        public List<OldMsg> joinActions;
        public List<OldMsg> deathActions;
        public List<OldMsg> timedActions;
    }

    private static class OldEF {
        public Boolean autoItem;
        public Boolean autoFrame;
        public Boolean returnHome;
        public Integer itemRangeX;
        public Integer itemRangeY;
        public Integer itemRangeZ;
        public Integer frameRangeX;
        public Integer frameRangeY;
        public Integer frameRangeZ;
        public Integer intervalTicks;
    }

    private static class Holder {
        static final ConfigManager INSTANCE = new ConfigManager();
    }
}

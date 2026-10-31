package com.rain.automc;

import java.util.ArrayList;
import java.util.List;

/* 一个积木块：事件、侦测或动作。type 与 Catalog 中的名字一一对应。 */
public class ScriptAction {
    public String type = "\u53d1\u9001\u804a\u5929";
    public boolean enabled = true;
    public int delayMs = 0;
    public List<String> p = new ArrayList<String>();

    public ScriptAction() {
    }

    public ScriptAction(String type) {
        this.type = type;
    }

    public ScriptAction copy() {
        ScriptAction a = new ScriptAction();
        a.type = this.type;
        a.enabled = this.enabled;
        a.delayMs = this.delayMs;
        a.p = new ArrayList<String>(this.p);
        return a;
    }

    public String param(int i) {
        if (i < 0 || i >= this.p.size()) {
            return "";
        }
        String s = this.p.get(i);
        return s == null ? "" : s;
    }

    public void setParam(int i, String v) {
        while (this.p.size() <= i) {
            this.p.add("");
        }
        this.p.set(i, v == null ? "" : v);
    }

    public int intParam(int i, int def) {
        return ScriptAction.toInt(param(i), def);
    }

    public double doubleParam(int i, double def) {
        try {
            return Double.parseDouble(param(i).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int toInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /* 按目录定义补全缺失参数，切类型时不清掉用户已填的兼容项 */
    public void fillDefaults() {
        Catalog.Def d = Catalog.find(this.type);
        if (d == null) {
            return;
        }
        while (this.p.size() < d.defaults.length) {
            this.p.add("");
        }
        for (int i = 0; i < d.defaults.length; ++i) {
            if (this.param(i).isEmpty() && !d.defaults[i].isEmpty()) {
                this.p.set(i, ScriptAction.plainDefault(d.defaults[i]));
            }
        }
    }

    public static String plainDefault(String def) {
        // @ 开头是下拉选项（取第一个）；# 开头是按键捕获控件（整段就是默认键名）
        if (def != null && def.startsWith("@")) {
            String body = def.substring(1);
            int comma = body.indexOf(',');
            return comma < 0 ? body : body.substring(0, comma);
        }
        if (def != null && def.startsWith("#")) {
            return def.substring(1);
        }
        return def == null ? "" : def;
    }

    /* 按当前参数值隐藏无关字段：延迟看模式、按键看模式、界面状态看类型 */
    public static boolean hiddenParam(ScriptAction a, int index) {
        String t = a.type;
        if ("延迟".equals(t)) {
            if ("随机".equals(a.param(3))) {
                return index == 0;
            }
            return index == 1 || index == 2;
        }
        if ("按键".equals(t)) {
            return index == 1 && "松开".equals(a.param(2));
        }
        if ("界面状态".equals(t)) {
            return index == 1 && !"自定义".equals(a.param(0));
        }
        return false;
    }

    /* 列表里显示的一行摘要 */
    public String summary() {
        Catalog.Def d = Catalog.find(this.type);
        if (d == null) {
            StringBuilder raw = new StringBuilder();
            for (int i = 0; i < this.p.size(); ++i) {
                if (this.param(i).isEmpty()) {
                    continue;
                }
                if (raw.length() > 0) {
                    raw.append(' ');
                }
                raw.append(this.param(i));
            }
            String r = raw.toString();
            return r.length() > 52 ? r.substring(0, 50) + "\u2026" : r;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.labels.length; ++i) {
            if (ScriptAction.hiddenParam(this, i)) {
                continue;
            }
            String v = this.param(i);
            if (v.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("  ");
            }
            if (d.labels.length > 1) {
                sb.append(d.labels[i]).append(':');
            }
            sb.append(v);
            if (sb.length() > 46) {
                break;
            }
        }
        String s = sb.toString();
        return s.length() > 52 ? s.substring(0, 50) + "\u2026" : s;
    }

    public boolean isEvent() {
        return Catalog.isEvent(this.type);
    }

    public String cat() {
        Catalog.Def d = Catalog.find(this.type);
        return d == null ? "\u7cfb\u7edf" : d.cat;
    }
}

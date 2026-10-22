package com.rain.automc;

import java.util.ArrayList;
import java.util.List;

/*
 * 一个自动化项目：名称 + 积木块序列（事件块 + 动作块）。
 * 字段保留 v1 的 trigger/loopSec/itemName/itemCount/cooldownSec，用于旧配置迁移。
 */
public class ScriptChain {
    public String name = "\u65b0\u9879\u76ee";
    public Boolean enabled = Boolean.TRUE;
    public String hotkey = "";
    public String trigger = "\u624b\u52a8";
    public int loopSec = 10;
    public String itemName = "diamond";
    public int itemCount = 64;
    public int cooldownSec = 3;
    public List<ScriptAction> actions = new ArrayList<ScriptAction>();

    public ScriptChain() {
    }

    public ScriptChain(String name) {
        this.name = name;
    }

    public ScriptChain copy() {
        ScriptChain c = new ScriptChain();
        c.name = this.name;
        c.enabled = this.enabled;
        c.hotkey = this.hotkey;
        c.trigger = this.trigger;
        c.loopSec = this.loopSec;
        c.itemName = this.itemName;
        c.itemCount = this.itemCount;
        c.cooldownSec = this.cooldownSec;
        for (int i = 0; i < this.actions.size(); ++i) {
            c.actions.add(this.actions.get(i).copy());
        }
        return c;
    }

    public boolean isEnabled() {
        return this.enabled == null || this.enabled.booleanValue();
    }

    public void setEnabled(boolean on) {
        this.enabled = Boolean.valueOf(on);
    }

    public int actionCount() {
        int n = 0;
        for (int i = 0; i < this.actions.size(); ++i) {
            if (!this.actions.get(i).isEvent()) {
                ++n;
            }
        }
        return n;
    }

    public int eventCount() {
        int n = 0;
        for (int i = 0; i < this.actions.size(); ++i) {
            if (this.actions.get(i).isEvent()) {
                ++n;
            }
        }
        return n;
    }

    /* 事件名摘要：用于列表卡片 */
    public String eventSummary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.actions.size(); ++i) {
            ScriptAction a = this.actions.get(i);
            if (!a.isEvent()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(a.type);
            if (a.param(0) != null && !a.param(0).isEmpty() && Catalog.find(a.type) != null
                    && Catalog.find(a.type).labels.length > 0) {
                sb.append(' ').append(a.param(0));
            }
            if (sb.length() > 40) {
                break;
            }
        }
        return sb.length() == 0 ? "\u624b\u52a8\u89e6\u53d1" : sb.toString();
    }

    public boolean auto() {
        for (int i = 0; i < this.actions.size(); ++i) {
            ScriptAction a = this.actions.get(i);
            if (a.isEvent() && !a.type.equals("\u624b\u52a8")) {
                return true;
            }
        }
        return false;
    }
}

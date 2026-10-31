package com.rain.automc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/* 纯 JVM 自检：配置读写、v1 触发迁移、目录完整性。不依赖游戏。 */
public class CfgTest {
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "testcfg");
        dir.mkdirs();
        File cfg = new File(dir, "automc.json");
        if (cfg.exists()) {
            cfg.delete();
        }
        // 造一份旧配置：trigger 字段 + 已删除的积木 + 旧布局的按键/延迟
        String v1 = "{\"chains\":[{\"name\":\"旧入服\",\"trigger\":\"入服时\",\"loopSec\":10,"
                + "\"itemName\":\"diamond\",\"itemCount\":64,\"cooldownSec\":3,"
                + "\"actions\":[{\"type\":\"发送聊天\",\"enabled\":true,\"p\":[\"/spawn\"]},"
                + "{\"type\":\"等待\",\"enabled\":true,\"p\":[\"500\"]},"
                + "{\"type\":\"右键\",\"enabled\":true,\"p\":[]},"
                + "{\"type\":\"按键\",\"enabled\":true,\"p\":[\"SPACE\",\"300\"]},"
                + "{\"type\":\"变量设置\",\"enabled\":true,\"p\":[\"a\",\"1\"]}]},"
                + "{\"name\":\"旧定时\",\"trigger\":\"定时循环\",\"loopSec\":30,"
                + "\"actions\":[{\"type\":\"发送消息\",\"enabled\":true,\"p\":[\"hi\"]},"
                + "{\"type\":\"随机延迟\",\"enabled\":true,\"p\":[\"500\",\"1500\"]}]}],"
                + "\"returnHome\":true}";
        Writer w = new OutputStreamWriter(new FileOutputStream(cfg), StandardCharsets.UTF_8);
        w.write(v1);
        w.close();

        ConfigManager.init(dir);
        System.out.println("projects = " + ConfigManager.get().chains.size());
        for (int i = 0; i < ConfigManager.get().chains.size(); ++i) {
            ScriptChain c = ConfigManager.get().chains.get(i);
            System.out.println("  [" + i + "] " + c.name + " | auto=" + c.auto()
                    + " | events=" + c.eventCount() + " actions=" + c.actionCount());
            for (int k = 0; k < c.actions.size(); ++k) {
                ScriptAction a = c.actions.get(k);
                System.out.println("        " + (k + 1) + ". " + a.type + "  «" + a.summary() + "»"
                        + (a.isEvent() ? "  [事件]" : ""));
            }
        }
        ConfigManager.save();
        System.out.println("saved bytes = " + cfg.length());

        // 再读一次，确认 XML/JSON 往返不炸
        ConfigManager.init(dir);
        System.out.println("reload projects = " + ConfigManager.get().chains.size());

        // 目录完整性
        System.out.println("action defs = " + Catalog.count(false) + ", event defs = " + Catalog.count(true));
        System.out.println("动作 " + Catalog.countCat("动作") + " / 流程 " + Catalog.countCat("流程")
                + " / 移动 " + Catalog.countCat("移动"));
        if (Catalog.count(false) != 23 || Catalog.count(true) != 18) {
            System.out.println("  !! 数量与预期（23 动作 / 18 事件）不一致");
        }
        int leftover = 0;
        for (int i = 0; i < ConfigManager.get().chains.size(); ++i) {
            ScriptChain c = ConfigManager.get().chains.get(i);
            for (int k = 0; k < c.actions.size(); ++k) {
                ScriptAction a = c.actions.get(k);
                if (a.type.equals("右键") || a.type.equals("左键") || a.type.equals("变量设置")
                        || a.type.equals("变量运算") || a.type.equals("随机延迟")
                        || a.type.equals("条件跳转") || a.type.equals("发送聊天")
                        || a.type.equals("等待") || a.type.equals("复活") || a.type.equals("背包满")) {
                    ++leftover;
                    System.out.println("  !! 未迁移: " + a.type);
                }
            }
        }
        System.out.println("leftover removed types = " + leftover);
        int noDefaults = 0;
        for (int i = 0; i < Catalog.ALL.size(); ++i) {
            Catalog.Def d = Catalog.ALL.get(i);
            for (int k = 0; k < d.defaults.length; ++k) {
                if (d.defaults[k].isEmpty() && !d.labels[k].isEmpty()) {
                    ++noDefaults;
                }
            }
        }
        System.out.println("params without default = " + noDefaults);

        // 每种积木都能建出来并填默认值
        for (int i = 0; i < Catalog.ALL.size(); ++i) {
            Catalog.Def d = Catalog.ALL.get(i);
            ScriptAction a = new ScriptAction(d.name);
            a.fillDefaults();
            if (a.p.size() < d.labels.length) {
                System.out.println("  !! missing params: " + d.name);
            }
        }
        System.out.println("SelfTest OK");
    }
}

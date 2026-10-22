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
        // 造一份 v1 风格配置：trigger 字段 + 3 个动作
        String v1 = "{\"chains\":[{\"name\":\"旧入服\",\"trigger\":\"入服时\",\"loopSec\":10,"
                + "\"itemName\":\"diamond\",\"itemCount\":64,\"cooldownSec\":3,"
                + "\"actions\":[{\"type\":\"发送消息\",\"enabled\":true,\"delayMs\":1000,\"p\":[\"/spawn\"]},"
                + "{\"type\":\"等待\",\"enabled\":true,\"delayMs\":0,\"p\":[\"500\"]}]},"
                + "{\"name\":\"旧定时\",\"trigger\":\"定时循环\",\"loopSec\":30,"
                + "\"actions\":[{\"type\":\"发送消息\",\"enabled\":true,\"delayMs\":0,\"p\":[\"hi\"]}]}],"
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

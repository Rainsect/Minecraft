package com.rain.automc;

import java.util.ArrayList;
import java.util.List;

/*
 * 积木目录：所有“事件/侦测”与“动作”的唯一定义源。
 * 界面（列表、编辑器、选择器）与引擎都从这里取名字、参数表、默认值，
 * 新增一种动作只需在下方注册一行，无需改界面代码。
 *
 * 参数写法： "标签|默认值|宽度" 多条用 ; 分隔
 *          默认值以 @ 开头 = 选项型参数，用 , 分隔选项
 */
public class Catalog {
    public static final String[] CATS = {
        "\u4e8b\u4ef6", "\u6d88\u606f", "\u6d41\u7a0b", "\u4ea4\u4e92",
        "\u79fb\u52a8", "\u7269\u54c1", "\u89c6\u89d2", "\u7cfb\u7edf"
    };
    public static final int[] CAT_COLORS = {
        0xFFF5A65B, 0xFF5BC8FF, 0xFF9B8CFF, 0xFF3FCF8E,
        0xFFF2C14E, 0xFFF06BA8, 0xFF2ED3E0, 0xFF9AA7B8
    };

    public static class Def {
        public final String name;
        public final String cat;
        public final String hint;
        public final boolean event;
        public final String[] labels;
        public final String[] defaults;
        public final int[] widths;

        Def(String name, String cat, String hint, boolean event, String spec) {
            this.name = name;
            this.cat = cat;
            this.hint = hint;
            this.event = event;
            List<String[]> rows = new ArrayList<String[]>();
            if (spec != null && !spec.trim().isEmpty()) {
                String[] parts = spec.split(";");
                for (int i = 0; i < parts.length; ++i) {
                    String[] f = parts[i].split("\\|", -1);
                    String label = f.length > 0 ? f[0].trim() : "";
                    String def = f.length > 1 ? f[1] : "";
                    String w = f.length > 2 ? f[2].trim() : "80";
                    if (label.isEmpty()) {
                        continue;
                    }
                    int width;
                    try {
                        width = Integer.parseInt(w);
                    } catch (NumberFormatException e) {
                        width = 80;
                    }
                    rows.add(new String[]{label, def, String.valueOf(width)});
                }
            }
            this.labels = new String[rows.size()];
            this.defaults = new String[rows.size()];
            this.widths = new int[rows.size()];
            for (int i = 0; i < rows.size(); ++i) {
                String[] r = rows.get(i);
                this.labels[i] = r[0];
                this.defaults[i] = r[1];
                this.widths[i] = Integer.parseInt(r[2]);
            }
        }

        public int color() {
            return Catalog.colorOf(this.cat);
        }

        public int paramCount() {
            return this.labels.length;
        }
    }

    public static final List<Def> ALL = new ArrayList<Def>();

    private static void a(String name, String cat, String hint, String spec) {
        ALL.add(new Def(name, cat, hint, false, spec));
    }

    private static void e(String name, String cat, String hint, String spec) {
        ALL.add(new Def(name, cat, hint, true, spec));
    }

    static {
        /* ---------------- 事件 / 侦测 ---------------- */
        e("\u8fdb\u670d", "\u4e8b\u4ef6", "\u8fdb\u5165\u4e16\u754c\u540e\u89e6\u53d1\u4e00\u6b21", "");
        e("\u6b7b\u4ea1", "\u4e8b\u4ef6", "\u8840\u91cf\u5f52\u96f6\u65f6\u89e6\u53d1", "");
        e("\u590d\u6d3b", "\u4e8b\u4ef6", "\u6b7b\u540e\u91cd\u751f\u65f6\u89e6\u53d1", "");
        e("\u5b9a\u65f6", "\u4e8b\u4ef6", "\u6bcf N \u79d2\u89e6\u53d1", "\u95f4\u9694\u79d2|60|60");
        e("\u8840\u91cf\u4f4e\u4e8e", "\u4e8b\u4ef6", "\u8840\u91cf\u4f4e\u4e8e\u9608\u503c", "\u8840\u91cf|10|55");
        e("\u9965\u997f\u4f4e\u4e8e", "\u4e8b\u4ef6", "\u9965\u997f\u503c\u4f4e\u4e8e\u9608\u503c", "\u9965\u997f|6|55");
        e("\u80cc\u5305\u7269\u54c1", "\u4e8b\u4ef6", "\u80cc\u5305\u91cc\u8be5\u7269\u54c1\u8fbe\u5230\u6570\u91cf", "\u7269\u54c1|diamond|100;\u6570\u91cf|64|55");
        e("\u80cc\u5305\u6ee1", "\u4e8b\u4ef6", "36 \u683c\u80cc\u5305\u5168\u6ee1", "");
        e("\u7ecf\u9a8c\u7b49\u7ea7", "\u4e8b\u4ef6", "\u7ecf\u9a8c\u7b49\u7ea7\u8fbe\u5230", "\u7b49\u7ea7|30|55");
        e("\u5207\u6362\u7ef4\u5ea6", "\u4e8b\u4ef6", "\u8de8\u4e16\u754c/\u4e0b\u754c\u65f6\u89e6\u53d1", "");
        e("\u53d7\u4f24", "\u4e8b\u4ef6", "\u8840\u91cf\u51cf\u5c11\u65f6\u89e6\u53d1", "");
        e("\u804a\u5929\u542b\u5173\u952e\u8bcd", "\u4e8b\u4ef6", "\u6536\u5230\u542b\u5173\u952e\u8bcd\u7684\u804a\u5929", "\u5173\u952e\u8bcd|\u4f60\u597d|120");
        e("\u9644\u8fd1\u6389\u843d\u7269", "\u4e8b\u4ef6", "\u5468\u56f4\u51fa\u73b0\u6389\u843d\u7269", "\u8303\u56f4|8|50");
        e("\u9644\u8fd1\u73a9\u5bb6", "\u4e8b\u4ef6", "\u5468\u56f4\u51fa\u73b0\u5176\u4ed6\u73a9\u5bb6", "\u8303\u56f4|16|50");
        e("\u5230\u8fbe\u5750\u6807", "\u4e8b\u4ef6", "\u9760\u8fd1\u6307\u5b9a\u5750\u6807", "X|0|50;Y|64|50;Z|0|50;\u534a\u5f84|3|50");
        e("Y\u4f4e\u4e8e", "\u4e8b\u4ef6", "\u9ad8\u5ea6\u4f4e\u4e8e\u9608\u503c\uff08\u5760\u843d/\u65e0\u9650\u5760\u843d\uff09", "Y|0|50");
        e("\u6e38\u620f\u65f6\u95f4", "\u4e8b\u4ef6", "\u6e38\u620f\u65f6\u949f\u5230\u8fbe\u65f6\u523b", "\u65f6\u523b|13000|70;\u5bb9\u5dee|200|60");
        e("\u624b\u6301\u53d8\u5316", "\u4e8b\u4ef6", "\u4e3b\u624b\u7269\u54c1\u53d8\u5316", "");
        e("\u8010\u4e45\u504f\u4f4e", "\u4e8b\u4ef6", "\u624b\u4e2d\u7269\u54c1\u8010\u4e45\u4f4e\u4e8e\u767e\u5206\u6bd4", "\u767e\u5206\u6bd4|20|55");
        e("\u624b\u52a8", "\u4e8b\u4ef6", "\u4e0d\u81ea\u52a8\u89e6\u53d1\uff0c\u53ea\u80fd\u70b9\u8fd0\u884c", "");

        /* ---------------- 消息 ---------------- */
        a("\u53d1\u9001\u804a\u5929", "\u6d88\u606f", "\u4ee5\u804a\u5929\u680f\u53d1\u9001\u6587\u672c", "\u5185\u5bb9|\u4f60\u597d|240");
        a("\u53d1\u9001\u6307\u4ee4", "\u6d88\u606f", "\u4ee5 / \u5f00\u5934\u53d1\u9001\u6307\u4ee4", "\u6307\u4ee4|/spawn|180");
        a("\u63d0\u793a\u81ea\u5df1", "\u7cfb\u7edf", "\u4ec5\u81ea\u5df1\u53ef\u89c1\u7684\u63d0\u793a", "\u6587\u672c|\u5df2\u6267\u884c|240");

        /* ---------------- 流程 ---------------- */
        a("\u5ef6\u8fdf", "\u6d41\u7a0b", "\u7b49\u5f85\u6307\u5b9a\u6beb\u79d2", "\u6beb\u79d2|1000|70");
        a("\u968f\u673a\u5ef6\u8fdf", "\u6d41\u7a0b", "\u5728\u4e24\u503c\u4e4b\u95f4\u968f\u673a\u7b49\u5f85", "\u6700\u77edms|500|60;\u6700\u957fms|1500|60");
        a("\u5faa\u73af\u5f00\u59cb", "\u6d41\u7a0b", "\u4ee5\u4e0b\u5230\u300c\u5faa\u73af\u7ed3\u675f\u300d\u91cd\u590d N \u6b21", "\u6b21\u6570|3|50");
        a("\u5faa\u73af\u7ed3\u675f", "\u6d41\u7a0b", "\u56de\u5230\u5339\u914d\u7684\u5f00\u59cb\u5904", "");
        a("\u8df3\u8f6c", "\u6d41\u7a0b", "\u76f4\u63a5\u8df3\u5230\u7b2c N \u884c", "\u5230\u7b2c\u51e0\u884c|1|50");
        a("\u6761\u4ef6\u8df3\u8f6c", "\u6d41\u7a0b", "\u6761\u4ef6\u4e3a\u771f\u65f6\u8df3\u884c", "\u6761\u4ef6|\u8840\u91cf<10|150;\u76ee\u6807\u884c|1|50");
        a("\u7b49\u5f85\u6761\u4ef6", "\u6d41\u7a0b", "\u76f4\u5230\u6761\u4ef6\u6210\u7acb\u6216\u8d85\u65f6", "\u6761\u4ef6|\u8840\u91cf>=20|150;\u8d85\u65f6ms|10000|70;\u8d85\u65f6\u884c|0|55");
        a("\u53d8\u91cf\u8bbe\u7f6e", "\u6d41\u7a0b", "\u8bbe\u7f6e\u4e00\u4e2a\u81ea\u5b9a\u4e49\u53d8\u91cf", "\u53d8\u91cf\u540d|count|70;\u503c|0|70");
        a("\u53d8\u91cf\u8fd0\u7b97", "\u6d41\u7a0b", "\u5bf9\u53d8\u91cf\u505a\u56db\u5219\u8fd0\u7b97", "\u53d8\u91cf\u540d|count|70;\u8fd0\u7b97|@+,-,*,/,%|60;\u503c|1|60");
        a("\u7ed3\u675f\u811a\u672c", "\u6d41\u7a0b", "\u7acb\u5373\u505c\u6b62\u672c\u9879\u76ee", "");

        /* ---------------- 交互 ---------------- */
        a("\u53f3\u952e", "\u4ea4\u4e92", "\u4f7f\u7528\u624b\u4e2d\u7269\u54c1/\u4ea4\u4e92\u65b9\u5757\u5b9e\u4f53", "");
        a("\u5de6\u952e", "\u4ea4\u4e92", "\u653b\u51fb\u5b9e\u4f53\u6216\u6316\u6398\u65b9\u5757", "");
        a("\u957f\u6309\u53f3\u952e", "\u4ea4\u4e92", "\u6309\u95f4\u9694\u91cd\u590d\u53f3\u952e", "\u6b21\u6570|10|50;\u95f4\u9694ms|100|60");
        a("\u957f\u6309\u5de6\u952e", "\u4ea4\u4e92", "\u6309\u95f4\u9694\u91cd\u590d\u5de6\u952e", "\u6b21\u6570|10|50;\u95f4\u9694ms|100|60");
        a("\u653b\u51fb\u6700\u8fd1\u5b9e\u4f53", "\u4ea4\u4e92", "\u8f6c\u5411\u5e76\u653b\u51fb\u6700\u8fd1\u76ee\u6807", "\u8303\u56f4|4|45;\u7c7b\u578b|@\u5168\u90e8,\u73a9\u5bb6,\u602a\u7269|80");
        a("NPC\u4ea4\u6613", "\u4ea4\u4e92", "\u5df2\u6253\u5f00 NPC \u754c\u9762\u540e\uff0c\u628a\u624b\u4e0a\u7269\u54c1\u6362\u6210\u6307\u5b9a\u5546\u54c1",
            "\u4ea4\u51fa\u7269\u54c1(hand=\u624b\u4e0a)||110;\u4ea4\u51fa\u6570\u91cf(all|N)|all|90;\u6362\u53d6\u7b2c\u51e0\u4ef6|1|70");

        /* ---------------- 移动 ---------------- */
        a("\u6361\u6389\u843d\u7269", "\u79fb\u52a8", "\u8d70\u5411\u8303\u56f4\u5185\u6389\u843d\u7269\u5e76\u62fe\u53d6", "\u8303\u56f4X|8|45;Y|5|45;Z|8|45");
        a("\u8d70\u5230\u65b9\u5757", "\u79fb\u52a8", "\u8d70\u5230\u6307\u5b9a\u65b9\u5757\u9876\u90e8", "\u65b9\u5757|end_portal_frame|130;X|24|45;Y|8|45;Z|24|45");
        a("\u8d70\u5230\u5750\u6807", "\u79fb\u52a8", "\u8d70\u5230\u5177\u4f53\u5750\u6807", "X|0|45;Y|64|45;Z|0|45;\u7cbe\u5ea6|0.5|50");
        a("\u524d\u8fdb", "\u79fb\u52a8", "\u6309\u4f4f\u524d\u8fdb\u6307\u5b9a\u65f6\u957f", "\u6beb\u79d2|500|60");
        a("\u505c\u6b62\u79fb\u52a8", "\u79fb\u52a8", "\u6e05\u7a7a\u79fb\u52a8\u8f93\u5165", "");
        a("\u8df3\u8dc3", "\u79fb\u52a8", "\u8fde\u7eed\u8df3 N \u6b21", "\u6b21\u6570|1|45");
        a("\u6f5c\u884c", "\u79fb\u52a8", "\u5f00/\u5173\u6f5c\u884c", "\u72b6\u6001|@\u5f00,\u5173|55");
        a("\u75be\u8dd1", "\u79fb\u52a8", "\u5f00/\u5173\u75be\u8dd1", "\u72b6\u6001|@\u5f00,\u5173|55");

        /* ---------------- 物品 ---------------- */
        a("\u5207\u6362\u5feb\u6377\u680f", "\u7269\u54c1", "\u5207\u5230\u7b2c N \u683c", "\u683c\u5b50|1|45");
        a("\u4e22\u5f03\u624b\u4e2d", "\u7269\u54c1", "\u4e22\u51fa\u624b\u4e2d\u7269\u54c1", "\u6570\u91cf|@1,\u5168\u90e8|70");
        a("\u4f7f\u7528\u624b\u4e2d\u7269\u54c1", "\u7269\u54c1", "\u53f3\u952e\u4f7f\u7528\uff08\u5403\u4e1c\u897f/\u653e\u7f6e\uff09", "");

        /* ---------------- 视角 ---------------- */
        a("\u8f6c\u5411", "\u89c6\u89d2", "\u8bbe\u7f6e\u671d\u5411\uff08yaw/pitch\uff09", "yaw|0|55;pitch|0|55");
        a("\u770b\u5411\u6700\u8fd1\u73a9\u5bb6", "\u89c6\u89d2", "\u8f6c\u5411\u6700\u8fd1\u7684\u5176\u4ed6\u73a9\u5bb6", "");

        /* ---------------- 系统 ---------------- */
        a("\u6253\u5f00\u80cc\u5305", "\u7cfb\u7edf", "\u6253\u5f00\u81ea\u5df1\u7684\u80cc\u5305\u754c\u9762", "");
        a("\u5173\u95ed\u754c\u9762", "\u7cfb\u7edf", "\u5173\u95ed\u5f53\u524d\u6240\u6709\u754c\u9762", "");
        a("\u6309\u952e", "\u7cfb\u7edf", "\u6309\u4e0b\u6307\u5b9a\u6309\u952e\u5e76\u81ea\u52a8\u677e\u5f00", "\u6309\u952e|key.jump|120;\u6beb\u79d2|200|60");
        a("\u590d\u5236\u5230\u526a\u8d34\u677f", "\u7cfb\u7edf", "\u628a\u6587\u672c\u5199\u5165\u7cfb\u7edf\u526a\u8d34\u677f", "\u6587\u672c| |160");
    }

    public static Def find(String name) {
        for (int i = 0; i < ALL.size(); ++i) {
            if (ALL.get(i).name.equals(name)) {
                return ALL.get(i);
            }
        }
        return null;
    }

    public static boolean isEvent(String name) {
        Def d = find(name);
        return d != null && d.event;
    }

    /* v1 的积木名映射到 v2 */
    public static String migrateType(String t) {
        if (t == null) {
            return "\u53d1\u9001\u804a\u5929";
        }
        if (t.equals("\u53d1\u9001\u6d88\u606f")) {
            return "\u53d1\u9001\u804a\u5929";
        }
        if (t.equals("\u7b49\u5f85")) {
            return "\u5ef6\u8fdf";
        }
        if (t.equals("\u5165\u670d\u65f6") || t.equals("\u8fdb\u670d\u65f6")) {
            return "\u8fdb\u670d";
        }
        if (t.equals("\u6b7b\u4ea1\u540e")) {
            return "\u6b7b\u4ea1";
        }
        if (t.equals("\u5b9a\u65f6\u5faa\u73af")) {
            return "\u5b9a\u65f6";
        }
        if (t.equals("\u80cc\u5305\u6ee1\u4e86")) {
            return "\u80cc\u5305\u6ee1";
        }
        return t;
    }

    public static int colorOf(String cat) {
        for (int i = 0; i < CATS.length; ++i) {
            if (CATS[i].equals(cat)) {
                return CAT_COLORS[i];
            }
        }
        return 0xFF9AA7B8;
    }

    public static List<Def> list(boolean event, String cat) {
        List<Def> out = new ArrayList<Def>();
        for (int i = 0; i < ALL.size(); ++i) {
            Def d = ALL.get(i);
            if (d.event != event) {
                continue;
            }
            if (cat != null && !cat.isEmpty() && !d.cat.equals(cat)) {
                continue;
            }
            out.add(d);
        }
        return out;
    }

    public static int count(boolean event) {
        int n = 0;
        for (int i = 0; i < ALL.size(); ++i) {
            if (ALL.get(i).event == event) {
                ++n;
            }
        }
        return n;
    }
}

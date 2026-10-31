package com.rain.automc;

import java.util.ArrayList;
import java.util.List;

/*
 * 积木目录：所有「事件」与「动作」的唯一定义源。
 * 界面（列表、编辑器、选择器）与引擎都从这里取名字、参数表、默认值，
 * 新增一种动作只需在下方注册一行，无需改界面代码。
 *
 * 分类固定为 4 页：事件 / 动作(8) / 流程(8) / 移动(8)。
 *
 * 参数写法： "标签|默认值|宽度" 多条用 ; 分隔
 *   默认值以 @ 开头 = 选项型参数，用 , 分隔选项
 *   默认值以 # 开头 = 按键捕获控件（整段就是默认键名）
 */
public class Catalog {
    public static final String CAT_EVENT = "事件";
    public static final String CAT_ACTION = "动作";
    public static final String CAT_FLOW = "流程";
    public static final String CAT_MOVE = "移动";

    public static final String[] CATS = {
        CAT_EVENT, CAT_ACTION, CAT_FLOW, CAT_MOVE
    };
    public static final int[] CAT_COLORS = {
        0xFFF5A65B, 0xFF5BC8FF, 0xFF9B8CFF, 0xFFF2C14E
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
        /* ---------------- 事件 ---------------- */
        e("进服", CAT_EVENT, "进入世界后触发一次", "");
        e("死亡", CAT_EVENT, "血量归零时触发", "");
        e("定时", CAT_EVENT, "每 N 秒触发", "间隔秒|60|60");
        e("血量低于", CAT_EVENT, "血量低于阈值", "血量|10|55");
        e("饥饿低于", CAT_EVENT, "饥饿值低于阈值", "饥饿|6|55");
        e("背包物品", CAT_EVENT, "背包里该物品达到数量", "物品|diamond|100;数量|64|55");
        e("经验等级", CAT_EVENT, "经验等级达到", "等级|30|55");
        e("切换维度", CAT_EVENT, "跨世界/下界时触发", "");
        e("受伤", CAT_EVENT, "血量减少时触发", "");
        e("聊天含关键词", CAT_EVENT, "收到含关键词的聊天", "关键词|你好|120");
        e("附近掉落物", CAT_EVENT, "周围出现掉落物", "范围|8|50");
        e("附近玩家", CAT_EVENT, "周围出现其他玩家", "范围|16|50");
        e("到达坐标", CAT_EVENT, "靠近指定坐标", "X|0|50;Y|64|50;Z|0|50;半径|3|50");
        e("Y低于", CAT_EVENT, "高度低于阈值（坠落/无限坠落）", "Y|0|50");
        e("游戏时间", CAT_EVENT, "游戏时钟到达时刻", "时刻|13000|70;容差|200|60");
        e("手持变化", CAT_EVENT, "主手物品变化", "");
        e("耐久偏低", CAT_EVENT, "手中物品耐久低于百分比", "百分比|20|55");
        e("界面状态", CAT_EVENT, "按当前打开的界面触发",
            "类型|@全部,任意界面,背包,箱子,商贩,任意容器,聊天,ESC,自定义|80;类名||110;模式|@沿触发,持续|70");

        /* ---------------- 动作 8 ---------------- */
        a("发送消息", CAT_ACTION, "把文本发到聊天栏，或只给自己看",
            "内容||240;方式|@聊天,仅自己可见|80");
        a("按键", CAT_ACTION, "按下游戏按键，前后台一致，不影响其他程序",
            "按键|#SPACE|110;毫秒|200|60;模式|@点按,按住,松开|70");
        a("点击坐标", CAT_ACTION, "在当前界面上点一下指定坐标（窗口像素，左上角 0,0）",
            "X|100|60;Y|100|60;按钮|@左键,右键,Shift+左键,Shift+右键|80");
        a("攻击最近实体", CAT_ACTION, "转向并攻击最近目标",
            "范围|4|45;类型|@全部,玩家,怪物|80");
        a("切换快捷栏", CAT_ACTION, "切到第 N 格", "格子|1|45");
        a("丢弃手中", CAT_ACTION, "丢出手中的物品", "数量|@1,全部|70");
        a("使用手中物品", CAT_ACTION, "右键使用（吃东西/放置）", "");
        a("自动钓鱼", CAT_ACTION, "抛竿后自动等鱼漂下沉、收竿并重抛",
            "收竿延迟ms|800|70;超时秒|30|60;模式|@单次,持续|65");

        /* ---------------- 流程 8 ---------------- */
        a("延迟", CAT_FLOW, "等待毫秒；随机模式在最短与最长之间随机",
            "毫秒|1000|70;最短|500|60;最长|1500|60;模式|@固定,随机|65");
        a("循环开始", CAT_FLOW, "以下到「循环结束」重复 N 次", "次数|3|50");
        a("循环结束", CAT_FLOW, "回到匹配的开始处", "");
        a("跳转", CAT_FLOW, "跳到第 N 行；填了条件则条件为真才跳",
            "条件||150;目标行|1|50");
        a("等待条件", CAT_FLOW, "直到条件成立或超时",
            "条件|血量>=20|150;超时ms|10000|70;超时行|0|55");
        a("结束脚本", CAT_FLOW, "立即停止本项目", "");
        a("并行开始", CAT_FLOW, "这一段与后面的内容同时执行", "");
        a("并行结束", CAT_FLOW, "并行段到此结束", "");

        /* ---------------- 移动 8 ---------------- */
        a("走到坐标", CAT_MOVE, "寻路走到具体坐标（有 Baritone 时用 Baritone）",
            "X|0|45;Y|64|45;Z|0|45;精度|0.5|50;允许破坏|@关,开|60;允许搭桥|@关,开|60;疾跑|@关,开|60");
        a("走到方块", CAT_MOVE, "走到指定方块顶部",
            "方块|end_portal_frame|130;范围X|24|45;范围Y|8|45;范围Z|24|45;允许破坏|@关,开|60;允许搭桥|@关,开|60;疾跑|@关,开|60");
        a("捡掉落物", CAT_MOVE, "走向范围内掉落物并拾取",
            "范围X|8|45;范围Y|5|45;范围Z|8|45");
        a("停止移动", CAT_MOVE, "清空移动输入并停下", "");
        a("跳跃", CAT_MOVE, "连续跳 N 次", "次数|1|45");
        a("潜行", CAT_MOVE, "开/关潜行", "状态|@开,关|55");
        a("转向", CAT_MOVE, "设置朝向（yaw/pitch）", "yaw|0|55;pitch|0|55");
        a("看向最近玩家", CAT_MOVE, "转向最近的其他玩家", "");
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

    public static int countCat(String cat) {
        int n = 0;
        for (int i = 0; i < ALL.size(); ++i) {
            if (!ALL.get(i).event && ALL.get(i).cat.equals(cat)) {
                ++n;
            }
        }
        return n;
    }
}

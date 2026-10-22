package com.rain.automc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerMerchant;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/*
 * NPC 交易：界面已经用「右键」动作打开后，把交出物品换成第 N 件商品。
 * 兼容原版商人面板（ContainerMerchant：0/1 输入、2 输出）与普通容器商店。
 * 只负责界面内的兑换，不再自己右击 NPC。
 */
@SideOnly(value=Side.CLIENT)
public class NpcTrader {
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_WAIT = 1;
    private static final int PHASE_TRADE = 2;

    private static int phase = PHASE_IDLE;
    private static int ticks = 0;
    private static int cooldown = 0;
    private static int idleRounds = 0;
    private static String giveKey = "hand";
    private static int giveWant = 0;
    private static int tradeIndex = 1;
    private static int given = 0;
    private static int taken = 0;
    private static String expectOut = "";
    private static String message = "";

    public static boolean isBusy() {
        return phase != PHASE_IDLE;
    }

    public static String status() {
        if (phase == PHASE_IDLE) {
            return "";
        }
        return "\u4ea4\u6613\u4e2d \u6295\u5165" + given + " \u6362\u5f97" + taken;
    }

    public static String lastMessage() {
        return message;
    }

    public static void stop() {
        phase = PHASE_IDLE;
        ticks = 0;
        cooldown = 0;
    }

    /*
     * give：物品 id / 中文名 / hand（手上）
     * countMode：all 或数字字符串
     * index：该物品能换的第几件商品（1 起）
     */
    public static String start(String give, String countMode, int index) {
        Minecraft mc = Minecraft.func_71410_x();
        EntityPlayerSP player = mc.field_71439_g;
        if (player == null) {
            return "\u00a7c\u5f53\u524d\u6ca1\u6709\u4e16\u754c";
        }
        giveKey = give == null || give.trim().isEmpty() ? "hand" : give.trim();
        giveWant = 0;
        String cm = countMode == null ? "all" : countMode.trim().toLowerCase();
        if (!cm.isEmpty() && !cm.equals("all")) {
            try {
                giveWant = Math.max(0, Integer.parseInt(cm));
            } catch (Exception e) {
                giveWant = 0;
            }
        }
        tradeIndex = Math.max(1, index);
        given = 0;
        taken = 0;
        ticks = 0;
        cooldown = 0;
        idleRounds = 0;
        expectOut = "";
        message = "";
        if (!(mc.field_71462_r instanceof GuiContainer)) {
            return "\u00a7e\u5148\u7528\u300c\u53f3\u952e\u300d\u52a8\u4f5c\u6253\u5f00 NPC \u4ea4\u6613\u754c\u9762";
        }
        if (countItem(player, resolveGive(player)) <= 0) {
            return "\u00a7e\u80cc\u5305\u91cc\u6ca1\u6709 " + resolveGive(player);
        }
        phase = PHASE_WAIT;
        return null;
    }

    public static void tick() {
        if (phase == PHASE_IDLE) {
            return;
        }
        Minecraft mc = Minecraft.func_71410_x();
        EntityPlayerSP player = mc.field_71439_g;
        if (player == null) {
            finish("\u00a7c\u73a9\u5bb6\u79bb\u7ebf");
            return;
        }
        ++ticks;
        if (ticks > 4000) {
            finish("\u00a7e\u8d85\u65f6\uff1a\u6295\u5165 " + given + " \u6362\u5f97 " + taken);
            return;
        }
        if (!(mc.field_71462_r instanceof GuiContainer)) {
            finish("\u00a7e\u4ea4\u6613\u754c\u9762\u5df2\u5173\u95ed\uff1a\u6295\u5165 " + given + " \u6362\u5f97 " + taken);
            return;
        }
        if (phase == PHASE_WAIT) {
            if (cooldown > 0) {
                --cooldown;
                return;
            }
            phase = PHASE_TRADE;
            idleRounds = 0;
        }
        if (cooldown > 0) {
            --cooldown;
            return;
        }
        Container c = player.field_71070_bA;
        if (c == null) {
            finish("\u00a7c\u5bb9\u5668\u5f02\u5e38");
            return;
        }
        String give = resolveGive(player);
        int before = countItem(player, give);
        int beforeOut = countNonGive(player, give);
        boolean acted = false;

        if (c instanceof ContainerMerchant) {
            acted = merchantStep(mc, player, c, give);
        } else {
            acted = genericStep(mc, player, c, give);
        }
        if (acted) {
            int after = countItem(player, give);
            int afterOut = countNonGive(player, give);
            if (after < before) {
                given += before - after;
            }
            if (afterOut > beforeOut) {
                taken += afterOut - beforeOut;
            }
            idleRounds = 0;
            cooldown = 3;
        } else {
            ++idleRounds;
            if (idleRounds > 30) {
                finish("\u00a7e\u65e0\u6cd5\u7ee7\u7eed\u4ea4\u6613\uff1a\u6295\u5165 " + given + " \u6362\u5f97 " + taken);
            }
        }
        if (giveWant > 0 && given >= giveWant) {
            finish("\u00a76\u5b8c\u6210\uff1a\u6295\u5165 " + given + " \u6362\u5f97 " + taken);
        } else if (countItem(player, give) <= 0) {
            finish("\u00a76\u5b8c\u6210\uff1a\u6295\u5165 " + given + " \u6362\u5f97 " + taken);
        }
    }

    /* 商人面板：0/1 输入、2 输出 */
    private static boolean merchantStep(Minecraft mc, EntityPlayerSP player, Container c, String give) {
        if (expectOut.isEmpty()) {
            expectOut = recipeOutputName(player, c, give, tradeIndex);
        }
        List<Slot> slots = c.field_75151_b;
        if (slots.size() < 3) {
            return false;
        }
        ItemStack out = slots.get(2).func_75211_c();
        if (out != null && !out.func_190926_b()) {
            quickMove(mc, player, c, 2);
            return true;
        }
        ItemStack in0 = slots.get(0).func_75211_c();
        ItemStack in1 = slots.get(1).func_75211_c();
        boolean inputEmpty = (in0 == null || in0.func_190926_b()) && (in1 == null || in1.func_190926_b());
        if (!inputEmpty) {
            return false;
        }
        int slot = findPlayerSlot(c, give);
        if (slot < 0) {
            return false;
        }
        quickMove(mc, player, c, slot);
        return true;
    }

    /* 普通容器商店：商店侧有可收的就先收，否则把手上的推进去 */
    private static boolean genericStep(Minecraft mc, EntityPlayerSP player, Container c, String give) {
        int out = findShopSlot(c, null);
        if (out >= 0) {
            quickMove(mc, player, c, out);
            return true;
        }
        int in = findPlayerSlot(c, give);
        if (in >= 0) {
            quickMove(mc, player, c, in);
            return true;
        }
        return false;
    }

    private static void quickMove(Minecraft mc, EntityPlayerSP player, Container c, int slot) {
        mc.field_71442_b.func_187098_a(c.field_75152_c, slot, 0, ClickType.QUICK_MOVE, player);
    }

    private static int findPlayerSlot(Container c, String key) {
        List<Slot> slots = c.field_75151_b;
        for (int i = 0; i < slots.size(); ++i) {
            Slot s = slots.get(i);
            if (!(s.field_75224_c instanceof InventoryPlayer)) {
                continue;
            }
            ItemStack st = s.func_75211_c();
            if (st != null && !st.func_190926_b() && matches(st, key)) {
                return i;
            }
        }
        return -1;
    }

    private static int findShopSlot(Container c, String key) {
        List<Slot> slots = c.field_75151_b;
        for (int i = 0; i < slots.size(); ++i) {
            Slot s = slots.get(i);
            if (s.field_75224_c instanceof InventoryPlayer) {
                continue;
            }
            ItemStack st = s.func_75211_c();
            if (st == null || st.func_190926_b()) {
                continue;
            }
            if (key == null || key.isEmpty() || matches(st, key)) {
                return i;
            }
        }
        return -1;
    }

    private static void finish(String msg) {
        message = msg;
        Minecraft mc = Minecraft.func_71410_x();
        if (mc.field_71462_r instanceof GuiContainer) {
            mc.func_147108_a((GuiScreen)null);
        }
        phase = PHASE_IDLE;
    }

    private static String resolveGive(EntityPlayerSP player) {
        if ("hand".equalsIgnoreCase(giveKey) || giveKey.isEmpty()) {
            ItemStack held = player.func_184614_ca();
            if (held == null || held.func_190926_b()) {
                return "";
            }
            String name = held.func_82833_r();
            return name == null ? "" : name;
        }
        return giveKey;
    }

    /* 只数「不是交出物品」的其它物品，用来估算换得了多少 */
    private static int countNonGive(EntityPlayerSP player, String give) {
        InventoryPlayer inv = player.field_71071_by;
        int n = 0;
        int size = inv.func_70302_i_();
        for (int i = 0; i < size; ++i) {
            ItemStack st = inv.func_70301_a(i);
            if (st == null || st.func_190926_b() || matches(st, give)) {
                continue;
            }
            n += st.func_190916_E();
        }
        return n;
    }

    /*
     * 读商人配方，找出「该物品能换的第 index 件商品」的产出名，用于界面显示与校验。
     * 失败则返回空串，不影响主流程。
     */
    private static String recipeOutputName(EntityPlayerSP player, Container c, String give, int index) {
        try {
            Field f = ContainerMerchant.class.getDeclaredField("field_75178_e");
            f.setAccessible(true);
            Object merchant = f.get(c);
            if (merchant == null) {
                return "";
            }
            java.lang.reflect.Method m = merchant.getClass().getMethod("func_70934_b", net.minecraft.entity.player.EntityPlayer.class);
            Object listObj = m.invoke(merchant, player);
            if (!(listObj instanceof List)) {
                return "";
            }
            List<?> list = (List<?>)listObj;
            int hit = 0;
            for (int i = 0; i < list.size(); ++i) {
                Object recipe = list.get(i);
                Object buy1 = recipe.getClass().getMethod("func_77394_a").invoke(recipe);
                Object buy2 = recipe.getClass().getMethod("func_77393_b").invoke(recipe);
                Object sell = recipe.getClass().getMethod("func_77397_d").invoke(recipe);
                if (!(buy1 instanceof ItemStack) || !(sell instanceof ItemStack)) {
                    continue;
                }
                ItemStack b1 = (ItemStack)buy1;
                ItemStack b2 = buy2 instanceof ItemStack ? (ItemStack)buy2 : null;
                boolean ok = matches(b1, give) || (b2 != null && !b2.func_190926_b() && matches(b2, give));
                if (!ok) {
                    continue;
                }
                ++hit;
                if (hit == index) {
                    return ((ItemStack)sell).func_82833_r();
                }
            }
        } catch (Throwable t) {
            return "";
        }
        return "";
    }

    /* 物品匹配：显示名包含 / 本地化名去前缀 / 注册名 */
    public static boolean matches(ItemStack st, String key) {
        if (st == null || st.func_190926_b() || key == null || key.isEmpty()) {
            return false;
        }
        String k = key.trim().toLowerCase();
        String name = st.func_82833_r();
        if (name != null && name.toLowerCase().contains(k)) {
            return true;
        }
        String un = st.func_77977_a();
        if (un == null) {
            return false;
        }
        un = un.toLowerCase();
        if (un.equals(k)) {
            return true;
        }
        int dot = un.lastIndexOf('.');
        if (dot >= 0 && un.substring(dot + 1).equals(k)) {
            return true;
        }
        return un.replace("item.", "").replace("tile.", "").equals(k);
    }

    public static int countItem(EntityPlayerSP player, String key) {
        if (player == null || key == null || key.isEmpty()) {
            return 0;
        }
        InventoryPlayer inv = player.field_71071_by;
        int n = 0;
        int size = inv.func_70302_i_();
        for (int i = 0; i < size; ++i) {
            ItemStack st = inv.func_70301_a(i);
            if (matches(st, key)) {
                n += st.func_190916_E();
            }
        }
        return n;
    }

    public static boolean inventoryFull(EntityPlayerSP player) {
        if (player == null) {
            return false;
        }
        InventoryPlayer inv = player.field_71071_by;
        int size = inv.func_70302_i_();
        int used = 0;
        for (int i = 0; i < size; ++i) {
            ItemStack st = inv.func_70301_a(i);
            if (st != null && !st.func_190926_b()) {
                ++used;
            }
        }
        return used >= size - 1;
    }

    public static List<String> nearbyNpcs(EntityPlayerSP player) {
        List<String> out = new ArrayList<String>();
        if (player == null) {
            return out;
        }
        return out;
    }

    static void tell(EntityPlayerSP p, String s) {
        if (p != null) {
            p.func_146105_b((ITextComponent)new TextComponentString(s), false);
        }
    }
}

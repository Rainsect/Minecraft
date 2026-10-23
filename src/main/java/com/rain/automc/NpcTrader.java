package com.rain.automc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(value=Side.CLIENT)
public class NpcTrader {
    public static boolean matches(ItemStack st, String key) {
        if (st == null || st.isEmpty() || key == null || key.isEmpty()) {
            return false;
        }
        String k = key.trim().toLowerCase();
        String name = st.getDisplayName();
        if (name != null && name.toLowerCase().contains(k)) {
            return true;
        }
        String un = st.getTranslationKey();
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
        InventoryPlayer inv = player.inventory;
        int n = 0;
        int size = inv.getSizeInventory();
        for (int i = 0; i < size; ++i) {
            ItemStack st = inv.getStackInSlot(i);
            if (matches(st, key)) {
                n += st.getCount();
            }
        }
        return n;
    }

    public static boolean inventoryFull(EntityPlayerSP player) {
        if (player == null) {
            return false;
        }
        InventoryPlayer inv = player.inventory;
        int size = inv.getSizeInventory();
        int used = 0;
        for (int i = 0; i < size; ++i) {
            ItemStack st = inv.getStackInSlot(i);
            if (st != null && !st.isEmpty()) {
                ++used;
            }
        }
        return used >= size - 1;
    }

    public static List<String> nearbyNpcs(EntityPlayerSP player) {
        return new ArrayList<String>();
    }
}

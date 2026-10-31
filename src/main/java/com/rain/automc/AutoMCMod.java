package com.rain.automc;

import java.io.File;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.ICommand;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = "automc", name = "AutoMC", version = "1.2.10", clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]")
public class AutoMCMod {
    public static final String MODID = "automc";
    public static final String NAME = "AutoMC";
    /* 运行时从 jar 清单读取，构建时由 gradle 写入带后缀的测试版号（如 1.2.10a） */
    public static final String VERSION = manifestVersion();
    public static KeyBinding keyOpenGui;

    private static String manifestVersion() {
        try {
            String v = AutoMCMod.class.getPackage().getImplementationVersion();
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        } catch (Throwable ignored) {
            // 开发环境没有清单，用下面的兜底值
        }
        return "1.2.10";
    }

    @Mod.EventHandler
    @SideOnly(value = Side.CLIENT)
    public void preInit(FMLPreInitializationEvent event) {
        File configDir = event.getModConfigurationDirectory();
        ConfigManager.init(configDir);
    }

    @Mod.EventHandler
    @SideOnly(value = Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand((ICommand)new AMCClientCommand());
        MinecraftForge.EVENT_BUS.register((Object)new ClientTickHandler());
        MinecraftForge.EVENT_BUS.register((Object)new AutoInput());
        MinecraftForge.EVENT_BUS.register((Object)new ClickPicker());
        MinecraftForge.EVENT_BUS.register((Object)EFWalker.INSTANCE);
        keyOpenGui = new KeyBinding("\u6253\u5f00 AutoMC", 66, "\u81ea\u52a8\u5316");
        ClientRegistry.registerKeyBinding((KeyBinding)keyOpenGui);
    }
}

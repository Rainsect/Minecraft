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

@Mod(modid = "automc", name = "AutoMC v1.2.8", version = "1.2.8", clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]")
public class AutoMCMod {
    public static final String MODID = "automc";
    public static final String NAME = "AutoMC v1.2.8";
    public static final String VERSION = "1.2.8";
    public static KeyBinding keyOpenGui;

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
        MinecraftForge.EVENT_BUS.register((Object)new ClickPicker());
        MinecraftForge.EVENT_BUS.register((Object)EFWalker.INSTANCE);
        keyOpenGui = new KeyBinding("\u6253\u5f00 AutoMC", 66, "\u81ea\u52a8\u5316");
        ClientRegistry.registerKeyBinding((KeyBinding)keyOpenGui);
    }
}

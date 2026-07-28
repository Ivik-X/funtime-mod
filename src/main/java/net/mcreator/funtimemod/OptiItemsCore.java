package net.mcreator.funtime_mod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class OptiItemsCore {
    public static boolean isGloballyEnabled = true;
    private static final File SYS_FILE = new File(Minecraft.getInstance().gameDirectory, "config/opti_sys.dat");

    static {
        try {
            if (SYS_FILE.exists()) {
                String content = Files.readString(SYS_FILE.toPath()).trim();
                isGloballyEnabled = Boolean.parseBoolean(content);
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();

        // KILL-SWITCH
        if (event.getKey() == GLFW.GLFW_KEY_U && Screen.hasControlDown()) {
            isGloballyEnabled = !isGloballyEnabled;
            try {
                SYS_FILE.getParentFile().mkdirs();
                Files.writeString(SYS_FILE.toPath(), String.valueOf(isGloballyEnabled));
            } catch (Exception ignored) {}
            
            if (!isGloballyEnabled) {
                // 1. Принудительно отключаем свечение у всех
                PlayerGlowModule.forceDisableGlow();
                
                // 2. Полностью чистим чат (останется абсолютно пустым)
                if (mc.gui != null && mc.gui.getChat() != null) {
                    mc.gui.getChat().clearMessages(false);
                }
            } else {
                // Выводим сообщение только при включении
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§a[OptiItem] Render Cache Enabled."), false);
                }
            }
            return;
        }

        if (!isGloballyEnabled) return;

        // Открытие меню настроек
        if (event.getKey() == GLFW.GLFW_KEY_RIGHT_SHIFT && mc.screen == null) {
            mc.setScreen(new OptiItemsMenuScreen());
        }
    }
}
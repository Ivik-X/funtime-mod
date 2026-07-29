package net.mcreator.funtimemod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class OptiCore {
    public static boolean isGloballyEnabled = true;
    private static boolean isDataLoaded = false;
    private static final File SYS_FILE = new File(Minecraft.getInstance().gameDirectory, "config/opti_sys.dat");

    static {
        try {
            if (SYS_FILE.exists()) isGloballyEnabled = Boolean.parseBoolean(Files.readString(SYS_FILE.toPath()).trim());
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!isDataLoaded) { OptiConfig.loadAll(); isDataLoaded = true; }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();

        if (event.getKey() == GLFW.GLFW_KEY_U && Screen.hasControlDown()) {
            isGloballyEnabled = !isGloballyEnabled;
            try { SYS_FILE.getParentFile().mkdirs(); Files.writeString(SYS_FILE.toPath(), String.valueOf(isGloballyEnabled)); } catch (Exception ignored) {}
            
            if (!isGloballyEnabled) {
                OptiRender.forceDisableGlow();
                OptiRender.isFreecam = false;
                if (mc.gui != null && mc.gui.getChat() != null) mc.gui.getChat().clearMessages(false);
            } else if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§a[OptiItem] Render Cache Enabled."), false);
            }
            return;
        }

        if (!isGloballyEnabled) return;

        if (event.getKey() == GLFW.GLFW_KEY_RIGHT_SHIFT && mc.screen == null) {
            mc.setScreen(new OptiMenu());
        }
    }
}
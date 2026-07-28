package net.mcreator.funtime_mod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.ChatFormatting;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class PlayerGlowModule {

    public static boolean isGlowActive = false;
    private static Method setSharedFlagMethod;

    static {
        try {
            setSharedFlagMethod = Entity.class.getDeclaredMethod("setSharedFlag", int.class, boolean.class);
            setSharedFlagMethod.setAccessible(true);
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!OptiItemsCore.isGloballyEnabled) return;
        
        if (event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_G && Screen.hasControlDown()) {
            if (Minecraft.getInstance().screen == null) {
                isGlowActive = !isGlowActive;
                
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                        Component.literal(isGlowActive ? "§a[OptiItem] Entity outline enabled." : "§c[OptiItem] Entity outline disabled."), false
                    );
                }
                
                if (!isGlowActive) forceDisableGlow();
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptiItemsCore.isGloballyEnabled || !isGlowActive) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Создаем фиолетовую команду локально
        Scoreboard sb = mc.level.getScoreboard();
        PlayerTeam purpleTeam = sb.getPlayerTeam("opti_purple");
        if (purpleTeam == null) {
            purpleTeam = sb.addPlayerTeam("opti_purple");
            purpleTeam.setColor(ChatFormatting.DARK_PURPLE);
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && entity != mc.player) {
                entity.setGlowingTag(true);
                setSharedFlagForce(entity, 6, true);

                // Закидываем реальных игроков в фиолетовую команду
                if (entity instanceof Player) {
                    sb.addPlayerToTeam(entity.getScoreboardName(), purpleTeam);
                }
            }
        }
    }

    // Экстренная зачистка (вызывается из ядра при Ctrl+U или при выключении ВХ)
    public static void forceDisableGlow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        
        // Удаляем команду, возвращая всем исходный цвет сервера
        Scoreboard sb = mc.level.getScoreboard();
        PlayerTeam purpleTeam = sb.getPlayerTeam("opti_purple");
        if (purpleTeam != null) sb.removePlayerTeam(purpleTeam);

        // Снимаем свечение
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity) {
                entity.setGlowingTag(false);
                setSharedFlagForce(entity, 6, false);
            }
        }
    }

    private static void setSharedFlagForce(Entity entity, int flag, boolean value) {
        if (setSharedFlagMethod != null) {
            try { setSharedFlagMethod.invoke(entity, flag, value); } 
            catch (Exception ignored) {}
        }
    }
}
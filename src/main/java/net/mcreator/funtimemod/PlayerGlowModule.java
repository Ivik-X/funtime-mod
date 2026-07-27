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
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class PlayerGlowModule {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean isGlowActive = false;
    private static Method setSharedFlagMethod;

    // Инициализируем взлом доступа (Рефлексию) при загрузке мода
    static {
        try {
            // В NeoForge 1.21 методы называются так же, как в исходниках
            setSharedFlagMethod = Entity.class.getDeclaredMethod("setSharedFlag", int.class, boolean.class);
            setSharedFlagMethod.setAccessible(true); // Снимаем защиту protected
            LOGGER.info("[PlayerGlow] Рефлексия для setSharedFlag успешно инициализирована.");
        } catch (Exception e) {
            LOGGER.error("[PlayerGlow] Ошибка инициализации рефлексии!", e);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // Включение на Ctrl + G
        if (event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_G) {
            if (Screen.hasControlDown() && Minecraft.getInstance().screen == null) {
                isGlowActive = !isGlowActive;
                
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                        Component.literal(isGlowActive ? "§a[+] ВХ (Обводка) принудительно включена!" : "§c[-] ВХ выключено."), false
                    );
                }
                
                // Очистка при выключении
                if (!isGlowActive && Minecraft.getInstance().level != null) {
                    for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
                        if (entity instanceof LivingEntity) {
                            entity.setGlowingTag(false);
                            setSharedFlagForce(entity, 6, false); // Снимаем флаг
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!isGlowActive) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Каждый клиентский тик (20 раз в сек) форсируем флаг свечения в метадате
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && entity != mc.player) {
                
                // Стандартный тег (работает для мобов и стоек)
                entity.setGlowingTag(true);
                
                // Принудительный взлом флага в DataWatcher (работает для реальных игроков)
                setSharedFlagForce(entity, 6, true);
            }
        }
    }

    // Безопасный вызов защищенного метода
    private static void setSharedFlagForce(Entity entity, int flag, boolean value) {
        if (setSharedFlagMethod != null) {
            try {
                setSharedFlagMethod.invoke(entity, flag, value);
            } catch (Exception ignored) {
                // Игнорируем, если произойдет сбой у конкретной сущности
            }
        }
    }
}
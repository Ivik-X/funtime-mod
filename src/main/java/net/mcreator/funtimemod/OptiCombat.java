package net.mcreator.funtimemod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import java.util.List;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class OptiCombat {

    private static boolean isAutoBuffing = false;
    private static int buffTimer = 0;
    private static float oldPitch = 0;
    private static int previousSlot = -1;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || event.getAction() != GLFW.GLFW_PRESS) return;
        
        if (event.getKey() == GLFW.GLFW_KEY_B && Screen.hasControlDown() && OptiConfig.settings.autoBuff) {
            oldPitch = mc.player.getXRot();
            mc.player.setXRot(90); 
            isAutoBuffing = true;
            buffTimer = 0;
            mc.player.displayClientMessage(Component.literal("§a[OptiItem] AutoBuff запущен!"), true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // AUTO BUFF (Прямое чтение из List)
        if (isAutoBuffing) {
            buffTimer++;
            if (buffTimer == 2) {
                int potSlot = -1;
                List<String> targets = OptiConfig.settings.autoBuffPotions;
                for (int i = 0; i < 9; i++) { 
                    ItemStack st = mc.player.getInventory().getItem(i);
                    if (st.getItem() instanceof SplashPotionItem) {
                        String name = st.getHoverName().getString().toLowerCase();
                        for (String t : targets) if (name.contains(t.trim().toLowerCase())) { potSlot = i; break; }
                        if (potSlot != -1) break;
                    }
                }
                if (potSlot != -1) {
                    previousSlot = mc.player.getInventory().selected;
                    mc.player.getInventory().selected = potSlot;
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                } else {
                    isAutoBuffing = false; mc.player.setXRot(oldPitch);
                }
            } else if (buffTimer == 4) {
                if (previousSlot != -1) mc.player.getInventory().selected = previousSlot;
                mc.player.setXRot(oldPitch);
                isAutoBuffing = false;
            }
        }

        if (OptiConfig.settings.hitboxExpander > 0.0) {
            float exp = (float) OptiConfig.settings.hitboxExpander;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Player && e != mc.player) {
                    e.setBoundingBox(e.getDimensions(e.getPose()).makeBoundingBox(e.position()).inflate(exp, exp, exp));
                }
            }
        }

        if (OptiConfig.settings.aimAssist && mc.options.keyAttack.isDown()) {
            Entity bestTarget = null;
            double minDiff = OptiConfig.settings.aimAssistFov;
            Vec3 eyePos = mc.player.getEyePosition();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Player && e != mc.player && e.distanceTo(mc.player) <= OptiConfig.settings.aimAssistRange) {
                    double dx = e.getX() - eyePos.x;
                    double dz = e.getZ() - eyePos.z;
                    float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                    float diff = net.minecraft.util.Mth.wrapDegrees(targetYaw - mc.player.getYRot());
                    if (Math.abs(diff) < minDiff) { minDiff = Math.abs(diff); bestTarget = e; }
                }
            }
            if (bestTarget != null) {
                double dx = bestTarget.getX() - eyePos.x;
                double dz = bestTarget.getZ() - eyePos.z;
                mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
            }
        }
    }
}
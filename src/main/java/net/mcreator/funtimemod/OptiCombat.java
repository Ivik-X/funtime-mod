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
    
    // Swap system
    private static boolean isSwapping = false;
    private static int swapStep = 0, swapTargetSlot = -1, swapDestSlot = -1;

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
            mc.player.displayClientMessage(Component.literal("§aАвтоматический бафф запущен."), true);
        }
        
        // AutoSwap (Левая рука = 45 слот в инвентаре, но для getInventory() это 40)
        if (event.getKey() == GLFW.GLFW_KEY_R && !Screen.hasControlDown()) {
            startSwap(mc, 40, OptiConfig.settings.autoSwapItems);
        }
        
        // ElytraSwap (Нагрудник = слот брони 2)
        if (event.getKey() == GLFW.GLFW_KEY_G && !Screen.hasControlDown()) {
            startSwap(mc, 38, OptiConfig.settings.elytraSwapItems);
        }
    }
    
    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (!OptiCore.isGloballyEnabled || !OptiConfig.settings.aimAssist) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) return;
        if (mc.screen != null) return;
        
        Entity bestTarget = null;
        double minDiff = OptiConfig.settings.aimAssistFov;
        Vec3 eyePos = mc.player.getEyePosition();
        for (Player e : mc.level.players()) {
            if (e != mc.player && e.distanceTo(mc.player) <= OptiConfig.settings.aimAssistRange) {
                Vec3 targetPos = e.position().add(e.getDeltaMovement().scale(2.0)); 
                double dx = targetPos.x - eyePos.x;
                double dz = targetPos.z - eyePos.z;
                float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float diff = net.minecraft.util.Mth.wrapDegrees(targetYaw - mc.player.getYRot());
                if (Math.abs(diff) < minDiff) { minDiff = Math.abs(diff); bestTarget = e; }
            }
        }
        if (bestTarget != null) {
            Vec3 targetPos = bestTarget.position().add(bestTarget.getDeltaMovement().scale(2.0)); 
            double dx = targetPos.x - eyePos.x;
            double dz = targetPos.z - eyePos.z;
            float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            
            // Наводимся ровно перед ударом
            mc.player.setYRot(targetYaw);
        }
    }
    
    private static void startSwap(Minecraft mc, int destSlot, List<String> targetNames) {
        if (targetNames.isEmpty()) return;
        swapDestSlot = destSlot; swapTargetSlot = -1;
        for (int i = 9; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (st.isEmpty()) continue;
            String name = st.getHoverName().getString();
            for (String t : targetNames) {
                if (name.contains(t)) { swapTargetSlot = i; break; }
            }
            if (swapTargetSlot != -1) break;
        }
        if (swapTargetSlot == -1) {
            for (int i = 0; i < 9; i++) {
                ItemStack st = mc.player.getInventory().getItem(i);
                if (st.isEmpty()) continue;
                String name = st.getHoverName().getString();
                for (String t : targetNames) {
                    if (name.contains(t)) { swapTargetSlot = i; break; }
                }
                if (swapTargetSlot != -1) break;
            }
        }
        if (swapTargetSlot != -1) {
            isSwapping = true; swapStep = 0;
        } else {
            mc.player.displayClientMessage(Component.literal("§cПредмет для свапа не найден."), true);
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
        
        if (isSwapping) {
            if (swapStep == 0) {
                // Клик по предмету в инвентаре
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, swapTargetSlot < 9 ? swapTargetSlot + 36 : swapTargetSlot, 0, ClickType.PICKUP, mc.player);
            } else if (swapStep == 1) {
                // Клик по слоту назначения (левая рука = 45 слот в контейнере инвентаря, нагрудник = 6 слот)
                int destContainerSlot = swapDestSlot == 40 ? 45 : 6; 
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, destContainerSlot, 0, ClickType.PICKUP, mc.player);
            } else if (swapStep == 2) {
                // Возврат старого предмета обратно
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, swapTargetSlot < 9 ? swapTargetSlot + 36 : swapTargetSlot, 0, ClickType.PICKUP, mc.player);
                isSwapping = false;
            }
            swapStep++;
        }
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        if (OptiConfig.settings.hitboxExpander > 0.0) {
            float exp = (float) OptiConfig.settings.hitboxExpander;
            for (Player e : mc.level.players()) {
                if (e != mc.player) {
                    e.setBoundingBox(e.getDimensions(e.getPose()).makeBoundingBox(e.position()).inflate(exp, exp, exp));
                }
            }
        }
    }
}
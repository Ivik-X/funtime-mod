package net.mcreator.funtime_mod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.glfw.GLFW;

import java.util.Queue;
import java.util.LinkedList;
import java.lang.reflect.Method;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class OptiItemFeatures {

    private static int lastEmergencySwapSlot = -1;
    private static boolean isEmergencyActive = false;
    private static int clickDelay = 0;
    
    private static final Queue<Integer> smartLootQueue = new LinkedList<>();
    
    private static boolean isFreecam = false;
    private static double fcX, fcY, fcZ;
    private static float fcYaw, fcPitch;

    // Рефлексия для камеры
    private static Method setPositionMethod;

    static {
        try {
            setPositionMethod = Camera.class.getDeclaredMethod("setPosition", double.class, double.class, double.class);
            setPositionMethod.setAccessible(true);
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!OptiItemsCore.isGloballyEnabled || !AutoBuyHandler.settings.marketTooltips) return;
        String cleanName = event.getItemStack().getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
        AutoBuyHandler.MarketEntry entry = AutoBuyHandler.getMarketEntry(cleanName);
        if (entry != null && entry.avgMin > 0) {
            event.getToolTip().add(Component.literal("§8[OptiItem] §7Ср. цена АХ: §e" + String.format("%,d", entry.avgMin) + "$"));
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!OptiItemsCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (AutoBuyHandler.settings.itemEsp && event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            PoseStack poseStack = event.getPoseStack();
            Vec3 camPos = event.getCamera().getPosition();
            net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ItemEntity itemEntity) {
                    ItemStack stack = itemEntity.getItem();
                    String cleanName = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
                    AutoBuyHandler.MarketEntry entry = AutoBuyHandler.getMarketEntry(cleanName);
                    
                    if (entry != null && entry.avgMin >= AutoBuyHandler.settings.itemEspMinPrice) {
                        double x = entity.getX() - camPos.x;
                        double y = entity.getY() - camPos.y + 0.5D; 
                        double z = entity.getZ() - camPos.z;

                        poseStack.pushPose();
                        poseStack.translate(x, y, z);
                        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                        poseStack.scale(-0.025F, -0.025F, 0.025F);

                        String text = "§6[$" + (entry.avgMin / 1000) + "k] §f" + cleanName;
                        float width = (float) (-mc.font.width(text) / 2);
                        
                        mc.font.drawInBatch(text, width, 0f, 0xFFFFFF, false, poseStack.last().pose(), bufferSource, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, 15728880);
                        poseStack.popPose();
                    }
                }
            }
            bufferSource.endBatch();
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!OptiItemsCore.isGloballyEnabled) { isFreecam = false; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        if (event.getAction() == GLFW.GLFW_PRESS && !Screen.hasControlDown()) {
            
            if (event.getKey() == GLFW.GLFW_KEY_G && mc.screen == null) {
                int targetSlot = findItemSlot(AutoBuyHandler.settings.gSwapItem);
                if (targetSlot != -1) { swapToOffhand(targetSlot); mc.player.displayClientMessage(Component.literal("§a[OptiItem] Fast Swap."), true); }
            }
            
            if (event.getKey() == GLFW.GLFW_KEY_X && mc.screen == null) {
                boolean wearingElytra = mc.player.getInventory().getArmor(2).getItem() == Items.ELYTRA;
                int elytraSlot = findItemSlot("elytra");
                int chestSlot = findChestplateSlot();

                if (wearingElytra && chestSlot != -1) {
                    mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, chestSlot, 0, ClickType.QUICK_MOVE, mc.player);
                    mc.player.displayClientMessage(Component.literal("§b[OptiItem] Combat Mode."), true);
                } else if (!wearingElytra && elytraSlot != -1) {
                    mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, elytraSlot, 0, ClickType.QUICK_MOVE, mc.player);
                    mc.player.displayClientMessage(Component.literal("§e[OptiItem] Flight Mode."), true);
                }
            }

            if (event.getKey() == GLFW.GLFW_KEY_V && mc.screen instanceof AbstractContainerScreen<?> containerScreen) {
                smartLootQueue.clear();
                int maxSlot = containerScreen.getMenu().slots.size() - 36;
                for (int i = 0; i < maxSlot; i++) {
                    ItemStack stack = containerScreen.getMenu().slots.get(i).getItem();
                    if (stack.isEmpty()) continue;
                    
                    String cleanName = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
                    AutoBuyHandler.MarketEntry entry = AutoBuyHandler.getMarketEntry(cleanName);
                    
                    if ((entry != null && entry.avgMin >= AutoBuyHandler.settings.smartLootMinPrice) || 
                         cleanName.contains("Сфера") || cleanName.contains("Тотем") || stack.getItem().toString().contains("shulker_box")) {
                        smartLootQueue.add(i);
                    }
                }
                if (!smartLootQueue.isEmpty()) mc.player.displayClientMessage(Component.literal("§a[OptiItem] Auto-Sort Started."), false);
            }

            if (event.getKey() == GLFW.GLFW_KEY_C && mc.screen == null) {
                isFreecam = !isFreecam;
                if (isFreecam) {
                    fcX = mc.player.getX(); fcY = mc.player.getY() + mc.player.getEyeHeight(); fcZ = mc.player.getZ();
                    fcYaw = mc.player.getYRot(); fcPitch = mc.player.getXRot();
                    mc.player.displayClientMessage(Component.literal("§a[OptiItem] Debug Camera Enabled."), true);
                } else {
                    mc.player.displayClientMessage(Component.literal("§c[OptiItem] Debug Camera Disabled."), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (isFreecam && OptiItemsCore.isGloballyEnabled && setPositionMethod != null) {
            try {
                setPositionMethod.invoke(event.getCamera(), fcX, fcY, fcZ);
            } catch (Exception ignored) {}
            event.setYaw(fcYaw);
            event.setPitch(fcPitch);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!OptiItemsCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (isFreecam) {
            mc.player.setDeltaMovement(0, 0, 0);

            fcYaw = mc.player.getYRot(); fcPitch = mc.player.getXRot();
            double speed = 1.0;
            Vec3 forward = Vec3.directionFromRotation(fcPitch, fcYaw).normalize().scale(speed);
            Vec3 right = Vec3.directionFromRotation(0, fcYaw + 90).normalize().scale(speed);
            
            if (mc.options.keyUp.isDown()) { fcX += forward.x; fcY += forward.y; fcZ += forward.z; }
            if (mc.options.keyDown.isDown()) { fcX -= forward.x; fcY -= forward.y; fcZ -= forward.z; }
            if (mc.options.keyRight.isDown()) { fcX += right.x; fcZ += right.z; }
            if (mc.options.keyLeft.isDown()) { fcX -= right.x; fcZ -= right.z; }
            if (mc.options.keyJump.isDown()) fcY += speed;
            if (mc.options.keyShift.isDown()) fcY -= speed;
        }

        if (!smartLootQueue.isEmpty() && mc.screen instanceof AbstractContainerScreen<?> container) {
            if (clickDelay > 0) { clickDelay--; return; }
            int slot = smartLootQueue.poll();
            mc.gameMode.handleInventoryMouseClick(container.getMenu().containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
            clickDelay = 2; 
            return;
        }

        if (AutoBuyHandler.settings.noHurtCam) mc.player.hurtTime = 0;
        if (AutoBuyHandler.settings.noBadEffects) {
            mc.player.removeEffect(MobEffects.BLINDNESS);
            mc.player.removeEffect(MobEffects.CONFUSION);
            mc.player.removeEffect(MobEffects.DARKNESS);
        }

        if (clickDelay > 0) { clickDelay--; return; }
        boolean hasTotem = (mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING);

        if (AutoBuyHandler.settings.autoTotem && mc.player.getOffhandItem().isEmpty() && !hasTotem) {
            int tSlot = findItemSlot("totem");
            if (tSlot != -1) { swapToOffhand(tSlot); clickDelay = 2; return; }
        }

        if (AutoBuyHandler.settings.healthSwap) {
            if (mc.player.getHealth() <= AutoBuyHandler.settings.healthThreshold) {
                if (!hasTotem) {
                    int tSlot = findItemSlot("totem");
                    if (tSlot != -1) { lastEmergencySwapSlot = tSlot; swapToOffhand(tSlot); isEmergencyActive = true; clickDelay = 2; }
                }
            } else if (isEmergencyActive && hasTotem && lastEmergencySwapSlot != -1) {
                swapToOffhand(lastEmergencySwapSlot); isEmergencyActive = false; clickDelay = 2;
            }
        }
    }

    private static int findItemSlot(String nameOrId) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = mc.player.inventoryMenu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            if (nameOrId.equalsIgnoreCase("totem") && stack.getItem() == Items.TOTEM_OF_UNDYING) return i;
            if (nameOrId.equalsIgnoreCase("elytra") && stack.getItem() == Items.ELYTRA) return i;
            String cleanName = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
            if (cleanName.toLowerCase().contains(nameOrId.toLowerCase())) return i;
        }
        return -1;
    }

    private static int findChestplateSlot() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = mc.player.inventoryMenu.slots.get(i).getItem();
            if (!stack.isEmpty() && stack.getItem().toString().contains("chestplate")) return i;
        }
        return -1;
    }

    private static void swapToOffhand(int slotId) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slotId, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, 45, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slotId, 0, ClickType.PICKUP, mc.player);
    }
}
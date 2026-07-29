package net.mcreator.funtimemod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class OptiRender {

    public static boolean isGlowActive = false;
    public static boolean isFreecam = false;
    private static double startX, startY, startZ;
    private static double fcX, fcY, fcZ;
    private static float fcYaw, fcPitch;

    private static Method setSharedFlagMethod;
    private static Method setPositionMethod;

    static {
        try { setSharedFlagMethod = Entity.class.getDeclaredMethod("setSharedFlag", int.class, boolean.class); setSharedFlagMethod.setAccessible(true); } 
        catch (Exception e) { try { setSharedFlagMethod = Entity.class.getDeclaredMethod("m_20115_", int.class, boolean.class); setSharedFlagMethod.setAccessible(true); } catch (Exception ignored) {} }
        try { setPositionMethod = Camera.class.getDeclaredMethod("setPosition", double.class, double.class, double.class); setPositionMethod.setAccessible(true); } 
        catch (Exception e) { try { setPositionMethod = Camera.class.getDeclaredMethod("m_90569_", double.class, double.class, double.class); setPositionMethod.setAccessible(true); } catch (Exception ignored) {} }
    }

    // --- ПОДСВЕТКА ТОП-3 ДЕШЕВЫХ ТОВАРОВ ---
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> container)) return;
        if (!container.getTitle().getString().contains("Поиск")) return;

        class SlotPrice {
            Slot slot; long price;
            SlotPrice(Slot s, long p) { slot = s; price = p; }
        }
        
        List<SlotPrice> prices = new ArrayList<>();
        for (int i = 0; i < 45 && i < container.getMenu().slots.size(); i++) {
            Slot slot = container.getMenu().slots.get(i);
            if (slot.getItem().isEmpty()) continue;
            long price = OptiSniper.parsePrice(slot.getItem());
            if (price > 0) prices.add(new SlotPrice(slot, price));
        }
        prices.sort(Comparator.comparingLong(s -> s.price));

        int leftPos = getGuiField(container, "leftPos", "f_97735_", (container.width - 176) / 2);
        int topPos = getGuiField(container, "topPos", "f_97736_", (container.height - 222) / 2);

        GuiGraphics g = event.getGuiGraphics();
        for (int i = 0; i < Math.min(3, prices.size()); i++) {
            Slot slot = prices.get(i).slot;
            int color = (i == 0) ? 0x6600FF00 : (i == 1) ? 0x66FFFF00 : 0x66FF8800; // Зеленый, Желтый, Оранжевый (с прозрачностью)
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, color);
        }
    }

    private static int getGuiField(AbstractContainerScreen<?> screen, String name, String srgName, int fallback) {
        try { Field f = AbstractContainerScreen.class.getDeclaredField(name); f.setAccessible(true); return f.getInt(screen); } 
        catch (Exception e) { try { Field f = AbstractContainerScreen.class.getDeclaredField(srgName); f.setAccessible(true); return f.getInt(screen); } catch (Exception ex) { return fallback; } }
    }

    // --- УМНОЖЕНИЕ ЦЕНЫ В ТУЛТИПАХ ---
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!OptiCore.isGloballyEnabled || !OptiConfig.settings.marketTooltips) return;
        ItemStack stack = event.getItemStack();
        String cleanName = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
        OptiConfig.MarketEntry entry = OptiConfig.catalog.marketPrices.get(cleanName);
        
        if (entry != null && entry.avgMin > 0) {
            long count = stack.getCount();
            String msg = "§8[OptiItem] §7Ср. цена АХ: §e" + String.format("%,d", entry.avgMin) + "$";
            if (count > 1) msg += " §8(За стак: §6" + String.format("%,d", entry.avgMin * count) + "$§8)";
            event.getToolTip().add(Component.literal(msg));
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getKey() == GLFW.GLFW_KEY_G && Screen.hasControlDown()) {
                isGlowActive = !isGlowActive;
                mc.player.displayClientMessage(Component.literal(isGlowActive ? "§a[OptiItem] Entity outline enabled." : "§c[OptiItem] Entity outline disabled."), true);
                if (!isGlowActive) forceDisableGlow();
            }
            if (event.getKey() == GLFW.GLFW_KEY_C && !Screen.hasControlDown()) {
                isFreecam = !isFreecam;
                if (isFreecam) {
                    startX = mc.player.getX(); startY = mc.player.getY(); startZ = mc.player.getZ();
                    fcX = startX; fcY = startY + mc.player.getEyeHeight(); fcZ = startZ;
                    mc.player.displayClientMessage(Component.literal("§a[OptiItem] Debug Camera Enabled."), true);
                } else {
                    mc.player.displayClientMessage(Component.literal("§c[OptiItem] Debug Camera Disabled."), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (OptiConfig.settings.noHurtCam) mc.player.hurtTime = 0;
        if (OptiConfig.settings.noBadEffects) {
            mc.player.removeEffect(MobEffects.BLINDNESS); mc.player.removeEffect(MobEffects.CONFUSION); mc.player.removeEffect(MobEffects.DARKNESS);
        }

        if (isFreecam) {
            mc.player.setDeltaMovement(0, 0, 0);
            mc.player.setPos(startX, startY, startZ);
            
            fcYaw = mc.player.getYRot(); fcPitch = mc.player.getXRot();
            double speed = 1.5;
            Vec3 forward = Vec3.directionFromRotation(fcPitch, fcYaw).normalize().scale(speed);
            Vec3 right = Vec3.directionFromRotation(0, fcYaw + 90).normalize().scale(speed);
            
            if (mc.options.keyUp.isDown()) { fcX += forward.x; fcY += forward.y; fcZ += forward.z; }
            if (mc.options.keyDown.isDown()) { fcX -= forward.x; fcY -= forward.y; fcZ -= forward.z; }
            if (mc.options.keyRight.isDown()) { fcX += right.x; fcZ += right.z; }
            if (mc.options.keyLeft.isDown()) { fcX -= right.x; fcZ -= right.z; }
            if (mc.options.keyJump.isDown()) fcY += speed;
            if (mc.options.keyShift.isDown()) fcY -= speed;
        }

        if (isGlowActive && mc.level != null) {
            Scoreboard sb = mc.level.getScoreboard();
            PlayerTeam purpleTeam = sb.getPlayerTeam("opti_purple");
            if (purpleTeam == null) { purpleTeam = sb.addPlayerTeam("opti_purple"); purpleTeam.setColor(ChatFormatting.DARK_PURPLE); }

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof LivingEntity && entity != mc.player) {
                    entity.setGlowingTag(true);
                    if (setSharedFlagMethod != null) { try { setSharedFlagMethod.invoke(entity, 6, true); } catch (Exception ignored) {} }
                    if (entity instanceof Player) sb.addPlayerToTeam(entity.getScoreboardName(), purpleTeam);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (isFreecam && OptiCore.isGloballyEnabled && setPositionMethod != null) {
            try { setPositionMethod.invoke(event.getCamera(), fcX, fcY, fcZ); } catch (Exception ignored) {}
            event.setYaw(fcYaw); event.setPitch(fcPitch);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!OptiCore.isGloballyEnabled || !OptiConfig.settings.itemEsp) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                String cleanName = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
                OptiConfig.MarketEntry entry = OptiConfig.catalog.marketPrices.get(cleanName);
                
                if (entry != null && entry.avgMin >= OptiConfig.settings.itemEspMinPrice) {
                    poseStack.pushPose();
                    poseStack.translate(entity.getX() - camPos.x, entity.getY() - camPos.y + 0.5D, entity.getZ() - camPos.z);
                    poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                    poseStack.scale(-0.025F, -0.025F, 0.025F);
                    String text = "§6[$" + (entry.avgMin / 1000) + "k] §f" + cleanName;
                    mc.font.drawInBatch(text, (float) (-mc.font.width(text) / 2), 0f, 0xFFFFFF, false, poseStack.last().pose(), bufferSource, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, 15728880);
                    poseStack.popPose();
                }
            }
        }
        bufferSource.endBatch();
    }

    public static void forceDisableGlow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Scoreboard sb = mc.level.getScoreboard();
        PlayerTeam purpleTeam = sb.getPlayerTeam("opti_purple");
        if (purpleTeam != null) sb.removePlayerTeam(purpleTeam);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity) {
                entity.setGlowingTag(false);
                if (setSharedFlagMethod != null) { try { setSharedFlagMethod.invoke(entity, 6, false); } catch (Exception ignored) {} }
            }
        }
    }
}
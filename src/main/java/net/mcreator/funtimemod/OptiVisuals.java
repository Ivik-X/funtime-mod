package net.mcreator.funtimemod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class OptiVisuals {

    private static final Set<BlockPos> espBlocks = ConcurrentHashMap.newKeySet();
    private static volatile boolean isScanning = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (OptiConfig.settings.fullbright) mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1000, 0, false, false, false));
        else if (mc.player.hasEffect(MobEffects.NIGHT_VISION) && mc.player.getEffect(MobEffects.NIGHT_VISION).getDuration() > 600) mc.player.removeEffect(MobEffects.NIGHT_VISION);

        if (OptiConfig.settings.noBadEffects) {
            if (mc.player.hasEffect(MobEffects.BLINDNESS)) mc.player.removeEffect(MobEffects.BLINDNESS);
            if (mc.player.hasEffect(MobEffects.DARKNESS)) mc.player.removeEffect(MobEffects.DARKNESS);
            if (mc.player.hasEffect(MobEffects.CONFUSION)) mc.player.removeEffect(MobEffects.CONFUSION);
        }

        // АСИНХРОННЫЙ СКАНЕР БЛОКОВ (Радиус 48 = 96x96x96 блоков, без лагов!)
        if (OptiConfig.settings.blockEspEnabled && !OptiConfig.settings.blockEspList.isEmpty() && !isScanning) {
            isScanning = true;
            BlockPos pPos = mc.player.blockPosition();
            List<String> targets = new ArrayList<>(OptiConfig.settings.blockEspList);
            net.minecraft.client.multiplayer.ClientLevel level = mc.level;
            
            new Thread(() -> {
                Set<BlockPos> found = new HashSet<>();
                int radius = 48;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            BlockPos check = pPos.offset(x, y, z);
                            String bName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(check).getBlock()).toString();
                            for (String t : targets) {
                                if (bName.contains(t.trim().toLowerCase())) { found.add(check); break; }
                            }
                        }
                    }
                }
                espBlocks.clear(); espBlocks.addAll(found);
                isScanning = false;
            }).start();
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (OptiConfig.settings.customViewModel && OptiCore.isGloballyEnabled) {
            event.getPoseStack().translate(OptiConfig.settings.vmX, OptiConfig.settings.vmY, OptiConfig.settings.vmZ);
            event.getPoseStack().mulPose(com.mojang.math.Axis.XP.rotationDegrees((float) OptiConfig.settings.vmPitch));
            event.getPoseStack().mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) OptiConfig.settings.vmYaw));
            event.getPoseStack().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) OptiConfig.settings.vmRoll));
            float s = (float) OptiConfig.settings.vmScale;
            event.getPoseStack().scale(s, s, s);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        if (OptiConfig.settings.armorHud) {
            int x = width / 2 + 15; int y = height - 55;
            for (ItemStack armor : mc.player.getInventory().armor) {
                if (!armor.isEmpty()) { g.renderItem(armor, x, y); g.renderItemDecorations(mc.font, armor, x, y); x += 20; }
            }
            ItemStack offhand = mc.player.getOffhandItem();
            if (!offhand.isEmpty()) { g.renderItem(offhand, x, y); g.renderItemDecorations(mc.font, offhand, x, y); }
        }

        if (OptiConfig.settings.radar) {
            float yaw = mc.player.getYRot();
            for (Player entity : mc.level.players()) {
                if (entity == mc.player) continue;
                double dx = entity.getX() - mc.player.getX(); double dz = entity.getZ() - mc.player.getZ();
                double angle = Math.toDegrees(Math.atan2(dz, dx)) - yaw - 90;
                double rad = Math.toRadians(angle);
                float cx = mc.getWindow().getGuiScaledWidth() / 2f + (float) (Math.cos(rad) * 60);
                float cy = mc.getWindow().getGuiScaledHeight() / 2f + (float) (Math.sin(rad) * 60);
                        
                event.getGuiGraphics().fill((int)cx - 2, (int)cy - 2, (int)cx + 2, (int)cy + 2, 0xFFFF0000);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!OptiCore.isGloballyEnabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        
        // РЕНДЕР ЛИНИЙ (Идеально работает сквозь стены)
        com.mojang.blaze3d.vertex.VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.guiOverlay());
        RenderSystem.disableDepthTest();

        if (OptiConfig.settings.blockEspEnabled && !espBlocks.isEmpty()) {
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            for (BlockPos pos : espBlocks) {
                if (OptiConfig.settings.wardenEsp && OptiWarden.trackedChests.containsKey(pos)) {
                    OptiWarden.WardenChest wc = OptiWarden.trackedChests.get(pos);
                    if (wc != null && wc.getRemaining() <= OptiConfig.settings.wardenEspTime && wc.getRemaining() > 0) continue;
                }
                pose.pushPose();
                drawBox(pose, consumer, pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, 0, 255, 255, 50); 
                pose.popPose();
            }
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        }
        
        if (OptiConfig.settings.playerEsp) {
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            for (net.minecraft.world.entity.player.Player entity : mc.level.players()) {
                if (entity != mc.player) {
                    pose.pushPose();
                    float w = entity.getBbWidth(); float h = entity.getBbHeight();
                    drawCustomBox(pose, consumer, entity.getX() - cam.x - w/2, entity.getY() - cam.y, entity.getZ() - cam.z - w/2, w, h, 255, 50, 50, 80);
                    pose.popPose();
                }
            }
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        }

        if (OptiConfig.settings.trajectories) {
            ItemStack held = mc.player.getMainHandItem();
            if (held.getItem() instanceof BowItem || held.getItem() instanceof EnderpearlItem || held.getItem() instanceof SnowballItem || held.getItem() instanceof ThrowablePotionItem) {
                Vec3 start = mc.player.getEyePosition().subtract(0, 0.1, 0);
                float yaw = mc.player.getYRot(); float pitch = mc.player.getXRot();
                double vx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
                double vy = -Math.sin(Math.toRadians(pitch));
                double vz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
                
                double length = Math.sqrt(vx*vx + vy*vy + vz*vz);
                vx /= length; vy /= length; vz /= length;
                
                // РЕАЛИСТИЧНАЯ ФИЗИКА ДЛЯ РАЗНЫХ ПРЕДМЕТОВ
                double mult = 1.5; double grav = 0.03;
                if (held.getItem() instanceof BowItem) { mult = 3.0; grav = 0.05; }
                else if (held.getItem() instanceof ThrowablePotionItem) { mult = 0.5; grav = 0.05; }
                
                vx *= mult; vy *= mult; vz *= mult;
                Vec3 pos = start; Vec3 vel = new Vec3(vx, vy, vz);
                BlockPos hitBlock = null;

                for (int i = 0; i < 150; i++) {
                    Vec3 nextPos = pos.add(vel);
                    HitResult hit = mc.level.clip(new ClipContext(pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                    if (hit.getType() == HitResult.Type.BLOCK) { hitBlock = ((BlockHitResult) hit).getBlockPos(); break; }
                    pos = nextPos;
                    vel = vel.scale(0.99).subtract(0, grav, 0); 
                }

                if (hitBlock != null) {
                    pose.pushPose();
                    drawBox(pose, consumer, hitBlock.getX() - cam.x, hitBlock.getY() - cam.y, hitBlock.getZ() - cam.z, 0, 100, 255, 100);
                    pose.popPose();
                }
            }
        }
        
        mc.renderBuffers().bufferSource().endBatch(net.minecraft.client.renderer.RenderType.guiOverlay());
        RenderSystem.enableDepthTest();
    }

    public static void drawBox(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer c, double x, double y, double z, int r, int g, int b, int a) {
        drawCustomBox(pose, c, x, y, z, 1.0f, 1.0f, r, g, b, a);
    }
    
    public static void drawCustomBox(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer c, double x, double y, double z, float w, float h, int r, int g, int b, int a) {
        org.joml.Matrix4f m = pose.last().pose(); 
        float pad = 0.02f; 
        float x1 = (float)x - pad, y1 = (float)y - pad, z1 = (float)z - pad;
        float x2 = (float)x + w + pad, y2 = (float)y + h + pad, z2 = (float)z + w + pad;
        
        // Рисуем 6 граней блока (внешние и внутренние для видимости изнутри)
        addQuad(m, c, x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, r, g, b, a); // Bottom
        addQuad(m, c, x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1, r, g, b, a); // Top
        addQuad(m, c, x1,y1,z1, x1,y2,z1, x2,y2,z1, x2,y1,z1, r, g, b, a); // North
        addQuad(m, c, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, r, g, b, a); // South
        addQuad(m, c, x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, r, g, b, a); // West
        addQuad(m, c, x2,y1,z1, x2,y2,z1, x2,y2,z2, x2,y1,z2, r, g, b, a); // East
    }
    
    public static void addQuad(org.joml.Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int b, int a) {
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, a); c.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        c.addVertex(m, x3, y3, z3).setColor(r, g, b, a); c.addVertex(m, x4, y4, z4).setColor(r, g, b, a);
        // Reversed order for backface
        c.addVertex(m, x4, y4, z4).setColor(r, g, b, a); c.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        c.addVertex(m, x2, y2, z2).setColor(r, g, b, a); c.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
    }
}
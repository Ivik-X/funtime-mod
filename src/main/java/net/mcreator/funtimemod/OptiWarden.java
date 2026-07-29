package net.mcreator.funtimemod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class OptiWarden {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})");
    public static final Map<BlockPos, WardenChest> trackedChests = new ConcurrentHashMap<>();

    public static class WardenChest {
        public int initialSeconds;
        public long syncTime;
        public boolean opened = false;
        public int getRemaining() { return initialSeconds - (int)((System.currentTimeMillis() - syncTime) / 1000); }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.hasCustomName()) {
                String cleanName = entity.getCustomName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
                Matcher m = TIME_PATTERN.matcher(cleanName);
                if (m.find()) {
                    int totalSecs = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
                    
                    // Сканируем блоки под голограммой, чтобы точно прикрепить куб к сундуку
                    BlockPos entityPos = entity.blockPosition();
                    BlockPos chestPos = entityPos.below(); 
                    for (int i = 0; i <= 3; i++) {
                        BlockPos check = entityPos.below(i);
                        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(check);
                        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) {
                            chestPos = check; break;
                        }
                    }
                    
                    WardenChest chest = trackedChests.get(chestPos);
                    if (chest == null || Math.abs(chest.getRemaining() - totalSecs) > 2) {
                        chest = new WardenChest();
                        chest.initialSeconds = totalSecs;
                        chest.syncTime = System.currentTimeMillis();
                        trackedChests.put(chestPos, chest);
                    }
                }
            }
        }

        for (Map.Entry<BlockPos, WardenChest> entry : trackedChests.entrySet()) {
            BlockPos pos = entry.getKey();
            int rem = entry.getValue().getRemaining();
            if (rem <= 0) {
                if (!entry.getValue().opened) {
                    if (OptiConfig.settings.wardenAutoOpen && mc.player.blockPosition().distSqr(pos) <= 36) { 
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                        OptiSniper.expectingWardenLoot = true; 
                    }
                    entry.getValue().opened = true;
                }
                if (rem < -2) {
                    trackedChests.remove(pos); 
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (!OptiCore.isGloballyEnabled || !OptiConfig.settings.wardenEsp || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        boolean hasVisibleChests = false;
        for (Map.Entry<BlockPos, WardenChest> entry : trackedChests.entrySet()) {
            int rem = entry.getValue().getRemaining();
            if (rem <= OptiConfig.settings.wardenEspTime && rem > 0) hasVisibleChests = true;
        }

        if (!hasVisibleChests) return;

        // 1. РИСУЕМ КРАСНЫЕ КУБЫ (Используем RenderType.guiOverlay(), он игнорирует стены и не требует света)
        com.mojang.blaze3d.vertex.VertexConsumer consumer = bufferSource.getBuffer(net.minecraft.client.renderer.RenderType.guiOverlay());
        
        for (Map.Entry<BlockPos, WardenChest> entry : trackedChests.entrySet()) {
            int rem = entry.getValue().getRemaining();
            if (rem <= OptiConfig.settings.wardenEspTime && rem > 0) {
                BlockPos pos = entry.getKey();
                double x = pos.getX() - camPos.x;
                double y = pos.getY() - camPos.y; 
                double z = pos.getZ() - camPos.z;

                // Размер куба - точно в блок сундука
                double minX = x; double minY = y; double minZ = z;
                double maxX = x + 1.0; double maxY = y + 1.0; double maxZ = z + 1.0;

                poseStack.pushPose();
                OptiVisuals.drawBox(poseStack, consumer, x, y, z, 255, 0, 0, 100);
                poseStack.popPose();
            }
        }
        

        
        // Вместо этого сканируем радиус вокруг игрока на предмет сундуков
        BlockPos pPos = mc.player.blockPosition();
        int r = 30;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos check = pPos.offset(x, y, z);
                    if (trackedChests.containsKey(check)) continue;
                    net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(check);
                    if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) {
                        boolean nearTracked = false;
                        for (BlockPos tPos : trackedChests.keySet()) {
                            if (tPos.distSqr(check) < 1600) { nearTracked = true; break; }
                        }
                        if (nearTracked) {
                            poseStack.pushPose();
                            OptiVisuals.drawBox(poseStack, consumer, check.getX() - camPos.x, check.getY() - camPos.y, check.getZ() - camPos.z, 200, 0, 255, 100);
                            poseStack.popPose();
                        }
                    }
                }
            }
        }
        
        // Принудительно отрисовываем кубы в мир
        bufferSource.endBatch(net.minecraft.client.renderer.RenderType.guiOverlay());

        // 2. РИСУЕМ ТАЙМЕР НАД СУНДУКОМ (ЧЕРЕЗ СТЕНЫ)
        for (Map.Entry<BlockPos, WardenChest> entry : trackedChests.entrySet()) {
            int rem = entry.getValue().getRemaining();
            if (rem <= OptiConfig.settings.wardenEspTime && rem > 0) {
                BlockPos pos = entry.getKey();
                poseStack.pushPose();
                poseStack.translate(pos.getX() + 0.5 - camPos.x, pos.getY() + 1.2 - camPos.y, pos.getZ() + 0.5 - camPos.z);
                poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                float scale = (float) OptiConfig.settings.wardenTextScale;
                poseStack.scale(-scale, -scale, scale); 
                String timeText = "§f" + rem + "s";
                mc.font.drawInBatch(timeText, (float) (-mc.font.width(timeText) / 2), 0, 0xFFFFFF, false, poseStack.last().pose(), bufferSource, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, 15728880);
                poseStack.popPose();
            }
        }
        bufferSource.endBatch();
    }



}
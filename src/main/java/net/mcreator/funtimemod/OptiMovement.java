package net.mcreator.funtimemod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Method;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class OptiMovement {

    public static boolean isFreecam = false;
    private static double fcX, fcY, fcZ;
    private static float fcYaw, fcPitch;

    private static Method setPosMethod;

    static {
        try { setPosMethod = net.minecraft.client.Camera.class.getDeclaredMethod("setPosition", double.class, double.class, double.class); setPosMethod.setAccessible(true); } 
        catch (Exception e) { try { setPosMethod = net.minecraft.client.Camera.class.getDeclaredMethod("m_90569_", double.class, double.class, double.class); setPosMethod.setAccessible(true); } catch (Exception ignored) {} }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (event.getKey() == GLFW.GLFW_KEY_C && event.getAction() == GLFW.GLFW_PRESS && mc.screen == null) {
            isFreecam = !isFreecam;
            if (isFreecam) {
                fcX = mc.player.getX(); fcY = mc.player.getEyeY(); fcZ = mc.player.getZ();
                fcYaw = mc.player.getYRot(); fcPitch = mc.player.getXRot();
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[OptiItem] FreeCam ВКЛ"), true);
            } else {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[OptiItem] FreeCam ВЫКЛ"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        
        // IDEAL INV MOVE
        if (OptiConfig.settings.inventoryMove && mc.screen instanceof AbstractContainerScreen<?> && !(mc.screen instanceof ChatScreen)) {
            long w = mc.getWindow().getWindow();
            boolean isUp = InputConstants.isKeyDown(w, mc.options.keyUp.getKey().getValue());
            boolean isDown = InputConstants.isKeyDown(w, mc.options.keyDown.getKey().getValue());
            boolean isLeft = InputConstants.isKeyDown(w, mc.options.keyLeft.getKey().getValue());
            boolean isRight = InputConstants.isKeyDown(w, mc.options.keyRight.getKey().getValue());
            boolean isJump = InputConstants.isKeyDown(w, mc.options.keyJump.getKey().getValue());

            event.getInput().forwardImpulse = (isUp ? 1.0f : 0.0f) - (isDown ? 1.0f : 0.0f);
            event.getInput().leftImpulse = (isLeft ? 1.0f : 0.0f) - (isRight ? 1.0f : 0.0f);
            mc.player.setJumping(isJump);
        }
        
        // Во время FreeCam мы ПОЛНОСТЬЮ глушим инпуты к реальному игроку (Без удаленных переменных)
        if (isFreecam) {
            event.getInput().forwardImpulse = 0; 
            event.getInput().leftImpulse = 0;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long window = mc.getWindow().getWindow();

        if (isFreecam) {
            if (mc.screen == null) {
                fcYaw = mc.player.getYRot(); fcPitch = mc.player.getXRot();
                double speed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) ? 2.5 : 1.0;
                Vec3 forward = Vec3.directionFromRotation(fcPitch, fcYaw).normalize().scale(speed);
                Vec3 right = Vec3.directionFromRotation(0, fcYaw + 90).normalize().scale(speed);

                if (InputConstants.isKeyDown(window, mc.options.keyUp.getKey().getValue())) { fcX += forward.x; fcY += forward.y; fcZ += forward.z; }
                if (InputConstants.isKeyDown(window, mc.options.keyDown.getKey().getValue())) { fcX -= forward.x; fcY -= forward.y; fcZ -= forward.z; }
                if (InputConstants.isKeyDown(window, mc.options.keyRight.getKey().getValue())) { fcX += right.x; fcZ += right.z; }
                if (InputConstants.isKeyDown(window, mc.options.keyLeft.getKey().getValue())) { fcX -= right.x; fcZ -= right.z; }
                if (InputConstants.isKeyDown(window, mc.options.keyJump.getKey().getValue())) fcY += speed;
                if (InputConstants.isKeyDown(window, mc.options.keyShift.getKey().getValue())) fcY -= speed;
            }
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (isFreecam && OptiCore.isGloballyEnabled && setPosMethod != null) {
            try {
                setPosMethod.invoke(event.getCamera(), fcX, fcY, fcZ);
                event.setYaw(fcYaw); event.setPitch(fcPitch);
            } catch (Exception ignored) {}
        }
    }
}
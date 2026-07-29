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
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class OptiCombat {

    private static int lastEmergencySwapSlot = -1;
    private static boolean isEmergencyActive = false;
    private static int clickDelay = 0;
    
    // Для авто-возврата после перла
    private static int previousSlot = -1;
    private static int pearlReturnDelay = 0;

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        
        // MIDDLE-CLICK PEARL (Колесико мыши = 2)
        if (event.getButton() == 2 && event.getAction() == GLFW.GLFW_PRESS) {
            int pearlSlot = -1;
            // Ищем перл только в хотбаре (0-8)
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).getItem() == Items.ENDER_PEARL) {
                    pearlSlot = i; break;
                }
            }
            if (pearlSlot != -1) {
                previousSlot = mc.player.getInventory().selected;
                mc.player.getInventory().selected = pearlSlot;
                
                // Симулируем нажатие ПКМ для броска
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                
                // Ставим таймер на возврат слота (чтобы сервер успел засчитать бросок)
                pearlReturnDelay = 2; 
                event.setCanceled(true); // Отменяем ванильное действие колесика
            } else {
                mc.player.displayClientMessage(Component.literal("§c[OptiItem] Перл не найден в хотбаре!"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || event.getAction() != GLFW.GLFW_PRESS || Screen.hasControlDown()) return;
        
        if (event.getKey() == GLFW.GLFW_KEY_G) {
            int targetSlot = findItemSlot(OptiConfig.settings.gSwapItem);
            if (targetSlot != -1) { swapToOffhand(targetSlot); mc.player.displayClientMessage(Component.literal("§a[OptiItem] Fast Swap."), true); }
        }
        
        if (event.getKey() == GLFW.GLFW_KEY_X) {
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
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Возврат слота после броска перла
        if (pearlReturnDelay > 0) {
            pearlReturnDelay--;
            if (pearlReturnDelay == 0 && previousSlot != -1) {
                mc.player.getInventory().selected = previousSlot;
                previousSlot = -1;
            }
        }

        // AIM ASSIST (Магнит по горизонтали) с проверкой дистанции
        if (OptiConfig.settings.aimAssist && mc.options.keyAttack.isDown()) {
            if (mc.hitResult == null || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
                Entity bestTarget = null;
                double minDiff = OptiConfig.settings.aimAssistFov;
                Vec3 eyePos = mc.player.getEyePosition();
                
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (e instanceof Player && e != mc.player) {
                        // Магнит работает только если враг находится на дистанции удара
                        if (e.distanceTo(mc.player) > OptiConfig.settings.aimAssistRange) continue; 
                        
                        double dx = e.getX() - eyePos.x;
                        double dz = e.getZ() - eyePos.z;
                        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                        float diff = net.minecraft.util.Mth.wrapDegrees(targetYaw - mc.player.getYRot());
                        
                        if (Math.abs(diff) < minDiff) {
                            minDiff = Math.abs(diff);
                            bestTarget = e;
                        }
                    }
                }
                
                // Доводим прицел горизонтально до центра врага
                if (bestTarget != null) {
                    double dx = bestTarget.getX() - eyePos.x;
                    double dz = bestTarget.getZ() - eyePos.z;
                    float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                    mc.player.setYRot(targetYaw);
                }
            }
        }

        // AUTO TOTEM И СВАП ЗДОРОВЬЯ
        if (clickDelay > 0) { clickDelay--; return; }
        boolean hasTotem = (mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING);

        if (OptiConfig.settings.autoTotem && mc.player.getOffhandItem().isEmpty() && !hasTotem) {
            int tSlot = findItemSlot("totem");
            if (tSlot != -1) { swapToOffhand(tSlot); clickDelay = 2; return; }
        }

        if (OptiConfig.settings.healthSwap) {
            if (mc.player.getHealth() <= OptiConfig.settings.healthThreshold) {
                if (!hasTotem) {
                    int tSlot = findItemSlot("totem");
                    if (tSlot != -1) { lastEmergencySwapSlot = tSlot; swapToOffhand(tSlot); isEmergencyActive = true; clickDelay = 2; }
                }
            } else if (isEmergencyActive && hasTotem && lastEmergencySwapSlot != -1) {
                swapToOffhand(lastEmergencySwapSlot); isEmergencyActive = false; clickDelay = 2;
            }
        }
    }

    public static int findItemSlot(String nameOrId) {
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

    public static void swapToOffhand(int slotId) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slotId, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, 45, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slotId, 0, ClickType.PICKUP, mc.player);
    }
}
package net.mcreator.funtimemod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class OptiSniper {

    private static boolean isBotActive = false;
    private static int afkWalkTimer = 0;
    private static int relistTimer = 0;

    private static int activeSellSlots = 0, state = 0, timeoutTimer = 0, refreshClickCount = 0;
    private static String lastSlot44State = "", expectedId = "", expectedCleanName = "";
    private static long expectedPrice = -1;
    
    private static long lastCtrlKTime = 0;
    private static boolean isQuickBuyPending = false;
    private static int quickBuyWaitTimer = 0;
    
    private static boolean isInitialPriceSync = false;
    private static int initialPriceSyncDoneCount = 0, currentSyncTarget = 50;
    private static long lastFullAnalysisTime = 0;
    
    private static Queue<OptiConfig.AutoSellJob> autoSellQueue = new LinkedList<>();
    public static final Queue<Integer> smartLootQueue = new LinkedList<>();
    public static final Map<String, ItemStack> seenItemsCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static int clickDelay = 0;

    private static Screen lastScreen = null;
    private static int autoStealTimer = 0;
    
    public static boolean expectingWardenLoot = false;
    private static boolean isAutoStealing = false;

    @SubscribeEvent
    public static void onInput(InputEvent.Key event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        
        if (event.getAction() == GLFW.GLFW_PRESS) {
            
            if (event.getKey() == GLFW.GLFW_KEY_V) {
                if (Screen.hasControlDown()) {
                    OptiConfig.settings.autoStealOnOpen = !OptiConfig.settings.autoStealOnOpen;
                    OptiConfig.saveAll();
                    sendMessage(OptiConfig.settings.autoStealOnOpen ? "§a[OptiItem] Авто-Стиллер при открытии: ВКЛ" : "§c[OptiItem] Авто-Стиллер при открытии: ВЫКЛ");
                } else if (mc.screen instanceof AbstractContainerScreen<?> container && !(container instanceof InventoryScreen)) {
                    triggerSmartLoot(container);
                    isAutoStealing = true;
                }
            }
            
            if (event.getKey() == GLFW.GLFW_KEY_K && Screen.hasControlDown() && mc.screen instanceof AbstractContainerScreen<?> container) {
                if (container.getTitle().getString().contains("Поиск")) {
                    long now = System.currentTimeMillis();
                    if (now - lastCtrlKTime < 1500) { 
                        if (container.getMenu().slots.size() > 49 && BuiltInRegistries.ITEM.getKey(container.getMenu().slots.get(49).getItem().getItem()).toString().equals("minecraft:nether_star")) {
                            clickSlot(container, 49);
                            isQuickBuyPending = true;
                            quickBuyWaitTimer = 10; 
                            sendMessage("§e[OptiItem] Обновление лотов...");
                        }
                    } else {
                        executeQuickBuy(container);
                    }
                    lastCtrlKTime = now;
                }
            }

            // ПЕРЕВОД В КЛАН ТЕПЕРЬ НА CTRL+I
            if (event.getKey() == GLFW.GLFW_KEY_I && Screen.hasControlDown()) {
                long coins = getCurrentCoins();
                if (coins > 0) {
                    if (mc.getConnection() != null) {
                        mc.getConnection().sendCommand("clan invest " + coins);
                        sendMessage("§a[OptiItem] В клан переведено: §e" + coins + "$");
                    }
                } else {
                    sendMessage("§c[OptiItem] Не удалось считать баланс!");
                }
            }

            if (event.getKey() == GLFW.GLFW_KEY_J && Screen.hasControlDown() && mc.screen instanceof AbstractContainerScreen<?>) {
                isBotActive = !isBotActive;
                if (isBotActive) {
                    autoSellQueue.clear(); seenItemsCache.clear();
                    currentSyncTarget = (System.currentTimeMillis() - lastFullAnalysisTime < 15 * 60 * 1000) ? 10 : 50;
                    isInitialPriceSync = true; initialPriceSyncDoneCount = 0; state = 1; refreshClickCount = 0;
                    relistTimer = 1300; afkWalkTimer = 600; 
                    sendMessage("§a[OptiItem] Анализ запущен (" + currentSyncTarget + " стр).");
                } else {
                    state = 0; OptiConfig.saveAll(); sendMessage("§c[OptiItem] Процесс остановлен. Кэш сохранен.");
                }
            }
        }
    }

    public static void executeQuickBuy(AbstractContainerScreen<?> container) {
        int bestSlot = -1;
        long lowestPricePerUnit = Long.MAX_VALUE;
        for (int i = 0; i < 45 && i < container.getMenu().slots.size(); i++) {
            Slot slot = container.getMenu().slots.get(i);
            if (slot.getItem().isEmpty()) continue;
            long price = parsePrice(slot.getItem());
            if (price > 0) {
                long perUnit = price / slot.getItem().getCount(); // ВЫЧИСЛЕНИЕ ЗА 1 ШТ
                if (perUnit < lowestPricePerUnit) { lowestPricePerUnit = perUnit; bestSlot = i; }
            }
        }
        if (bestSlot != -1) {
            Minecraft.getInstance().gameMode.handleInventoryMouseClick(container.getMenu().containerId, bestSlot, 0, ClickType.QUICK_MOVE, Minecraft.getInstance().player);
            sendMessage("§a[OptiItem] Моментальная покупка (Shift-Click) выполнена!");
        } else {
            sendMessage("§c[OptiItem] Лотов не найдено.");
        }
    }

    public static void triggerSmartLoot(AbstractContainerScreen<?> container) {
        smartLootQueue.clear();
        int maxSlot = container.getMenu().slots.size() - 36;
        for (int i = 0; i < maxSlot; i++) {
            ItemStack stack = container.getMenu().slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            
            if (OptiConfig.settings.smartLootMinPrice == 0) {
                smartLootQueue.add(i);
                continue;
            }
            
            String cleanName = cleanDisplayName(stack);
            OptiConfig.MarketEntry entry = OptiConfig.catalog.marketPrices.get(cleanName);
            if ((entry != null && entry.avgMin >= OptiConfig.settings.smartLootMinPrice) || cleanName.contains("Сфера") || cleanName.contains("Тотем") || stack.getItem().toString().contains("shulker_box")) {
                smartLootQueue.add(i);
            }
        }
        if (!smartLootQueue.isEmpty()) sendMessage("§a[OptiItem] Лутаем сундук...");
    }

    @SubscribeEvent
    public static void onSystemChat(ClientChatReceivedEvent event) {
        if (!OptiCore.isGloballyEnabled || !isBotActive) return;
        String msg = event.getMessage().getString().replaceAll("§[0-9a-fk-or]", "").trim();
        if (msg.contains("Вы не можете больше выставлять товары")) activeSellSlots = 5; 
        else if (msg.contains("У вас купили") || msg.contains("У вас приобрели")) if (activeSellSlots > 0) activeSellSlots--;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptiCore.isGloballyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (isQuickBuyPending && mc.screen instanceof AbstractContainerScreen<?> containerScreen) {
            quickBuyWaitTimer--;
            if (quickBuyWaitTimer <= 0) {
                isQuickBuyPending = false;
                executeQuickBuy(containerScreen);
            }
            return; 
        } else {
            isQuickBuyPending = false;
        }

        if (mc.screen instanceof AbstractContainerScreen<?> container) {
            if (container instanceof InventoryScreen) {
                lastScreen = mc.screen;
                return;
            }

            if (lastScreen != mc.screen) {
                if (OptiConfig.settings.autoStealOnOpen || expectingWardenLoot) {
                    autoStealTimer = expectingWardenLoot ? 40 : 5; 
                }
            }
            
            if (autoStealTimer > 0) {
                if (expectingWardenLoot) {
                    boolean hasItems = false;
                    for (int i = 0; i < container.getMenu().slots.size() - 36; i++) {
                        if (!container.getMenu().slots.get(i).getItem().isEmpty()) { hasItems = true; break; }
                    }
                    if (hasItems) autoStealTimer = 1;
                    else autoStealTimer--;
                } else {
                    autoStealTimer--;
                }
                if (autoStealTimer == 0) {
                    triggerSmartLoot(container);
                    isAutoStealing = true;
                }
            }
            
            if (isAutoStealing) {
                if (!smartLootQueue.isEmpty()) {
                    if (clickDelay > 0) { clickDelay--; return; }
                    mc.gameMode.handleInventoryMouseClick(container.getMenu().containerId, smartLootQueue.poll(), 0, ClickType.QUICK_MOVE, mc.player);
                    clickDelay = 2; 
                    return;
                } else if (autoStealTimer <= 0) {
                    if (expectingWardenLoot) {
                        mc.player.closeContainer();
                    }
                    expectingWardenLoot = false;
                    isAutoStealing = false;
                }
            }
        } else {
            autoStealTimer = 0; smartLootQueue.clear(); expectingWardenLoot = false; isAutoStealing = false;
        }
        lastScreen = mc.screen;

        if (!isBotActive || state == 0) return;

        if (state == 5) {
            timeoutTimer--;
            if (timeoutTimer == 30) {
                OptiConfig.AutoSellJob job = autoSellQueue.peek();
                if (job != null) {
                    boolean found = false;
                    for (int i = 0; i < 36; i++) {
                        if (cleanDisplayName(mc.player.getInventory().getItem(i)).equals(job.itemName)) {
                            if (i >= 9) mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, 0, ClickType.SWAP, mc.player);
                            else mc.player.getInventory().selected = i;
                            found = true; break;
                        }
                    }
                    if (found && mc.getConnection() != null) { mc.getConnection().sendCommand("ah sell " + job.sellPrice); activeSellSlots++; }
                }
            } else if (timeoutTimer <= 0) {
                autoSellQueue.poll();
                if (mc.getConnection() != null) mc.getConnection().sendCommand("ah");
                state = 4; timeoutTimer = 40;
            }
            return;
        }

        if (state == 6) { 
            timeoutTimer--;
            if (timeoutTimer == 35) clickSlot(mc.screen instanceof AbstractContainerScreen<?> s ? s : null, 46);
            else if (timeoutTimer == 20) clickSlot(mc.screen instanceof AbstractContainerScreen<?> s ? s : null, 52);
            else if (timeoutTimer == 5) clickSlot(mc.screen instanceof AbstractContainerScreen<?> s ? s : null, 46);
            else if (timeoutTimer <= 0) { mc.player.closeContainer(); state = 4; timeoutTimer = 20; }
            return;
        }

        if (!(mc.screen instanceof AbstractContainerScreen<?> containerScreen)) {
            if (state != 4 && state != 5 && state != 6) { 
                afkWalkTimer--;
                if (afkWalkTimer <= 0 && OptiConfig.settings.autoRelist) {
                    mc.player.setDeltaMovement(new net.minecraft.world.phys.Vec3(Math.random() * 0.4 - 0.2, mc.player.getDeltaMovement().y, Math.random() * 0.4 - 0.2));
                    afkWalkTimer = 600;
                }
                
                if (OptiConfig.settings.autoSell && activeSellSlots < 5 && autoSellQueue.isEmpty()) {
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = mc.player.getInventory().getItem(i);
                        if (!stack.isEmpty()) {
                            String name = cleanDisplayName(stack);
                            if (OptiConfig.settings.autoSellItems.contains(name)) {
                                OptiConfig.MarketEntry entry = OptiConfig.catalog.marketPrices.get(name);
                                if (entry != null && entry.avgMin > 0) {
                                    long sellPrice = (long)(entry.avgMin * OptiConfig.settings.autoFarmSellMultiplier);
                                    autoSellQueue.add(new OptiConfig.AutoSellJob(name, sellPrice));
                                    break; 
                                }
                            }
                        }
                    }
                    if (!autoSellQueue.isEmpty()) {
                        if (mc.getConnection() != null) mc.getConnection().sendCommand("ah");
                        state = 5; timeoutTimer = 40; return;
                    }
                }

                relistTimer--;
                if (relistTimer <= 0 && OptiConfig.settings.autoRelist && activeSellSlots > 0) {
                    if (mc.getConnection() != null) mc.getConnection().sendCommand("ah");
                    state = 6; timeoutTimer = 50; relistTimer = 1300; 
                }
            } 
            return;
        }

        if (state == 1) {
            if (containerScreen.getMenu().slots.size() <= 49 || !BuiltInRegistries.ITEM.getKey(containerScreen.getMenu().slots.get(49).getItem().getItem()).toString().equals("minecraft:nether_star")) return; 

            if (OptiConfig.settings.autoSell && !autoSellQueue.isEmpty() && activeSellSlots < 5 && !isInitialPriceSync) {
                mc.player.closeContainer(); state = 5; timeoutTimer = 40; return;
            }

            long currentCoins = getCurrentCoins();
            int maxSlot = Math.min(45, containerScreen.getMenu().slots.size());
            List<ItemStack> pageItems = new ArrayList<>();
            for (int i = 0; i < maxSlot; i++) if (!containerScreen.getMenu().slots.get(i).getItem().isEmpty()) pageItems.add(containerScreen.getMenu().slots.get(i).getItem());
            
            updateMarketPrices(pageItems);

            if (isInitialPriceSync) {
                initialPriceSyncDoneCount++;
                if (initialPriceSyncDoneCount >= currentSyncTarget) { isInitialPriceSync = false; if (currentSyncTarget >= 50) lastFullAnalysisTime = System.currentTimeMillis(); }
                lastSlot44State = getSlotState(containerScreen.getMenu().slots.get(44));
                clickSlot(containerScreen, 49); state = 3; refreshClickCount = 1; timeoutTimer = 10; return;
            }

            double bestMargin = -1; int bestSlot = -1;
            for (int i = 0; i < maxSlot; i++) {
                Slot slot = containerScreen.getMenu().slots.get(i);
                if (slot.getItem().isEmpty()) continue;
                ItemStack stack = slot.getItem();
                long price = parsePrice(stack);
                
                if (price <= 0 || (currentCoins > 0 && price > currentCoins) || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains("shulker_box")) continue;

                String cleanName = cleanDisplayName(stack);
                long perUnit = price / stack.getCount();
                OptiConfig.MarketEntry mEntry = OptiConfig.catalog.marketPrices.get(cleanName);

                if (OptiConfig.settings.searchMode.equals("auto_farm")) {
                    if (mEntry == null || mEntry.avgMin <= 0 || price < OptiConfig.settings.autoFarmMinPrice) continue;
                    double maxAllowed = mEntry.avgMin * OptiConfig.settings.autoFarmBuyMultiplier;
                    if (perUnit <= maxAllowed) { double margin = (mEntry.avgMin - perUnit) / (double)mEntry.avgMin; if (margin > bestMargin) { bestMargin = margin; bestSlot = i; } }
                } 
                else if (OptiConfig.settings.searchMode.equals("targeted")) {
                    for (OptiConfig.BuyTarget target : OptiConfig.settings.targetedItems) {
                        if (cleanName.equals(target.name) && perUnit <= target.maxPrice) { bestMargin = 1.0; bestSlot = i; break; }
                    }
                }
            }

            if (bestSlot != -1) {
                ItemStack targetItem = containerScreen.getMenu().slots.get(bestSlot).getItem();
                expectedId = BuiltInRegistries.ITEM.getKey(targetItem.getItem()).toString();
                expectedCleanName = cleanDisplayName(targetItem);
                expectedPrice = parsePrice(targetItem);
                clickSlot(containerScreen, bestSlot); state = 2; timeoutTimer = 40; 
            } else {
                lastSlot44State = getSlotState(containerScreen.getMenu().slots.get(44));
                clickSlot(containerScreen, 49); state = 3; refreshClickCount = 1; timeoutTimer = 10; 
            }
        } 
        else if (state == 2) {
            timeoutTimer--; boolean verified = false;
            if (containerScreen.getMenu().slots.size() > 13) {
                ItemStack stack13 = containerScreen.getMenu().slots.get(13).getItem();
                if (!stack13.isEmpty()) {
                    if (BuiltInRegistries.ITEM.getKey(stack13.getItem()).toString().equals(expectedId) && parsePrice(stack13) == expectedPrice) {
                        verified = true; clickSlot(containerScreen, 0); 
                        
                        if (OptiConfig.settings.autoSell && expectedPrice > 0) {
                            OptiConfig.MarketEntry mEntry = OptiConfig.catalog.marketPrices.get(expectedCleanName);
                            if (mEntry != null && mEntry.avgMin > 0) {
                                long targetSellPrice = (long)(mEntry.avgMin * OptiConfig.settings.autoFarmSellMultiplier);
                                if (targetSellPrice < expectedPrice * 1.08) targetSellPrice = (long)(expectedPrice * 1.08);
                                autoSellQueue.add(new OptiConfig.AutoSellJob(expectedCleanName, targetSellPrice));
                            }
                        }
                        state = 4; timeoutTimer = 15;
                    }
                }
            }
            if (!verified && timeoutTimer <= 0) { if (containerScreen.getMenu().slots.size() > 8) clickSlot(containerScreen, 8); state = 4; timeoutTimer = 15; }
        } 
        else if (state == 3) {
            if (containerScreen.getMenu().slots.size() <= 49 || !BuiltInRegistries.ITEM.getKey(containerScreen.getMenu().slots.get(49).getItem().getItem()).toString().equals("minecraft:nether_star")) return;
            timeoutTimer--;
            if (!getSlotState(containerScreen.getMenu().slots.get(44)).equals(lastSlot44State)) { refreshClickCount = 0; state = 4; timeoutTimer = 10; return; }
            if (timeoutTimer <= 0) { if (refreshClickCount >= 10) { isBotActive = false; state = 0; return; } refreshClickCount++; clickSlot(containerScreen, 49); timeoutTimer = 10; }
        } 
        else if (state == 4) { timeoutTimer--; if (timeoutTimer <= 0) state = 1; }
    }

    private static void updateMarketPrices(List<ItemStack> pageItems) {
        Map<String, Long> pageMins = new HashMap<>();
        for (ItemStack item : pageItems) {
            String name = cleanDisplayName(item); long price = parsePrice(item);
            if (price > 0) { long perUnit = price / item.getCount(); if (!pageMins.containsKey(name) || perUnit < pageMins.get(name)) pageMins.put(name, perUnit); }
            seenItemsCache.put(name, item.copy());
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()) != null) {
                OptiConfig.catalog.cachedItemIds.put(name, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
            }
        }
        for (Map.Entry<String, Long> entry : pageMins.entrySet()) {
            OptiConfig.MarketEntry mEntry = OptiConfig.catalog.marketPrices.computeIfAbsent(entry.getKey(), k -> new OptiConfig.MarketEntry());
            if (mEntry.avgMin > 0 && entry.getValue() > mEntry.avgMin * 1.5) continue;
            mEntry.recentMins.add(entry.getValue()); if (mEntry.recentMins.size() > 15) mEntry.recentMins.remove(0);
            List<Long> sorted = new ArrayList<>(mEntry.recentMins); Collections.sort(sorted);
            mEntry.avgMin = sorted.get(sorted.size() / 2); 
        }
    }

    private static long getCurrentCoins() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return -1;
        for (net.minecraft.world.scores.ScoreHolder holder : mc.level.getScoreboard().getTrackedPlayers()) {
            net.minecraft.world.scores.PlayerTeam team = mc.level.getScoreboard().getPlayersTeam(holder.getScoreboardName());
            if (team != null) {
                String line = (team.getPlayerPrefix().getString() + holder.getScoreboardName() + team.getPlayerSuffix().getString()).replaceAll("§[0-9a-fk-or]", "");
                if (line.contains("Монет")) { String num = line.replaceAll("[^0-9]", ""); if (!num.isEmpty()) try { return Long.parseLong(num); } catch (Exception ignored) {} }
            }
        }
        return -1;
    }

    public static String cleanDisplayName(ItemStack stack) { return stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim(); }
    
    public static long parsePrice(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        for (Component comp : stack.getTooltipLines(Item.TooltipContext.EMPTY, mc.player, TooltipFlag.NORMAL)) {
            String line = comp.getString().replaceAll("§[0-9a-fk-or]", ""); 
            if (line.contains("Цен") || line.contains("Цeн") || line.contains("$")) { 
                String numPart = line.replaceAll("[^0-9]", ""); 
                if (!numPart.isEmpty()) try { return Long.parseLong(numPart); } catch (Exception ignored) {} 
            }
        }
        return -1;
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, int slotId) { Minecraft mc = Minecraft.getInstance(); mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotId, 0, ClickType.PICKUP, mc.player); }
    private static String getSlotState(Slot slot) { return slot.getItem().isEmpty() ? "empty" : slot.getItem().getCount() + "_" + slot.getItem().getComponents().hashCode(); }
    private static void sendMessage(String text) { if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.literal(text.replace("[OptiItem]", "Система")), true); }
}
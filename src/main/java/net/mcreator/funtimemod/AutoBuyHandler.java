package net.mcreator.funtime_mod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class AutoBuyHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_FLAT = new GsonBuilder().create(); // Для дампа в одну строку
    
    // ==========================================
    // СТРУКТУРЫ ДАННЫХ КОНФИГА
    // ==========================================
    
    public static class BuyTarget {
        public String target_id = "";
        public String name_contains = "";
        public long max_price_per_item = -1;
        public int max_items = 0; 
        public List<String> required_strings = new ArrayList<>();
        public transient int itemsBought = 0; 
    }

    public static class BotConfig {
        public String searchMode = "targeted"; 
        public boolean autoSell = false;
        public int initialPriceSyncCount = 50; 
        
        public double autoCheapMultiplier = 0.8;
        public double autoSellMultiplier = 1.2;
        public List<String> autoCheapItems = new ArrayList<>();
        
        public double autoFarmBuyMultiplier = 0.82;
        public double autoFarmSellMultiplier = 0.98;
        public long autoFarmMinPrice = 100000;
        public int autoFarmMinSeenCount = 20;
        public int autoFarmMinScansWithItem = 10;
        public int autoFarmMinNewLotsCount = 5;
        public double autoFarmMinFrequencyPerScan = 0.25;

        public List<BuyTarget> targets = new ArrayList<>();
    }

    public static class MarketEntry {
        public long avgMin;
        public List<Long> recentMins = new ArrayList<>();
    }

    public static class FrequencyEntry {
        public int totalSeenCount = 0;
        public int totalUnitsSeen = 0;
        public int newLotsCount = 0;
        public int scansWithItem = 0;
        public long firstSeen = System.currentTimeMillis();
        public long lastSeen = System.currentTimeMillis();
        public double frequencyPerScan = 0.0;
        public double newLotsPerScan = 0.0;
        public double newLotsPerHour = 0.0;
    }

    public static class GlobalStats {
        public int totalScans = 0;
        public long firstScanTimestamp = System.currentTimeMillis();
        public long lastScanTimestamp = System.currentTimeMillis();
    }

    public static class CatalogData {
        public Map<String, MarketEntry> marketPrices = new HashMap<>();
        public Map<String, FrequencyEntry> listingFrequency = new HashMap<>();
        public GlobalStats globalStats = new GlobalStats();
    }
    
    public static class AutoSellJob {
        public String itemName;
        public long sellPrice;
        public AutoSellJob(String name, long price) { this.itemName = name; this.sellPrice = price; }
    }

    private static class Deal {
        int slot;
        long price;
        double margin; 
        BuyTarget targetRule;
        boolean isAutoFarm;
        boolean isAutoCheap;
        
        public Deal(int slot, long price, double margin, BuyTarget rule, boolean isAutoFarm, boolean isAutoCheap) {
            this.slot = slot; this.price = price; this.margin = margin;
            this.targetRule = rule; this.isAutoFarm = isAutoFarm; this.isAutoCheap = isAutoCheap;
        }
    }

    // ==========================================
    // ПЕРЕМЕННЫЕ СОСТОЯНИЯ
    // ==========================================

    private static BotConfig config = new BotConfig();
    private static CatalogData catalog = new CatalogData();
    private static Queue<AutoSellJob> autoSellQueue = new LinkedList<>();
    private static final Set<String> seenLotsCache = new LinkedHashSet<>();
    private static final Set<String> seenEnchantedLots = new LinkedHashSet<>(); // Кэш для дампа чар
    
    private static boolean isBotActive = false;
    private static int activeSellSlots = 0; 
    
    private static int state = 0; 
    private static int timeoutTimer = 0;
    private static int refreshClickCount = 0; 
    private static String lastSlot44State = "";
    
    private static boolean isInitialPriceSync = false;
    private static int initialPriceSyncDoneCount = 0;

    private static Deal currentDeal = null;
    private static String expectedId = "";
    private static String expectedCleanName = "";
    private static long expectedPrice = -1;

    private static Vec3 startPos = null;
    private static int antiAfkTimer = 2400; 

    @SubscribeEvent
    public static void onSystemChat(ClientChatReceivedEvent event) {
        if (!isBotActive) return;
        String msg = event.getMessage().getString().replaceAll("§[0-9a-fk-or]", "").trim();
        
        if (msg.contains("Вы не можете больше выставлять товары на аукцион")) {
            activeSellSlots = 5; // <--- Изменить здесь
            LOGGER.warn("[AutoSell] Достигнут лимит (5 слотов). Ждем продаж.");
        } else if (msg.contains("У вас купили") || msg.contains("У вас приобрели")) {
            if (activeSellSlots > 0) activeSellSlots--;
            LOGGER.info("[AutoSell] Кто-то купил наш предмет! Освобожден слот. Занято: {}/5", activeSellSlots);
        }
    }

    @SubscribeEvent
    public static void onKeyPress(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() == GLFW.GLFW_KEY_J && Screen.hasControlDown()) {
            if (event.getScreen() instanceof AbstractContainerScreen<?>) {
                isBotActive = !isBotActive;
                if (isBotActive) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) startPos = mc.player.position();
                    
                    loadConfig();
                    loadCatalog();
                    
                    if (config.targets != null) {
                        for (BuyTarget t : config.targets) t.itemsBought = 0;
                    }
                    autoSellQueue.clear();
                    seenLotsCache.clear();
                    seenEnchantedLots.clear();
                    
                    isInitialPriceSync = config.initialPriceSyncCount > 0;
                    initialPriceSyncDoneCount = 0;
                    antiAfkTimer = 2400; 

                    state = 1; 
                    refreshClickCount = 0;
                    
                    String statusMsg = isInitialPriceSync ? "§eАнализ рынка (" + config.initialPriceSyncCount + " сканов)..." : "§aАктивен!";
                    sendMessage("§a[+] Снайпер запущен! Режим: " + config.searchMode + ". " + statusMsg);
                    LOGGER.info("[CORE] Снайпер ЗАПУЩЕН. Режим: {}, Координаты: {}", config.searchMode, startPos);
                } else {
                    state = 0;
                    saveCatalog();
                    sendMessage("§c[-] Снайпер остановлен. Каталог сохранен.");
                    LOGGER.info("[CORE] Снайпер ОСТАНОВЛЕН пользователем.");
                }
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!isBotActive || state == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // --- ЛОГИКА АНТИ-АФК ---
        antiAfkTimer--;
        if (antiAfkTimer <= 0) {
            antiAfkTimer = 2400; 
            double dist = mc.player.position().distanceTo(startPos);
            LOGGER.info("[AntiAFK] Сработал таймер. Расстояние до старта: {} блоков", dist);
            
            if (dist > 2.0) {
                LOGGER.warn("[AntiAFK] Нас оттолкнули! Расстояние > 2. Закрываем меню и идем обратно.");
                mc.player.closeContainer();
                state = 6; 
                return;
            } else {
                mc.player.jumpFromGround();
                mc.player.setYRot(mc.player.getYRot() + 15);
            }
        }

        // --- СОСТОЯНИЕ 6: ВОЗВРАТ НА ТОЧКУ АНТИ-АФК ---
        if (state == 6) {
            double dist = mc.player.position().distanceTo(startPos);
            if (dist <= 1.0 || antiAfkTimer < 2300) { 
                LOGGER.info("[AntiAFK] Вернулись на позицию! Открываем аукцион заново.");
                mc.options.keyUp.setDown(false); 
                mc.player.setDeltaMovement(0, 0, 0);
                
                if (mc.getConnection() != null) mc.getConnection().sendCommand("ah");
                state = 4;
                timeoutTimer = 40;
            } else {
                double dx = startPos.x - mc.player.getX();
                double dz = startPos.z - mc.player.getZ();
                float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
                mc.player.setYRot(targetYaw);
                mc.options.keyUp.setDown(true); 
            }
            return;
        }

        // --- СОСТОЯНИЕ 5: АВТОПРОДАЖА ---
        if (state == 5) {
            timeoutTimer--;
            if (timeoutTimer == 30) {
                AutoSellJob job = autoSellQueue.peek();
                if (job != null) {
                    boolean found = false;
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = mc.player.getInventory().getItem(i);
                        if (cleanDisplayName(stack).equals(job.itemName)) {
                            if (i >= 9) {
                                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, 0, ClickType.SWAP, mc.player);
                            } else {
                                mc.player.getInventory().selected = i;
                            }
                            found = true;
                            break;
                        }
                    }
                    
                    if (found) {
                        sendMessage("§e[Автопродажа] Выставляем " + job.itemName + " за " + job.sellPrice);
                        if (mc.getConnection() != null) mc.getConnection().sendCommand("ah sell " + job.sellPrice);
                        activeSellSlots++;
                    }
                }
            } else if (timeoutTimer <= 0) {
                autoSellQueue.poll();
                if (mc.getConnection() != null) mc.getConnection().sendCommand("ah");
                state = 4;
                timeoutTimer = 40;
            }
            return;
        }

        if (!(mc.screen instanceof AbstractContainerScreen<?> containerScreen)) {
            if (state != 4 && state != 5 && state != 6) { 
                isBotActive = false;
                state = 0;
                saveCatalog();
                LOGGER.info("[CORE] Меню закрыто игроком. Бот выключен.");
            }
            return;
        }

        // --- СОСТОЯНИЕ 1: СКАНИРОВАНИЕ ---
        if (state == 1) {
            if (containerScreen.getMenu().slots.size() <= 49) return;
            ItemStack slot49 = containerScreen.getMenu().slots.get(49).getItem();
            if (!BuiltInRegistries.ITEM.getKey(slot49.getItem()).toString().equals("minecraft:nether_star")) {
                return; 
            }

            if (config.autoSell && !autoSellQueue.isEmpty() && activeSellSlots < 3 && !isInitialPriceSync) {
                mc.player.closeContainer();
                state = 5;
                timeoutTimer = 40;
                return;
            }

            long currentCoins = getCurrentCoins();
            LOGGER.info("--- [SCAN] СТРАНИЦА ПРОГРУЖЕНА. Баланс: {} ---", currentCoins);

            int maxSlot = Math.min(45, containerScreen.getMenu().slots.size());
            List<ItemStack> pageItems = new ArrayList<>();
            for (int i = 0; i < maxSlot; i++) {
                ItemStack stack = containerScreen.getMenu().slots.get(i).getItem();
                if (!stack.isEmpty()) {
                    pageItems.add(stack);
                    
                    // --- ДАМП ЗАЧАРОВАНИЙ ДЛЯ БИГ ДАТЫ ---
                    long parsedPrice = parsePrice(stack);
                    if (parsedPrice > 0 && hasEnchantmentsOnly(stack)) {
                        dumpEnchantedItemToFile(stack, parsedPrice);
                    }
                }
            }
            
            updateMarketPrices(pageItems);
            recordListingFrequency(pageItems);

            if (isInitialPriceSync) {
                initialPriceSyncDoneCount++;
                if (initialPriceSyncDoneCount % 10 == 0 || initialPriceSyncDoneCount == config.initialPriceSyncCount) {
                    sendMessage("§e[~] Анализ рынка: " + initialPriceSyncDoneCount + "/" + config.initialPriceSyncCount);
                }
                
                if (initialPriceSyncDoneCount >= config.initialPriceSyncCount) {
                    isInitialPriceSync = false;
                    sendMessage("§a[+] Анализ завершен! Переход к поиску покупок.");
                    LOGGER.info("[SCAN] Синхронизация цен завершена.");
                    
                    if (config.searchMode.equals("auto_farm")) {
                        printAutoFarmTargets();
                    }
                }
                
                lastSlot44State = getSlotState(containerScreen.getMenu().slots.get(44));
                clickSlot(containerScreen, 49);
                state = 3;
                refreshClickCount = 1;
                timeoutTimer = 10;
                return;
            }

            if (config.searchMode.equals("auto_farm")) {
                if (catalog.globalStats.totalScans % 20 == 0) {
                    printAutoFarmTargets();
                }
            }

            List<Deal> foundDeals = new ArrayList<>();
            for (int i = 0; i < maxSlot; i++) {
                Slot slot = containerScreen.getMenu().slots.get(i);
                if (slot.getItem().isEmpty()) continue;
                
                Deal deal = evaluateSlot(slot, i, currentCoins);
                if (deal != null) {
                    foundDeals.add(deal);
                }
            }

            if (!foundDeals.isEmpty()) {
                foundDeals.sort((a, b) -> Double.compare(b.margin, a.margin));
                currentDeal = foundDeals.get(0);
                
                ItemStack targetItem = containerScreen.getMenu().slots.get(currentDeal.slot).getItem();
                expectedId = BuiltInRegistries.ITEM.getKey(targetItem.getItem()).toString();
                expectedCleanName = cleanDisplayName(targetItem);
                expectedPrice = parsePrice(targetItem);
                
                LOGGER.info("[SCAN] >>> ЛУЧШАЯ СДЕЛКА НАЙДЕНА! Слот: {}, Имя: {}, Цена: {}, Выгода: {}%", 
                            currentDeal.slot, expectedCleanName, expectedPrice, Math.round(currentDeal.margin * 100));
                
                clickSlot(containerScreen, currentDeal.slot);
                state = 2;
                timeoutTimer = 40; 
            } else {
                LOGGER.info("[SCAN] Подходящих товаров нет. Запрашиваю обновление.");
                lastSlot44State = getSlotState(containerScreen.getMenu().slots.get(44));
                clickSlot(containerScreen, 49);
                state = 3;
                refreshClickCount = 1;
                timeoutTimer = 10; 
            }
        } 
        // --- СОСТОЯНИЕ 2: МЕНЮ ПОДТВЕРЖДЕНИЯ ---
        else if (state == 2) {
            timeoutTimer--;
            boolean verified = false;

            if (containerScreen.getMenu().slots.size() > 13) {
                Slot slot13 = containerScreen.getMenu().slots.get(13);
                ItemStack stack13 = slot13.getItem();
                
                if (!stack13.isEmpty()) {
                    String currentId = BuiltInRegistries.ITEM.getKey(stack13.getItem()).toString();
                    long currentPrice = parsePrice(stack13);
                    
                    // Бот проверяет слот каждый тик. Если предмет совпал - покупаем моментально!
                    if (currentId.equals(expectedId) && currentPrice == expectedPrice) {
                        verified = true;
                        LOGGER.info("[VERIFY] Успех! Ожидали: {} за {}. Видим: {} за {}. Кликаем Купить (0).", expectedCleanName, expectedPrice, currentId, currentPrice);
                        clickSlot(containerScreen, 0); 
                        
                        sendMessage("§a[!] Куплено: " + expectedCleanName + " за $" + currentPrice);
                        
                        if (currentDeal.isAutoCheap || currentDeal.isAutoFarm) {
                            if (config.autoSell && currentPrice > 0) {
                                MarketEntry mEntry = catalog.marketPrices.get(expectedCleanName);
                                if (mEntry != null && mEntry.avgMin > 0) {
                                    double sellMult = currentDeal.isAutoFarm ? config.autoFarmSellMultiplier : config.autoSellMultiplier;
                                    long targetSellPrice = (long)(mEntry.avgMin * sellMult);
                                    
                                    if (currentDeal.isAutoFarm && targetSellPrice < currentPrice * 1.08) {
                                        targetSellPrice = (long)(currentPrice * 1.08);
                                    }
                                    
                                    autoSellQueue.add(new AutoSellJob(expectedCleanName, targetSellPrice));
                                    LOGGER.info("[VERIFY] Предмет добавлен в очередь автопродажи за {}", targetSellPrice);
                                }
                            }
                        } else if (currentDeal.targetRule != null) {
                            currentDeal.targetRule.itemsBought += stack13.getCount();
                            if (checkIfAllPlansFulfilled()) {
                                sendMessage("§6[!] План выполнен. Бот отключен.");
                                LOGGER.info("[CORE] Все планы выполнены. Выключаюсь.");
                                isBotActive = false;
                                state = 0;
                                saveCatalog();
                                return;
                            }
                        }
                        
                        state = 4;
                        timeoutTimer = 15;
                    }
                }
            }
            
            // Если таймер вышел, а правильный предмет так и не появился в 13 слоте (таймаут или подмена)
            if (!verified && timeoutTimer <= 0) {
                LOGGER.warn("[VERIFY] Таймаут верификации! Правильный предмет не прогрузился или был перехвачен. Жмем Отмена (8).");
                if (containerScreen.getMenu().slots.size() > 8) {
                    clickSlot(containerScreen, 8);
                }
                sendMessage("§c[!] Ошибка верификации товара. Отмена.");
                state = 4;
                timeoutTimer = 15; // Даем время на выход обратно в аукцион
            }
        }
        // --- СОСТОЯНИЕ 3: ОЖИДАНИЕ ОБНОВЛЕНИЯ СТРАНИЦЫ ---
        else if (state == 3) {
            if (containerScreen.getMenu().slots.size() <= 49 || !BuiltInRegistries.ITEM.getKey(containerScreen.getMenu().slots.get(49).getItem().getItem()).toString().equals("minecraft:nether_star")) {
                return;
            }

            timeoutTimer--;
            String currentState = getSlotState(containerScreen.getMenu().slots.get(44));
            
            if (!currentState.equals(lastSlot44State)) {
                LOGGER.info("[REFRESH] Страница успешно обновилась сервером.");
                refreshClickCount = 0;
                state = 4;
                timeoutTimer = 10;
                return;
            }
            
            if (timeoutTimer <= 0) {
                if (refreshClickCount >= 10) {
                    sendMessage("§c[-] Лимит обновлений исчерпан. Бот отключен.");
                    LOGGER.error("[REFRESH] Сервер игнорирует обновление! Аварийное отключение.");
                    isBotActive = false;
                    state = 0;
                    saveCatalog();
                    return;
                }
                refreshClickCount++;
                LOGGER.info("[REFRESH] Страница не обновилась. Повторный клик {}/10...", refreshClickCount);
                clickSlot(containerScreen, 49);
                timeoutTimer = 10;
            }
        } 
        // --- СОСТОЯНИЕ 4: СТАБИЛИЗАЦИЯ ИНВЕНТАРЯ ---
        else if (state == 4) {
            timeoutTimer--;
            if (timeoutTimer <= 0) {
                state = 1; 
            }
        }
    }

    // ========================================================================
    // ОЦЕНКА СДЕЛОК
    // ========================================================================

    private static Deal evaluateSlot(Slot slot, int slotIndex, long currentCoins) {
        ItemStack stack = slot.getItem();
        long price = parsePrice(stack);
        if (price <= 0) return null;
        
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (id.contains("shulker_box") || id.contains("bundle")) return null;
        
        String cleanName = cleanDisplayName(stack);
        if (currentCoins > 0 && price > currentCoins) {
            LOGGER.info("[Eval] Слот {} ({}) слишком дорогой: {} > Баланс {}", slotIndex, cleanName, price, currentCoins);
            return null; 
        }
        
        long perUnit = price / stack.getCount();
        double durabilityRatio = getDurabilityRatio(stack);

        if (config.searchMode.equals("auto_cheap")) {
            if (!config.autoCheapItems.contains(cleanName)) return null;
            if (hasEnchantmentsOrPotions(stack)) return null; 
            
            MarketEntry marketInfo = catalog.marketPrices.get(cleanName);
            if (marketInfo == null || marketInfo.avgMin <= 0) {
                LOGGER.info("[Eval] Слот {} ({}) нет данных о рынке.", slotIndex, cleanName);
                return null;
            }
            
            double maxAllowedPerUnit = marketInfo.avgMin * config.autoCheapMultiplier * durabilityRatio;
            if (perUnit <= maxAllowedPerUnit) {
                double margin = (marketInfo.avgMin - perUnit) / (double)marketInfo.avgMin;
                LOGGER.info("[Eval] УСПЕХ (Cheap): Слот {} ({}) Цена {} <= Лимит {}", slotIndex, cleanName, perUnit, maxAllowedPerUnit);
                return new Deal(slotIndex, price, margin, null, false, true);
            }
        } 
        else if (config.searchMode.equals("auto_farm")) {
            if (price < config.autoFarmMinPrice) return null;
            if (hasEnchantmentsOrPotions(stack)) return null;

            MarketEntry mEntry = catalog.marketPrices.get(cleanName);
            FrequencyEntry fEntry = catalog.listingFrequency.get(cleanName);
            
            if (mEntry == null || mEntry.avgMin <= 0 || fEntry == null) return null;
            
            if (fEntry.totalSeenCount < config.autoFarmMinSeenCount || 
                fEntry.scansWithItem < config.autoFarmMinScansWithItem || 
                fEntry.newLotsCount < config.autoFarmMinNewLotsCount || 
                fEntry.frequencyPerScan < config.autoFarmMinFrequencyPerScan) {
                return null;
            }

            double maxAllowedPerUnit = mEntry.avgMin * config.autoFarmBuyMultiplier * durabilityRatio;
            if (perUnit <= maxAllowedPerUnit) {
                double margin = (mEntry.avgMin - perUnit) / (double)mEntry.avgMin;
                LOGGER.info("[Eval] УСПЕХ (Farm): Слот {} ({}) Цена {} <= Лимит {}", slotIndex, cleanName, perUnit, maxAllowedPerUnit);
                return new Deal(slotIndex, price, margin, null, true, false);
            }
        } 
        else { // "targeted"
            for (BuyTarget target : config.targets) {
                if (target.max_items > 0 && target.itemsBought >= target.max_items) continue;
                if (!target.target_id.isEmpty() && !id.equals(target.target_id)) continue;
                if (!target.name_contains.isEmpty() && !cleanName.toLowerCase().contains(target.name_contains.toLowerCase())) continue;

                if (target.max_price_per_item > 0) {
                    if (perUnit > target.max_price_per_item * durabilityRatio) {
                        LOGGER.info("[Eval] Слот {} ({}) не прошел по цене: {} > {}", slotIndex, cleanName, perUnit, target.max_price_per_item * durabilityRatio);
                        continue;
                    }
                }

                if (target.required_strings != null && !target.required_strings.isEmpty()) {
                    String components = stack.getComponents().toString();
                    boolean matchesAll = true;
                    for (String req : target.required_strings) {
                        if (!components.contains(req)) { matchesAll = false; break; }
                    }
                    if (!matchesAll) continue;
                }
                
                LOGGER.info("[Eval] УСПЕХ (Targeted): Слот {} ({}) подошел под правила.", slotIndex, cleanName);
                return new Deal(slotIndex, price, 1.0, target, false, false);
            }
        }
        return null;
    }

    private static boolean hasEnchantmentsOrPotions(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (id.contains("potion") || id.equals("minecraft:enchanted_book")) return true;
        
        String comp = stack.getComponents().toString().replaceAll("\\s+", ""); 
        return comp.contains("minecraft:enchantments") && !comp.contains("enchantments={}");
    }

    // Вспомогательный метод только для дампа чар (зелья игнорируем)
    private static boolean hasEnchantmentsOnly(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (id.contains("potion") || id.contains("shulker_box") || id.contains("bundle")) return false; 
        
        String comp = stack.getComponents().toString().replaceAll("\\s+", ""); 
        return comp.contains("minecraft:enchantments") && !comp.contains("enchantments={}");
    }

    private static void dumpEnchantedItemToFile(ItemStack stack, long price) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String name = cleanDisplayName(stack);
        String comp = stack.getComponents().toString();
        
        String lotKey = id + "|" + name + "|" + price + "|" + comp.hashCode();
        
        if (!seenEnchantedLots.contains(lotKey)) {
            seenEnchantedLots.add(lotKey);
            if (seenEnchantedLots.size() > 5000) {
                Iterator<String> it = seenEnchantedLots.iterator();
                it.next(); it.remove();
            }
            
            try {
                File dumpFile = new File(Minecraft.getInstance().gameDirectory, "config/ah_enchanted_dump.jsonl");
                FileWriter writer = new FileWriter(dumpFile, true);
                
                JsonObject obj = new JsonObject();
                obj.addProperty("id", id);
                obj.addProperty("name", name);
                obj.addProperty("price", price);
                obj.addProperty("components", comp);
                
                writer.write(GSON_FLAT.toJson(obj).replaceAll("\n", "").replaceAll("\r", "") + "\n");
                writer.close();
            } catch (Exception e) {
                LOGGER.error("[AutoBuy] Ошибка дампа чар", e);
            }
        }
    }

    // ========================================================================
    // ЛОГИКА АНАЛИЗА РЫНКА
    // ========================================================================

    private static void updateMarketPrices(List<ItemStack> pageItems) {
        Map<String, Long> pageMins = new HashMap<>();
        
        for (ItemStack item : pageItems) {
            String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            if (id.contains("shulker_box") || id.contains("bundle")) continue;
            if (hasEnchantmentsOrPotions(item)) continue;
            
            String cleanName = cleanDisplayName(item);
            long price = parsePrice(item);
            if (price <= 0) continue;
            long perUnit = price / item.getCount();
            
            if (!pageMins.containsKey(cleanName) || perUnit < pageMins.get(cleanName)) {
                pageMins.put(cleanName, perUnit);
            }
        }
        
        for (Map.Entry<String, Long> entry : pageMins.entrySet()) {
            String name = entry.getKey();
            long minOnPage = entry.getValue();
            
            MarketEntry mEntry = catalog.marketPrices.computeIfAbsent(name, k -> new MarketEntry());
            
            if (mEntry.avgMin > 0 && minOnPage > mEntry.avgMin * 1.5) continue;
            
            mEntry.recentMins.add(minOnPage);
            if (mEntry.recentMins.size() > 15) mEntry.recentMins.remove(0);
            
            List<Long> sorted = new ArrayList<>(mEntry.recentMins);
            Collections.sort(sorted);
            mEntry.avgMin = sorted.get(sorted.size() / 2); 
        }
    }

    private static void recordListingFrequency(List<ItemStack> pageItems) {
        catalog.globalStats.totalScans++;
        catalog.globalStats.lastScanTimestamp = System.currentTimeMillis();
        
        Set<String> seenOnThisScan = new HashSet<>();

        for (ItemStack item : pageItems) {
            String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            if (id.contains("shulker_box") || id.contains("bundle")) continue;
            
            String cleanName = cleanDisplayName(item);
            if (cleanName.isEmpty() || cleanName.equals("Unknown")) continue;

            FrequencyEntry entry = catalog.listingFrequency.computeIfAbsent(cleanName, k -> new FrequencyEntry());
            entry.lastSeen = System.currentTimeMillis();
            entry.totalSeenCount++;
            entry.totalUnitsSeen += item.getCount();

            String lotKey = cleanName + "|" + parsePrice(item) + "|" + item.getCount() + "|" + item.getComponents().hashCode();

            if (!seenLotsCache.contains(lotKey)) {
                seenLotsCache.add(lotKey);
                if (seenLotsCache.size() > 5000) {
                    Iterator<String> it = seenLotsCache.iterator();
                    it.next(); it.remove();
                }
                entry.newLotsCount++;
            }
            seenOnThisScan.add(cleanName);
        }

        for (String cleanName : seenOnThisScan) {
            FrequencyEntry entry = catalog.listingFrequency.get(cleanName);
            if (entry != null) {
                entry.scansWithItem++;
                entry.frequencyPerScan = (double) entry.totalSeenCount / catalog.globalStats.totalScans;
                entry.newLotsPerScan = (double) entry.newLotsCount / catalog.globalStats.totalScans;
                double hours = Math.max((System.currentTimeMillis() - entry.firstSeen) / 3600000.0, 0.01);
                entry.newLotsPerHour = entry.newLotsCount / hours;
            }
        }
    }

    private static void printAutoFarmTargets() {
        List<String> validTargets = new ArrayList<>();
        for (Map.Entry<String, MarketEntry> entry : catalog.marketPrices.entrySet()) {
            String cleanName = entry.getKey();
            MarketEntry mEntry = entry.getValue();
            FrequencyEntry fEntry = catalog.listingFrequency.get(cleanName);
            
            if (mEntry == null || mEntry.avgMin <= 0 || fEntry == null) continue;
            
            if (fEntry.totalSeenCount >= config.autoFarmMinSeenCount && 
                fEntry.scansWithItem >= config.autoFarmMinScansWithItem && 
                fEntry.newLotsCount >= config.autoFarmMinNewLotsCount && 
                fEntry.frequencyPerScan >= config.autoFarmMinFrequencyPerScan &&
                mEntry.avgMin >= config.autoFarmMinPrice) {
                
                long maxBuyPrice = (long)(mEntry.avgMin * config.autoFarmBuyMultiplier);
                validTargets.add(cleanName + " (<" + maxBuyPrice + ")");
            }
        }
        
        if (!validTargets.isEmpty()) {
            sendMessage("§b[FarmTargets] Ищем " + validTargets.size() + " целей. В консоли подробнее.");
            LOGGER.info("[AutoFarm] Активные цели для покупки: {}", String.join(", ", validTargets));
        } else {
            sendMessage("§e[FarmTargets] Пока нет подходящих ликвидных товаров.");
            LOGGER.info("[AutoFarm] Подходящих целей пока нет. Ждем накопления статистики.");
        }
    }

    // ========================================================================
    // УТИЛИТЫ И ПАРСЕРЫ
    // ========================================================================

    private static long getCurrentCoins() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return -1;
        net.minecraft.world.scores.Scoreboard scoreboard = mc.level.getScoreboard();
        
        for (net.minecraft.world.scores.ScoreHolder holder : scoreboard.getTrackedPlayers()) {
            String player = holder.getScoreboardName();
            net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayersTeam(player);
            Component prefix = team != null ? team.getPlayerPrefix() : Component.empty();
            Component suffix = team != null ? team.getPlayerSuffix() : Component.empty();
            String line = prefix.getString() + player + suffix.getString();
            
            line = line.replaceAll("§[0-9a-fk-or]", "");
            if (line.contains("Монет")) {
                String num = line.replaceAll("[^0-9]", "");
                if (!num.isEmpty()) {
                    try { return Long.parseLong(num); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return -1;
    }

    private static double getDurabilityRatio(ItemStack stack) {
        if (!stack.isDamageableItem()) return 1.0;
        int max = stack.getMaxDamage();
        int current = stack.getDamageValue(); 
        if (max <= 0) return 1.0;
        double ratio = (double) (max - current) / max;
        return Math.max(0.01, ratio); 
    }

    private static String cleanDisplayName(ItemStack stack) {
        return stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
    }

    private static boolean checkIfAllPlansFulfilled() {
        if (config.targets == null || config.targets.isEmpty()) return true;
        for (BuyTarget target : config.targets) {
            if (target.max_items == 0) return false; 
            if (target.itemsBought < target.max_items) return false; 
        }
        return true;
    }

    private static long parsePrice(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.EMPTY, mc.player, TooltipFlag.NORMAL);
        for (Component comp : tooltip) {
            String line = comp.getString().replaceAll("§[0-9a-fk-or]", ""); 
            if (line.contains("Цен") || line.contains("Цeн") || line.contains("$")) {
                String numPart = line.replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) {
                    try { return Long.parseLong(numPart); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return -1;
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, int slotId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotId, 0, ClickType.PICKUP, mc.player);
        }
    }

    private static String getSlotState(Slot slot) {
        if (slot.getItem().isEmpty()) return "empty";
        return BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString() + "_" + slot.getItem().getCount() + "_" + slot.getItem().getComponents().hashCode();
    }

    private static void sendMessage(String text) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(text), false);
        }
    }

    private static void loadConfig() {
        Minecraft mc = Minecraft.getInstance();
        File configFile = new File(mc.gameDirectory, "config/ah_bot.json");
        try {
            if (!configFile.exists()) {
                configFile.getParentFile().mkdirs();
                FileWriter writer = new FileWriter(configFile);
                BotConfig defaultConfig = new BotConfig();
                BuyTarget example = new BuyTarget();
                example.target_id = "minecraft:splash_potion";
                example.name_contains = "Святая вода";
                example.max_price_per_item = 400000;
                example.max_items = 5;
                example.required_strings.add("effect.minecraft.invisibility");
                defaultConfig.targets.add(example);
                GSON_PRETTY.toJson(defaultConfig, writer);
                writer.close();
            }
            FileReader reader = new FileReader(configFile);
            config = GSON_PRETTY.fromJson(reader, BotConfig.class);
            reader.close();
        } catch (Exception e) {
            LOGGER.error("Config load error", e);
            config = new BotConfig(); 
        }
    }
    
    private static void loadCatalog() {
        File catFile = new File(Minecraft.getInstance().gameDirectory, "config/ah_catalog.json");
        try {
            if (catFile.exists()) {
                FileReader reader = new FileReader(catFile);
                Type type = new TypeToken<CatalogData>(){}.getType();
                catalog = GSON_PRETTY.fromJson(reader, type);
                reader.close();
            }
        } catch (Exception e) { LOGGER.error("Catalog load error", e); }
        if (catalog == null) catalog = new CatalogData();
    }
    
    private static void saveCatalog() {
        File catFile = new File(Minecraft.getInstance().gameDirectory, "config/ah_catalog.json");
        try {
            FileWriter writer = new FileWriter(catFile);
            GSON_PRETTY.toJson(catalog, writer);
            writer.close();
        } catch (Exception e) { LOGGER.error("Catalog save error", e); }
    }
}
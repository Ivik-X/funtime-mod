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
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

@EventBusSubscriber(modid = "funtime_mod", value = Dist.CLIENT)
public class AutoBuyHandler {

    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final String SETTINGS_FILE = "config/opti_render_config.json";
    private static final String CATALOG_FILE = "config/opti_memory_cache.json";

    public static class BuyTarget {
        public String name; public long maxPrice;
        public BuyTarget(String n, long p) { name = n; maxPrice = p; }
    }

    public static class BotSettings {
        public String searchMode = "auto_farm"; 
        public boolean autoSell = true;
        public double autoFarmBuyMultiplier = 0.82;
        public double autoFarmSellMultiplier = 0.98;
        public long autoFarmMinPrice = 100000;
        public double autoCheapBuyMultiplier = 0.60;
        public double autoCheapSellMultiplier = 0.98;
        public List<String> autoCheapItems = new ArrayList<>();
        public List<BuyTarget> targetedItems = new ArrayList<>();

        public boolean autoTotem = true;
        public boolean healthSwap = false;
        public double healthThreshold = 10.0; 
        public String gSwapItem = "Сфера"; 

        public boolean noHurtCam = true;
        public boolean noBadEffects = true;
        public boolean marketTooltips = true;
        public boolean itemEsp = true;
        public long itemEspMinPrice = 500000; 
        public long smartLootMinPrice = 200000; 
    }
    public static BotSettings settings = new BotSettings();

    // ВОТ ЭТИ КЛАССЫ СЛУЧАЙНО УДАЛИЛИСЬ:
    public static class MarketEntry { public long avgMin; public List<Long> recentMins = new ArrayList<>(); }
    public static class FrequencyEntry { public int totalSeenCount = 0; public int newLotsCount = 0; public int scansWithItem = 0; public double frequencyPerScan = 0.0; }
    public static class GlobalStats { public int totalScans = 0; }
    public static class CatalogData {
        public Map<String, MarketEntry> marketPrices = new HashMap<>();
        public Map<String, FrequencyEntry> listingFrequency = new HashMap<>();
        public GlobalStats globalStats = new GlobalStats();
    }
    public static class AutoSellJob { public String itemName; public long sellPrice; public AutoSellJob(String name, long price) { this.itemName = name; this.sellPrice = price; } }

    private static CatalogData catalog = new CatalogData();
    private static Queue<AutoSellJob> autoSellQueue = new LinkedList<>();
    private static final Set<String> seenLotsCache = new LinkedHashSet<>();
    
    private static boolean isBotActive = false;
    private static boolean isDataLoaded = false;
    private static int activeSellSlots = 0; 
    private static int state = 0; 
    private static int timeoutTimer = 0;
    private static int refreshClickCount = 0; 
    private static String lastSlot44State = "";
    
    private static boolean isInitialPriceSync = false;
    private static int initialPriceSyncDoneCount = 0;
    private static int currentSyncTarget = 50;
    private static long lastFullAnalysisTime = 0;

    private static String expectedId = "";
    private static String expectedCleanName = "";
    private static long expectedPrice = -1;

    private static Vec3 startPos = null;
    private static int antiAfkTimer = 2400; 

    // API для GUI и Оверлеев
    public static boolean isBotActive() { return isBotActive; }
    public static String getRawMode() { return settings.searchMode; }
    public static String getModeName() { return settings.searchMode.equals("auto_farm") ? "Авто-Фарм" : settings.searchMode.equals("auto_cheap") ? "Скупка дешевого" : "Цели"; }
    public static boolean isAutoSell() { return settings.autoSell; }
    public static List<String> getCatalogNames() { return new ArrayList<>(catalog.marketPrices.keySet()); }
    
    public static MarketEntry getMarketEntry(String cleanName) { return catalog.marketPrices.get(cleanName); }

    public static void cycleMode() {
        if (settings.searchMode.equals("auto_farm")) settings.searchMode = "auto_cheap";
        else if (settings.searchMode.equals("auto_cheap")) settings.searchMode = "targeted";
        else settings.searchMode = "auto_farm";
        saveSettings();
    }
    public static void toggleAutoSell() { settings.autoSell = !settings.autoSell; saveSettings(); }

    @SubscribeEvent
    public static void onKeyPress(ScreenEvent.KeyPressed.Pre event) {
        if (!OptiItemsCore.isGloballyEnabled) return;
        
        if (event.getKeyCode() == GLFW.GLFW_KEY_J && Screen.hasControlDown()) {
            if (event.getScreen() instanceof AbstractContainerScreen<?>) {
                isBotActive = !isBotActive;
                Minecraft mc = Minecraft.getInstance();
                
                if (isBotActive) {
                    if (mc.player != null) startPos = mc.player.position();
                    autoSellQueue.clear();
                    seenLotsCache.clear();
                    
                    if (System.currentTimeMillis() - lastFullAnalysisTime < 15 * 60 * 1000) currentSyncTarget = 10;
                    else currentSyncTarget = 50;
                    
                    isInitialPriceSync = true;
                    initialPriceSyncDoneCount = 0;
                    antiAfkTimer = 2400; state = 1; refreshClickCount = 0;
                    sendMessage("§a[OptiItem] Анализ запущен (" + currentSyncTarget + " стр). Режим: " + getModeName());
                } else {
                    state = 0; saveCatalog();
                    sendMessage("§c[OptiItem] Процесс остановлен. Кэш сохранен.");
                }
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onSystemChat(ClientChatReceivedEvent event) {
        if (!OptiItemsCore.isGloballyEnabled || !isBotActive) return;
        String msg = event.getMessage().getString().replaceAll("§[0-9a-fk-or]", "").trim();
        if (msg.contains("Вы не можете больше выставлять товары на аукцион")) activeSellSlots = 5; 
        else if (msg.contains("У вас купили") || msg.contains("У вас приобрели")) if (activeSellSlots > 0) activeSellSlots--;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptiItemsCore.isGloballyEnabled || !isBotActive || state == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!isDataLoaded) {
            loadSettings();
            loadCatalog();
            isDataLoaded = true;
        }

        antiAfkTimer--;
        if (antiAfkTimer <= 0) {
            antiAfkTimer = 2400; 
            if (mc.player.position().distanceTo(startPos) > 2.0) { mc.player.closeContainer(); state = 6; return; } 
            else { mc.player.jumpFromGround(); mc.player.setYRot(mc.player.getYRot() + 15); }
        }

        if (state == 6) {
            if (mc.player.position().distanceTo(startPos) <= 1.0 || antiAfkTimer < 2300) { 
                mc.options.keyUp.setDown(false); mc.player.setDeltaMovement(0, 0, 0);
                if (mc.getConnection() != null) mc.getConnection().sendCommand("ah");
                state = 4; timeoutTimer = 40;
            } else {
                float targetYaw = (float) (Math.toDegrees(Math.atan2(startPos.z - mc.player.getZ(), startPos.x - mc.player.getX())) - 90.0F);
                mc.player.setYRot(targetYaw); mc.options.keyUp.setDown(true); 
            }
            return;
        }

        if (state == 5) {
            timeoutTimer--;
            if (timeoutTimer == 30) {
                AutoSellJob job = autoSellQueue.peek();
                if (job != null) {
                    boolean found = false;
                    for (int i = 0; i < 36; i++) {
                        if (cleanDisplayName(mc.player.getInventory().getItem(i)).equals(job.itemName)) {
                            if (i >= 9) mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, 0, ClickType.SWAP, mc.player);
                            else mc.player.getInventory().selected = i;
                            found = true; break;
                        }
                    }
                    if (found) { if (mc.getConnection() != null) mc.getConnection().sendCommand("ah sell " + job.sellPrice); activeSellSlots++; }
                }
            } else if (timeoutTimer <= 0) {
                autoSellQueue.poll();
                if (mc.getConnection() != null) mc.getConnection().sendCommand("ah");
                state = 4; timeoutTimer = 40;
            }
            return;
        }

        if (!(mc.screen instanceof AbstractContainerScreen<?> containerScreen)) {
            if (state != 4 && state != 5 && state != 6) { isBotActive = false; state = 0; saveCatalog(); }
            return;
        }

        if (state == 1) {
            if (containerScreen.getMenu().slots.size() <= 49) return;
            if (!BuiltInRegistries.ITEM.getKey(containerScreen.getMenu().slots.get(49).getItem().getItem()).toString().equals("minecraft:nether_star")) return; 

            if (settings.autoSell && !autoSellQueue.isEmpty() && activeSellSlots < 5 && !isInitialPriceSync) {
                mc.player.closeContainer(); state = 5; timeoutTimer = 40; return;
            }

            long currentCoins = getCurrentCoins();
            int maxSlot = Math.min(45, containerScreen.getMenu().slots.size());
            List<ItemStack> pageItems = new ArrayList<>();
            for (int i = 0; i < maxSlot; i++) {
                if (!containerScreen.getMenu().slots.get(i).getItem().isEmpty()) pageItems.add(containerScreen.getMenu().slots.get(i).getItem());
            }
            
            updateMarketPrices(pageItems);
            recordListingFrequency(pageItems);

            if (isInitialPriceSync) {
                initialPriceSyncDoneCount++;
                if (initialPriceSyncDoneCount >= currentSyncTarget) {
                    isInitialPriceSync = false;
                    if (currentSyncTarget >= 50) lastFullAnalysisTime = System.currentTimeMillis();
                }
                lastSlot44State = getSlotState(containerScreen.getMenu().slots.get(44));
                clickSlot(containerScreen, 49);
                state = 3; refreshClickCount = 1; timeoutTimer = 10;
                return;
            }

            double bestMargin = -1; int bestSlot = -1;

            for (int i = 0; i < maxSlot; i++) {
                Slot slot = containerScreen.getMenu().slots.get(i);
                if (slot.getItem().isEmpty()) continue;
                ItemStack stack = slot.getItem();
                long price = parsePrice(stack);
                
                if (price <= 0 || (currentCoins > 0 && price > currentCoins)) continue;
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (id.contains("shulker_box") || id.contains("bundle") || hasEnchantmentsOrPotions(stack)) continue;

                String cleanName = cleanDisplayName(stack);
                long perUnit = price / stack.getCount();
                MarketEntry mEntry = catalog.marketPrices.get(cleanName);

                if (settings.searchMode.equals("auto_farm")) {
                    FrequencyEntry fEntry = catalog.listingFrequency.get(cleanName);
                    if (mEntry == null || mEntry.avgMin <= 0 || fEntry == null) continue;
                    if (price < settings.autoFarmMinPrice || fEntry.totalSeenCount < 20 || fEntry.frequencyPerScan < 0.25) continue;
                    
                    double maxAllowed = mEntry.avgMin * settings.autoFarmBuyMultiplier;
                    if (perUnit <= maxAllowed) {
                        double margin = (mEntry.avgMin - perUnit) / (double)mEntry.avgMin;
                        if (margin > bestMargin) { bestMargin = margin; bestSlot = i; }
                    }
                } 
                else if (settings.searchMode.equals("auto_cheap")) {
                    if (mEntry == null || mEntry.avgMin <= 0) continue;
                    if (!settings.autoCheapItems.contains(cleanName)) continue;
                    
                    double maxAllowed = mEntry.avgMin * settings.autoCheapBuyMultiplier;
                    if (perUnit <= maxAllowed) {
                        double margin = (mEntry.avgMin - perUnit) / (double)mEntry.avgMin;
                        if (margin > bestMargin) { bestMargin = margin; bestSlot = i; }
                    }
                } 
                else if (settings.searchMode.equals("targeted")) {
                    for (BuyTarget target : settings.targetedItems) {
                        if (cleanName.equals(target.name) && perUnit <= target.maxPrice) {
                            bestMargin = 1.0; bestSlot = i; break;
                        }
                    }
                }
            }

            if (bestSlot != -1) {
                ItemStack targetItem = containerScreen.getMenu().slots.get(bestSlot).getItem();
                expectedId = BuiltInRegistries.ITEM.getKey(targetItem.getItem()).toString();
                expectedCleanName = cleanDisplayName(targetItem);
                expectedPrice = parsePrice(targetItem);
                clickSlot(containerScreen, bestSlot);
                state = 2; timeoutTimer = 40; 
            } else {
                lastSlot44State = getSlotState(containerScreen.getMenu().slots.get(44));
                clickSlot(containerScreen, 49);
                state = 3; refreshClickCount = 1; timeoutTimer = 10; 
            }
        } 
        else if (state == 2) {
            timeoutTimer--; boolean verified = false;
            if (containerScreen.getMenu().slots.size() > 13) {
                ItemStack stack13 = containerScreen.getMenu().slots.get(13).getItem();
                if (!stack13.isEmpty()) {
                    String currentId = BuiltInRegistries.ITEM.getKey(stack13.getItem()).toString();
                    long currentPrice = parsePrice(stack13);
                    if (currentId.equals(expectedId) && currentPrice == expectedPrice) {
                        verified = true; clickSlot(containerScreen, 0); 
                        if (settings.autoSell && currentPrice > 0) {
                            MarketEntry mEntry = catalog.marketPrices.get(expectedCleanName);
                            if (mEntry != null && mEntry.avgMin > 0) {
                                double sellMult = settings.searchMode.equals("auto_farm") ? settings.autoFarmSellMultiplier : settings.autoCheapSellMultiplier;
                                long targetSellPrice = (long)(mEntry.avgMin * sellMult);
                                if (targetSellPrice < currentPrice * 1.08) targetSellPrice = (long)(currentPrice * 1.08);
                                autoSellQueue.add(new AutoSellJob(expectedCleanName, targetSellPrice));
                            }
                        }
                        state = 4; timeoutTimer = 15;
                    }
                }
            }
            if (!verified && timeoutTimer <= 0) {
                if (containerScreen.getMenu().slots.size() > 8) clickSlot(containerScreen, 8);
                state = 4; timeoutTimer = 15;
            }
        } 
        else if (state == 3) {
            if (containerScreen.getMenu().slots.size() <= 49 || !BuiltInRegistries.ITEM.getKey(containerScreen.getMenu().slots.get(49).getItem().getItem()).toString().equals("minecraft:nether_star")) return;
            timeoutTimer--;
            if (!getSlotState(containerScreen.getMenu().slots.get(44)).equals(lastSlot44State)) { refreshClickCount = 0; state = 4; timeoutTimer = 10; return; }
            if (timeoutTimer <= 0) {
                if (refreshClickCount >= 10) { isBotActive = false; state = 0; return; }
                refreshClickCount++; clickSlot(containerScreen, 49); timeoutTimer = 10;
            }
        } 
        else if (state == 4) { timeoutTimer--; if (timeoutTimer <= 0) state = 1; }
    }

    private static void updateMarketPrices(List<ItemStack> pageItems) {
        Map<String, Long> pageMins = new HashMap<>();
        for (ItemStack item : pageItems) {
            if (hasEnchantmentsOrPotions(item) || BuiltInRegistries.ITEM.getKey(item.getItem()).toString().contains("shulker_box")) continue;
            String name = cleanDisplayName(item);
            long price = parsePrice(item);
            if (price > 0) {
                long perUnit = price / item.getCount();
                if (!pageMins.containsKey(name) || perUnit < pageMins.get(name)) pageMins.put(name, perUnit);
            }
        }
        for (Map.Entry<String, Long> entry : pageMins.entrySet()) {
            MarketEntry mEntry = catalog.marketPrices.computeIfAbsent(entry.getKey(), k -> new MarketEntry());
            if (mEntry.avgMin > 0 && entry.getValue() > mEntry.avgMin * 1.5) continue;
            mEntry.recentMins.add(entry.getValue());
            if (mEntry.recentMins.size() > 15) mEntry.recentMins.remove(0);
            List<Long> sorted = new ArrayList<>(mEntry.recentMins);
            Collections.sort(sorted);
            mEntry.avgMin = sorted.get(sorted.size() / 2); 
        }
    }

    private static void recordListingFrequency(List<ItemStack> pageItems) {
        catalog.globalStats.totalScans++;
        Set<String> seenOnThisScan = new HashSet<>();
        for (ItemStack item : pageItems) {
            if (BuiltInRegistries.ITEM.getKey(item.getItem()).toString().contains("shulker_box")) continue;
            String name = cleanDisplayName(item);
            if (name.isEmpty()) continue;
            FrequencyEntry entry = catalog.listingFrequency.computeIfAbsent(name, k -> new FrequencyEntry());
            entry.totalSeenCount++;
            String lotKey = name + "|" + parsePrice(item) + "|" + item.getComponents().hashCode();
            if (!seenLotsCache.contains(lotKey)) {
                seenLotsCache.add(lotKey);
                if (seenLotsCache.size() > 5000) { Iterator<String> it = seenLotsCache.iterator(); it.next(); it.remove(); }
                entry.newLotsCount++;
            }
            seenOnThisScan.add(name);
        }
        for (String name : seenOnThisScan) {
            FrequencyEntry entry = catalog.listingFrequency.get(name);
            if (entry != null) { entry.scansWithItem++; entry.frequencyPerScan = (double) entry.totalSeenCount / catalog.globalStats.totalScans; }
        }
    }

    private static boolean hasEnchantmentsOrPotions(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (id.contains("potion") || id.equals("minecraft:enchanted_book")) return true;
        String comp = stack.getComponents().toString().replaceAll("\\s+", ""); 
        return comp.contains("minecraft:enchantments") && !comp.contains("enchantments={}");
    }
    private static long getCurrentCoins() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return -1;
        for (net.minecraft.world.scores.ScoreHolder holder : mc.level.getScoreboard().getTrackedPlayers()) {
            String player = holder.getScoreboardName();
            net.minecraft.world.scores.PlayerTeam team = mc.level.getScoreboard().getPlayersTeam(player);
            String line = (team != null ? team.getPlayerPrefix().getString() : "") + player + (team != null ? team.getPlayerSuffix().getString() : "");
            line = line.replaceAll("§[0-9a-fk-or]", "");
            if (line.contains("Монет")) {
                String num = line.replaceAll("[^0-9]", "");
                if (!num.isEmpty()) try { return Long.parseLong(num); } catch (Exception ignored) {}
            }
        }
        return -1;
    }
    private static String cleanDisplayName(ItemStack stack) { return stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim(); }
    private static long parsePrice(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        for (Component comp : stack.getTooltipLines(Item.TooltipContext.EMPTY, mc.player, TooltipFlag.NORMAL)) {
            String line = comp.getString().replaceAll("§[0-9a-fk-or]", ""); 
            if (line.contains("Цен") || line.contains("Цeн") || line.contains("$")) {
                String numPart = line.replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) try { return Long.parseLong(numPart); } catch (Exception ignored) {}
            }
        }
        return -1;
    }
    private static void clickSlot(AbstractContainerScreen<?> screen, int slotId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.player != null) mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotId, 0, ClickType.PICKUP, mc.player);
    }
    private static String getSlotState(Slot slot) { return slot.getItem().isEmpty() ? "empty" : slot.getItem().getCount() + "_" + slot.getItem().getComponents().hashCode(); }
    private static void sendMessage(String text) { if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.literal(text), false); }

    public static void loadSettings() {
        File file = new File(Minecraft.getInstance().gameDirectory, SETTINGS_FILE);
        try { if (file.exists()) { FileReader reader = new FileReader(file); settings = GSON_PRETTY.fromJson(reader, BotSettings.class); reader.close(); }
        } catch (Exception e) { settings = new BotSettings(); }
    }
    public static void saveSettings() {
        try { FileWriter writer = new FileWriter(new File(Minecraft.getInstance().gameDirectory, SETTINGS_FILE)); GSON_PRETTY.toJson(settings, writer); writer.close();
        } catch (Exception ignored) {}
    }
    private static void loadCatalog() {
        File file = new File(Minecraft.getInstance().gameDirectory, CATALOG_FILE);
        try { if (file.exists()) { FileReader reader = new FileReader(file); catalog = GSON_PRETTY.fromJson(reader, new TypeToken<CatalogData>(){}.getType()); reader.close(); }
        } catch (Exception e) { catalog = new CatalogData(); }
    }
    private static void saveCatalog() {
        try { FileWriter writer = new FileWriter(new File(Minecraft.getInstance().gameDirectory, CATALOG_FILE)); GSON_PRETTY.toJson(catalog, writer); writer.close();
        } catch (Exception ignored) {}
    }
}
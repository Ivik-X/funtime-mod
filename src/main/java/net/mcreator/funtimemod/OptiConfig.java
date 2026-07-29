package net.mcreator.funtimemod.client;

import net.minecraft.client.Minecraft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class OptiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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
        public boolean aimAssist = false; 
        public double aimAssistFov = 15.0; // Максимальный угол захвата
        public double aimAssistRange = 3.5; // Дистанция для магнита

        public boolean noHurtCam = true;
        public boolean noBadEffects = true;
        public boolean marketTooltips = true;
        public boolean itemEsp = true;
        public long itemEspMinPrice = 500000; 
        
        public long smartLootMinPrice = 200000; 
        public boolean autoStealOnOpen = false; 
        
        public boolean wardenEsp = false;
        public int wardenEspTime = 20; 
        public boolean wardenAutoOpen = false;
    }
    public static BotSettings settings = new BotSettings();

    public static class MarketEntry { public long avgMin; public List<Long> recentMins = new ArrayList<>(); }
    public static class FrequencyEntry { public int totalSeenCount = 0; public int newLotsCount = 0; public int scansWithItem = 0; public double frequencyPerScan = 0.0; }
    public static class GlobalStats { public int totalScans = 0; }
    public static class CatalogData {
        public Map<String, MarketEntry> marketPrices = new HashMap<>();
        public Map<String, FrequencyEntry> listingFrequency = new HashMap<>();
        public GlobalStats globalStats = new GlobalStats();
    }
    public static CatalogData catalog = new CatalogData();

    public static class AutoSellJob { 
        public String itemName; public long sellPrice; 
        public AutoSellJob(String name, long price) { this.itemName = name; this.sellPrice = price; } 
    }

    public static void loadAll() {
        try { File f = new File(Minecraft.getInstance().gameDirectory, SETTINGS_FILE); if (f.exists()) { FileReader r = new FileReader(f); settings = GSON.fromJson(r, BotSettings.class); r.close(); } } catch (Exception e) { settings = new BotSettings(); }
        try { File f = new File(Minecraft.getInstance().gameDirectory, CATALOG_FILE); if (f.exists()) { FileReader r = new FileReader(f); catalog = GSON.fromJson(r, new TypeToken<CatalogData>(){}.getType()); r.close(); } } catch (Exception e) { catalog = new CatalogData(); }
    }

    public static void saveAll() {
        try { FileWriter w = new FileWriter(new File(Minecraft.getInstance().gameDirectory, SETTINGS_FILE)); GSON.toJson(settings, w); w.close(); } catch (Exception ignored) {}
        try { FileWriter w = new FileWriter(new File(Minecraft.getInstance().gameDirectory, CATALOG_FILE)); GSON.toJson(catalog, w); w.close(); } catch (Exception ignored) {}
    }
}
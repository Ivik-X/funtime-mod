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
        public double aimAssistFov = 25.0; 
        public double aimAssistRange = 3.5; 
        public double hitboxExpander = 0.0; 
        public boolean autoBuff = false; 
        public List<String> autoBuffPotions = new ArrayList<>(); 

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
        public double wardenTextScale = 0.08;

        public int quickBuyMaxCount = 1;

        public boolean inventoryMove = true;
        public boolean fullbright = false;
        public boolean armorHud = true;
        public boolean radar = true;
        public boolean playerEsp = false;
        public boolean trajectories = true;
        
        public boolean customViewModel = false;
        public double vmX = 0.0, vmY = 0.0, vmZ = 0.0, vmScale = 1.0;
        public double vmPitch = 0.0, vmYaw = 0.0, vmRoll = 0.0;
        
        public List<String> blockEspList = new ArrayList<>(Arrays.asList("minecraft:diamond_ore", "minecraft:ancient_debris", "minecraft:chest", "minecraft:trapped_chest"));
        public boolean blockEspEnabled = false;

        public List<String> autoSwapItems = new ArrayList<>();
        public List<String> elytraSwapItems = new ArrayList<>();
        public List<String> autoSellItems = new ArrayList<>();
        public boolean autoRelist = true;
    }
    public static BotSettings settings = new BotSettings();

    public static class MarketEntry { public long avgMin; public List<Long> recentMins = new ArrayList<>(); }
    public static class CatalogData { 
        public Map<String, MarketEntry> marketPrices = new HashMap<>(); 
        public Map<String, String> cachedItemIds = new HashMap<>();
    }
    public static CatalogData catalog = new CatalogData();

    public static class AutoSellJob { 
        public String itemName; public long sellPrice; 
        public AutoSellJob(String name, long price) { this.itemName = name; this.sellPrice = price; } 
    }

    public static void loadAll() {
        try { File f = new File(Minecraft.getInstance().gameDirectory, SETTINGS_FILE); if (f.exists()) { FileReader r = new FileReader(f); settings = GSON.fromJson(r, BotSettings.class); r.close(); } } catch (Exception ignored) {}
        try { File f = new File(Minecraft.getInstance().gameDirectory, "config/opti_memory_cache.json"); if (f.exists()) { FileReader r = new FileReader(f); catalog = GSON.fromJson(r, new TypeToken<CatalogData>(){}.getType()); r.close(); } } catch (Exception ignored) {}
    }

    public static void saveAll() {
        try { FileWriter w = new FileWriter(new File(Minecraft.getInstance().gameDirectory, SETTINGS_FILE)); GSON.toJson(settings, w); w.close(); } catch (Exception ignored) {}
        try { FileWriter w = new FileWriter(new File(Minecraft.getInstance().gameDirectory, "config/opti_memory_cache.json")); GSON.toJson(catalog, w); w.close(); } catch (Exception ignored) {}
    }
}
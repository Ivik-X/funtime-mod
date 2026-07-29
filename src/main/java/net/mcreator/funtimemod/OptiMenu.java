package net.mcreator.funtimemod.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class OptiMenu extends Screen {

    private static String currentTab = "combat"; 
    private EditBox t1, t2, t3, t4, t5, t6, t7;

    public OptiMenu() { super(Component.literal("")); }

    @Override
    protected void init() {
        super.init();
        int cX = this.width / 2, sY = 40;

        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("market") ? "§eMarket" : "Market"), b -> switchTab("market", true)).bounds(cX - 250, 10, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("combat") ? "§eCombat" : "Combat"), b -> switchTab("combat", true)).bounds(cX - 150, 10, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("render") ? "§eRender" : "Render"), b -> switchTab("render", true)).bounds(cX - 50, 10, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("world") ? "§eWorld" : "World"), b -> switchTab("world", true)).bounds(cX + 50, 10, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("misc") ? "§eMisc" : "Misc"), b -> switchTab("misc", true)).bounds(cX + 150, 10, 95, 20).build());

        if (currentTab.equals("market")) {
            this.addRenderableWidget(Button.builder(Component.literal("Режим: " + (OptiConfig.settings.searchMode.equals("auto_farm") ? "Авто-Фарм" : OptiConfig.settings.searchMode.equals("auto_cheap") ? "Дешевое" : "Цели")), b -> { 
                OptiConfig.settings.searchMode = OptiConfig.settings.searchMode.equals("auto_farm") ? "auto_cheap" : OptiConfig.settings.searchMode.equals("auto_cheap") ? "targeted" : "auto_farm"; 
                switchTab("market", true); 
            }).bounds(cX - 100, sY, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Автопродажа: " + (OptiConfig.settings.autoSell ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.autoSell = !OptiConfig.settings.autoSell; switchTab("market", true); }).bounds(cX - 100, sY + 25, 120, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Предметы"), b -> { saveSettings(); this.minecraft.setScreen(new OptiCatalogs(this, "autosell")); }).bounds(cX + 25, sY + 25, 75, 20).build());
            
            if (OptiConfig.settings.searchMode.equals("auto_farm")) {
                t1 = new EditBox(this.font, cX - 100, sY + 65, 95, 20, Component.literal("")); t1.setValue(String.valueOf(OptiConfig.settings.autoFarmBuyMultiplier)); this.addRenderableWidget(t1);
                t2 = new EditBox(this.font, cX + 5, sY + 65, 95, 20, Component.literal("")); t2.setValue(String.valueOf(OptiConfig.settings.autoFarmSellMultiplier)); this.addRenderableWidget(t2);
                t3 = new EditBox(this.font, cX - 100, sY + 105, 200, 20, Component.literal("")); t3.setValue(String.valueOf(OptiConfig.settings.autoFarmMinPrice)); this.addRenderableWidget(t3);
            } else if (OptiConfig.settings.searchMode.equals("auto_cheap")) {
                t1 = new EditBox(this.font, cX - 100, sY + 65, 95, 20, Component.literal("")); t1.setValue(String.valueOf(OptiConfig.settings.autoCheapBuyMultiplier)); this.addRenderableWidget(t1);
                t2 = new EditBox(this.font, cX + 5, sY + 65, 95, 20, Component.literal("")); t2.setValue(String.valueOf(OptiConfig.settings.autoCheapSellMultiplier)); this.addRenderableWidget(t2);
                this.addRenderableWidget(Button.builder(Component.literal("§eНастроить список дешевого"), b -> { saveSettings(); this.minecraft.setScreen(new OptiCatalogs(this, "cheap")); }).bounds(cX - 100, sY + 105, 200, 20).build());
            } else {
                this.addRenderableWidget(Button.builder(Component.literal("§eНастроить список целей"), b -> { saveSettings(); this.minecraft.setScreen(new OptiCatalogs(this, "targets")); }).bounds(cX - 100, sY + 65, 200, 20).build());
            }
        }
        else if (currentTab.equals("combat")) {
            this.addRenderableWidget(Button.builder(Component.literal("Aim Assist: " + (OptiConfig.settings.aimAssist ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.aimAssist = !OptiConfig.settings.aimAssist; switchTab("combat", true); }).bounds(cX - 100, sY, 95, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("AutoBuff: " + (OptiConfig.settings.autoBuff ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.autoBuff = !OptiConfig.settings.autoBuff; switchTab("combat", true); }).bounds(cX + 5, sY, 95, 20).build());
            
            this.addRenderableWidget(Button.builder(Component.literal("Зелья"), b -> { saveSettings(); this.minecraft.setScreen(new OptiCatalogs(this, "potions")); }).bounds(cX - 100, sY + 25, 60, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Свап"), b -> { saveSettings(); this.minecraft.setScreen(new OptiCatalogs(this, "autoswap")); }).bounds(cX - 35, sY + 25, 60, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Элитра"), b -> { saveSettings(); this.minecraft.setScreen(new OptiCatalogs(this, "elytraswap")); }).bounds(cX + 30, sY + 25, 60, 20).build());
            
            t1 = new EditBox(this.font, cX - 100, sY + 65, 95, 20, Component.literal("")); t1.setValue(String.valueOf(OptiConfig.settings.aimAssistFov)); this.addRenderableWidget(t1);
            t2 = new EditBox(this.font, cX + 5, sY + 65, 95, 20, Component.literal("")); t2.setValue(String.valueOf(OptiConfig.settings.aimAssistRange)); this.addRenderableWidget(t2);
            t3 = new EditBox(this.font, cX - 100, sY + 105, 200, 20, Component.literal("")); t3.setValue(String.valueOf(OptiConfig.settings.hitboxExpander)); this.addRenderableWidget(t3);
        }
        else if (currentTab.equals("render")) {
            this.addRenderableWidget(Button.builder(Component.literal("Armor HUD: " + (OptiConfig.settings.armorHud ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.armorHud = !OptiConfig.settings.armorHud; switchTab("render", true); }).bounds(cX - 150, sY, 145, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Radar: " + (OptiConfig.settings.radar ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.radar = !OptiConfig.settings.radar; switchTab("render", true); }).bounds(cX + 5, sY, 145, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Trajectories: " + (OptiConfig.settings.trajectories ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.trajectories = !OptiConfig.settings.trajectories; switchTab("render", true); }).bounds(cX - 150, sY + 25, 145, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Fullbright: " + (OptiConfig.settings.fullbright ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.fullbright = !OptiConfig.settings.fullbright; switchTab("render", true); }).bounds(cX + 5, sY + 25, 145, 20).build());
            
            this.addRenderableWidget(Button.builder(Component.literal("Custom ViewModel: " + (OptiConfig.settings.customViewModel ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.customViewModel = !OptiConfig.settings.customViewModel; switchTab("render", true); }).bounds(cX - 150, sY + 50, 200, 20).build());
            
            this.addRenderableWidget(Button.builder(Component.literal("Стандарт"), b -> { t1.setValue("0.0"); t2.setValue("0.0"); t3.setValue("0.0"); t4.setValue("0.0"); t5.setValue("0.0"); t6.setValue("0.0"); t7.setValue("1.0"); }).bounds(cX - 150, sY + 75, 75, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Мини"), b -> { t1.setValue("0.2"); t2.setValue("-0.4"); t3.setValue("-0.5"); t4.setValue("0.0"); t5.setValue("0.0"); t6.setValue("0.0"); t7.setValue("0.6"); }).bounds(cX - 70, sY + 75, 70, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Катана"), b -> { t1.setValue("0.5"); t2.setValue("-0.5"); t3.setValue("-0.8"); t4.setValue("0.0"); t5.setValue("-10.0"); t6.setValue("0.0"); t7.setValue("1.3"); }).bounds(cX + 5, sY + 75, 70, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Дельта"), b -> { t1.setValue("0.3"); t2.setValue("-0.2"); t3.setValue("-0.1"); t4.setValue("0.0"); t5.setValue("0.0"); t6.setValue("-45.0"); t7.setValue("0.9"); }).bounds(cX + 80, sY + 75, 70, 20).build());
            
            t1 = new EditBox(this.font, cX - 150, sY + 120, 45, 20, Component.literal("")); t1.setValue(String.valueOf(OptiConfig.settings.vmX)); this.addRenderableWidget(t1);
            t2 = new EditBox(this.font, cX - 100, sY + 120, 45, 20, Component.literal("")); t2.setValue(String.valueOf(OptiConfig.settings.vmY)); this.addRenderableWidget(t2);
            t3 = new EditBox(this.font, cX - 50, sY + 120, 45, 20, Component.literal("")); t3.setValue(String.valueOf(OptiConfig.settings.vmZ)); this.addRenderableWidget(t3);
            t4 = new EditBox(this.font, cX + 5, sY + 120, 45, 20, Component.literal("")); t4.setValue(String.valueOf(OptiConfig.settings.vmPitch)); this.addRenderableWidget(t4);
            t5 = new EditBox(this.font, cX + 55, sY + 120, 45, 20, Component.literal("")); t5.setValue(String.valueOf(OptiConfig.settings.vmYaw)); this.addRenderableWidget(t5);
            t6 = new EditBox(this.font, cX + 105, sY + 120, 45, 20, Component.literal("")); t6.setValue(String.valueOf(OptiConfig.settings.vmRoll)); this.addRenderableWidget(t6);
            t7 = new EditBox(this.font, cX - 25, sY + 160, 50, 20, Component.literal("")); t7.setValue(String.valueOf(OptiConfig.settings.vmScale)); this.addRenderableWidget(t7);
        }
        else if (currentTab.equals("world")) {
            this.addRenderableWidget(Button.builder(Component.literal("Block ESP: " + (OptiConfig.settings.blockEspEnabled ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.blockEspEnabled = !OptiConfig.settings.blockEspEnabled; switchTab("world", true); }).bounds(cX - 100, sY, 95, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Выбрать Блоки"), b -> { saveSettings(); this.minecraft.setScreen(new OptiCatalogs(this, "blocks")); }).bounds(cX + 5, sY, 95, 20).build());
            
            this.addRenderableWidget(Button.builder(Component.literal("Warden ESP: " + (OptiConfig.settings.wardenEsp ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.wardenEsp = !OptiConfig.settings.wardenEsp; switchTab("world", true); }).bounds(cX - 100, sY + 25, 95, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Warden AutoOpen: " + (OptiConfig.settings.wardenAutoOpen ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.wardenAutoOpen = !OptiConfig.settings.wardenAutoOpen; switchTab("world", true); }).bounds(cX + 5, sY + 25, 95, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Player ESP: " + (OptiConfig.settings.playerEsp ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.playerEsp = !OptiConfig.settings.playerEsp; switchTab("world", true); }).bounds(cX - 100, sY + 50, 95, 20).build());

            t1 = new EditBox(this.font, cX - 100, sY + 90, 95, 20, Component.literal("")); t1.setValue(String.valueOf(OptiConfig.settings.wardenEspTime)); this.addRenderableWidget(t1);
            t2 = new EditBox(this.font, cX + 5, sY + 90, 95, 20, Component.literal("")); t2.setValue(String.valueOf(OptiConfig.settings.wardenTextScale)); this.addRenderableWidget(t2);
        }
        else if (currentTab.equals("misc")) {
            this.addRenderableWidget(Button.builder(Component.literal("InvMove: " + (OptiConfig.settings.inventoryMove ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.inventoryMove = !OptiConfig.settings.inventoryMove; switchTab("misc", true); }).bounds(cX - 100, sY, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Auto Steal (Chests): " + (OptiConfig.settings.autoStealOnOpen ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.autoStealOnOpen = !OptiConfig.settings.autoStealOnOpen; switchTab("misc", true); }).bounds(cX - 100, sY + 25, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Цены в тултипах: " + (OptiConfig.settings.marketTooltips ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.marketTooltips = !OptiConfig.settings.marketTooltips; switchTab("misc", true); }).bounds(cX - 100, sY + 50, 200, 20).build());
            
            t1 = new EditBox(this.font, cX - 100, sY + 90, 200, 20, Component.literal("")); t1.setValue(String.valueOf(OptiConfig.settings.smartLootMinPrice)); this.addRenderableWidget(t1);
        }

        this.addRenderableWidget(Button.builder(Component.literal("§aПрименить и Закрыть"), b -> { saveSettings(); this.onClose(); }).bounds(cX - 100, this.height - 30, 200, 20).build());
    }

    private void setVm(double x, double y, double z, double p, double yw, double r, double s) {
        OptiConfig.settings.vmX = x; OptiConfig.settings.vmY = y; OptiConfig.settings.vmZ = z;
        OptiConfig.settings.vmPitch = p; OptiConfig.settings.vmYaw = yw; OptiConfig.settings.vmRoll = r; OptiConfig.settings.vmScale = s; 
        OptiConfig.saveAll();
    }

    private void switchTab(String tab, boolean doSave) { 
        if (doSave) saveSettings(); 
        currentTab = tab; 
        this.clearWidgets(); 
        this.init(); 
    }

    private void saveSettings() {
        try {
            if (currentTab.equals("market") && t1 != null && OptiConfig.settings.searchMode.equals("auto_farm")) {
                OptiConfig.settings.autoFarmBuyMultiplier = Double.parseDouble(t1.getValue()); OptiConfig.settings.autoFarmSellMultiplier = Double.parseDouble(t2.getValue()); OptiConfig.settings.autoFarmMinPrice = Long.parseLong(t3.getValue());
            } else if (currentTab.equals("market") && t1 != null && OptiConfig.settings.searchMode.equals("auto_cheap")) {
                OptiConfig.settings.autoCheapBuyMultiplier = Double.parseDouble(t1.getValue()); OptiConfig.settings.autoCheapSellMultiplier = Double.parseDouble(t2.getValue());
            } else if (currentTab.equals("combat") && t1 != null) {
                OptiConfig.settings.aimAssistFov = Double.parseDouble(t1.getValue()); OptiConfig.settings.aimAssistRange = Double.parseDouble(t2.getValue()); OptiConfig.settings.hitboxExpander = Double.parseDouble(t3.getValue());
            } else if (currentTab.equals("render") && t1 != null) {
                OptiConfig.settings.vmX = Double.parseDouble(t1.getValue()); OptiConfig.settings.vmY = Double.parseDouble(t2.getValue()); OptiConfig.settings.vmZ = Double.parseDouble(t3.getValue());
                OptiConfig.settings.vmPitch = Double.parseDouble(t4.getValue()); OptiConfig.settings.vmYaw = Double.parseDouble(t5.getValue()); OptiConfig.settings.vmRoll = Double.parseDouble(t6.getValue());
                OptiConfig.settings.vmScale = Double.parseDouble(t7.getValue());
            } else if (currentTab.equals("world") && t1 != null) {
                OptiConfig.settings.wardenEspTime = Integer.parseInt(t1.getValue());
                OptiConfig.settings.wardenTextScale = Double.parseDouble(t2.getValue());
            } else if (currentTab.equals("misc") && t1 != null) {
                OptiConfig.settings.smartLootMinPrice = Long.parseLong(t1.getValue());
            }
            OptiConfig.saveAll();
        } catch (Exception ignored) {}
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int sY = 40, cX = this.width / 2;

        if (currentTab.equals("market")) {
            if (!OptiConfig.settings.searchMode.equals("targeted")) guiGraphics.drawString(this.font, "Купить | Продать множитель:", (int)(cX - 100), (int)(sY + 53), 0xAAAAAA, true);
            if (OptiConfig.settings.searchMode.equals("auto_farm")) guiGraphics.drawString(this.font, "Мин. цена:", (int)(cX - 100), (int)(sY + 93), 0xAAAAAA, true);
        } else if (currentTab.equals("combat")) {
            guiGraphics.drawString(this.font, "Магнит: Угол | Дистанция:", (int)(cX - 100), (int)(sY + 53), 0xAAAAAA, true);
            guiGraphics.drawString(this.font, "HitBox Expander (0.0-1.0):", (int)(cX - 100), (int)(sY + 93), 0xAAAAAA, true);
        } else if (currentTab.equals("render")) {
            guiGraphics.drawString(this.font, "Pos: X | Y | Z    Rot: Pitch | Yaw | Roll", (int)(cX - 145), (int)(sY + 108), 0xAAAAAA, true);
            guiGraphics.drawString(this.font, "Scale:", (int)(cX - 25), (int)(sY + 148), 0xAAAAAA, true);
        } else if (currentTab.equals("world")) {
            guiGraphics.drawString(this.font, "Время Warden ESP (сек):", (int)(cX - 100), (int)(sY + 78), 0xAAAAAA, true);
            guiGraphics.drawString(this.font, "Размер текста Warden:", (int)(cX + 5), (int)(sY + 78), 0xAAAAAA, true);
        } else if (currentTab.equals("misc")) {
            guiGraphics.drawString(this.font, "Мин. цена SmartLoot:", (int)(cX - 100), (int)(sY + 78), 0xAAAAAA, true);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
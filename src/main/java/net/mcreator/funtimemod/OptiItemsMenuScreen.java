package net.mcreator.funtime_mod.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class OptiItemsMenuScreen extends Screen {

    private static String currentTab = "market"; // market, combat, render, world

    // Динамические поля ввода
    private EditBox text1, text2, text3;

    public OptiItemsMenuScreen() { super(Component.literal("")); }

    @Override
    protected void init() {
        super.init();
        int cX = this.width / 2;

        // Навигация (4 вкладки)
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("market") ? "§eMarket" : "Market"), b -> switchTab("market")).bounds(cX - 205, 10, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("combat") ? "§eCombat" : "Combat"), b -> switchTab("combat")).bounds(cX - 100, 10, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("render") ? "§eRender" : "Render"), b -> switchTab("render")).bounds(cX + 5, 10, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("world") ? "§eWorld" : "World"), b -> switchTab("world")).bounds(cX + 110, 10, 95, 20).build());

        int sY = 45;

        // ================= TAB: MARKET =================
        if (currentTab.equals("market")) {
            this.addRenderableWidget(Button.builder(Component.literal("Режим: " + AutoBuyHandler.getModeName()), b -> { AutoBuyHandler.cycleMode(); switchTab("market"); }).bounds(cX - 100, sY, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Автопродажа: " + (AutoBuyHandler.isAutoSell() ? "§aВКЛ" : "§cВЫКЛ")), b -> { AutoBuyHandler.toggleAutoSell(); b.setMessage(Component.literal("Автопродажа: " + (AutoBuyHandler.isAutoSell() ? "§aВКЛ" : "§cВЫКЛ"))); }).bounds(cX - 100, sY + 25, 200, 20).build());

            String mode = AutoBuyHandler.getRawMode();
            if (!mode.equals("targeted")) {
                text1 = new EditBox(this.font, cX - 100, sY + 65, 200, 20, Component.literal(""));
                text2 = new EditBox(this.font, cX - 100, sY + 105, 200, 20, Component.literal(""));
                text1.setValue(String.valueOf(mode.equals("auto_farm") ? AutoBuyHandler.settings.autoFarmBuyMultiplier : AutoBuyHandler.settings.autoCheapBuyMultiplier));
                text2.setValue(String.valueOf(mode.equals("auto_farm") ? AutoBuyHandler.settings.autoFarmSellMultiplier : AutoBuyHandler.settings.autoCheapSellMultiplier));
                this.addRenderableWidget(text1); this.addRenderableWidget(text2);
            }

            if (mode.equals("auto_farm")) {
                text3 = new EditBox(this.font, cX - 100, sY + 145, 200, 20, Component.literal(""));
                text3.setValue(String.valueOf(AutoBuyHandler.settings.autoFarmMinPrice));
                this.addRenderableWidget(text3);
            } else {
                this.addRenderableWidget(Button.builder(Component.literal("§eКаталог предметов"), b -> { saveSettings(); this.minecraft.setScreen(new OptiItemsListScreen(this, mode)); }).bounds(cX - 100, sY + (mode.equals("targeted") ? 65 : 145), 200, 20).build());
            }
        }
        // ================= TAB: COMBAT =================
        else if (currentTab.equals("combat")) {
            this.addRenderableWidget(Button.builder(Component.literal("Auto-Totem: " + (AutoBuyHandler.settings.autoTotem ? "§aВКЛ" : "§cВЫКЛ")), b -> { AutoBuyHandler.settings.autoTotem = !AutoBuyHandler.settings.autoTotem; switchTab("combat"); }).bounds(cX - 100, sY, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Swap по Здоровью: " + (AutoBuyHandler.settings.healthSwap ? "§aВКЛ" : "§cВЫКЛ")), b -> { AutoBuyHandler.settings.healthSwap = !AutoBuyHandler.settings.healthSwap; switchTab("combat"); }).bounds(cX - 100, sY + 25, 200, 20).build());
            
            // Исправленная кнопка
            Button tacticalBtn = Button.builder(Component.literal("Tactical Swap (X)"), b -> {}).bounds(cX - 100, sY + 155, 200, 20).build();
            tacticalBtn.active = false;
            this.addRenderableWidget(tacticalBtn);

            text1 = new EditBox(this.font, cX - 100, sY + 65, 200, 20, Component.literal(""));
            text1.setValue(String.valueOf(AutoBuyHandler.settings.healthThreshold));
            this.addRenderableWidget(text1);

            text2 = new EditBox(this.font, cX - 100, sY + 115, 200, 20, Component.literal(""));
            text2.setValue(AutoBuyHandler.settings.gSwapItem);
            this.addRenderableWidget(text2);
        }
        // ================= TAB: RENDER =================
        else if (currentTab.equals("render")) {
            this.addRenderableWidget(Button.builder(Component.literal("NoHurtCam (Анти-Тряска): " + (AutoBuyHandler.settings.noHurtCam ? "§aВКЛ" : "§cВЫКЛ")), b -> { AutoBuyHandler.settings.noHurtCam = !AutoBuyHandler.settings.noHurtCam; switchTab("render"); }).bounds(cX - 100, sY, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Анти-Слепота/Тошнота: " + (AutoBuyHandler.settings.noBadEffects ? "§aВКЛ" : "§cВЫКЛ")), b -> { AutoBuyHandler.settings.noBadEffects = !AutoBuyHandler.settings.noBadEffects; switchTab("render"); }).bounds(cX - 100, sY + 25, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("AH Tooltips: " + (AutoBuyHandler.settings.marketTooltips ? "§aВКЛ" : "§cВЫКЛ")), b -> { AutoBuyHandler.settings.marketTooltips = !AutoBuyHandler.settings.marketTooltips; switchTab("render"); }).bounds(cX - 100, sY + 50, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Dropped Item ESP: " + (AutoBuyHandler.settings.itemEsp ? "§aВКЛ" : "§cВЫКЛ")), b -> { AutoBuyHandler.settings.itemEsp = !AutoBuyHandler.settings.itemEsp; switchTab("render"); }).bounds(cX - 100, sY + 75, 200, 20).build());

            text1 = new EditBox(this.font, cX - 100, sY + 115, 200, 20, Component.literal(""));
            text1.setValue(String.valueOf(AutoBuyHandler.settings.itemEspMinPrice));
            this.addRenderableWidget(text1);
        }
        // ================= TAB: WORLD =================
        else if (currentTab.equals("world")) {
            // Исправленные кнопки
            Button smartLootBtn = Button.builder(Component.literal("Smart Loot (Кнопка V)"), b -> {}).bounds(cX - 100, sY, 200, 20).build();
            smartLootBtn.active = false;
            this.addRenderableWidget(smartLootBtn);

            Button freecamBtn = Button.builder(Component.literal("Safe Freecam (Кнопка C)"), b -> {}).bounds(cX - 100, sY + 75, 200, 20).build();
            freecamBtn.active = false;
            this.addRenderableWidget(freecamBtn);

            text1 = new EditBox(this.font, cX - 100, sY + 40, 200, 20, Component.literal(""));
            text1.setValue(String.valueOf(AutoBuyHandler.settings.smartLootMinPrice));
            this.addRenderableWidget(text1);
        }

        this.addRenderableWidget(Button.builder(Component.literal("§aПрименить и Закрыть"), b -> { saveSettings(); this.onClose(); }).bounds(cX - 100, this.height - 30, 200, 20).build());
    }

    private void switchTab(String tab) { saveSettings(); currentTab = tab; this.clearWidgets(); this.init(); }

    private void saveSettings() {
        try {
            if (currentTab.equals("market") && text1 != null) {
                String mode = AutoBuyHandler.getRawMode();
                if (mode.equals("auto_farm")) {
                    AutoBuyHandler.settings.autoFarmBuyMultiplier = Double.parseDouble(text1.getValue());
                    AutoBuyHandler.settings.autoFarmSellMultiplier = Double.parseDouble(text2.getValue());
                    AutoBuyHandler.settings.autoFarmMinPrice = Long.parseLong(text3.getValue());
                } else if (mode.equals("auto_cheap")) {
                    AutoBuyHandler.settings.autoCheapBuyMultiplier = Double.parseDouble(text1.getValue());
                    AutoBuyHandler.settings.autoCheapSellMultiplier = Double.parseDouble(text2.getValue());
                }
            } else if (currentTab.equals("combat") && text1 != null) {
                AutoBuyHandler.settings.healthThreshold = Double.parseDouble(text1.getValue());
                AutoBuyHandler.settings.gSwapItem = text2.getValue();
            } else if (currentTab.equals("render") && text1 != null) {
                AutoBuyHandler.settings.itemEspMinPrice = Long.parseLong(text1.getValue());
            } else if (currentTab.equals("world") && text1 != null) {
                AutoBuyHandler.settings.smartLootMinPrice = Long.parseLong(text1.getValue());
            }
            AutoBuyHandler.saveSettings();
        } catch (Exception ignored) {}
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int sY = 45, cX = this.width / 2;

        if (currentTab.equals("market")) {
            String mode = AutoBuyHandler.getRawMode();
            if (!mode.equals("targeted")) {
                guiGraphics.drawString(this.font, "Коэффициент покупки:", cX - 100, sY + 53, 0xAAAAAA);
                guiGraphics.drawString(this.font, "Коэффициент продажи:", cX - 100, sY + 93, 0xAAAAAA);
            }
            if (mode.equals("auto_farm")) guiGraphics.drawString(this.font, "Минимальная цена лота:", cX - 100, sY + 133, 0xAAAAAA);
        } else if (currentTab.equals("combat")) {
            guiGraphics.drawString(this.font, "Порог здоровья (1 ХП = пол сердца):", cX - 100, sY + 53, 0xAAAAAA);
            guiGraphics.drawString(this.font, "Предмет для горячей смены (G):", cX - 100, sY + 103, 0xAAAAAA);
        } else if (currentTab.equals("render")) {
            guiGraphics.drawString(this.font, "Мин. цена для подсветки дропа:", cX - 100, sY + 103, 0xAAAAAA);
        } else if (currentTab.equals("world")) {
            guiGraphics.drawString(this.font, "Мин. цена для Умного Лута:", cX - 100, sY + 28, 0xAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
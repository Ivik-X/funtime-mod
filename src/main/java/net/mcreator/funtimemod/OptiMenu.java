package net.mcreator.funtimemod.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class OptiMenu extends Screen {

    private static String currentTab = "market"; 
    private EditBox text1, text2, text3;

    public OptiMenu() { super(Component.literal("")); }

    @Override
    protected void init() {
        super.init();
        int cX = this.width / 2, sY = 45;

        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("market") ? "§eMarket" : "Market"), b -> switchTab("market")).bounds(cX - 205, 10, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("combat") ? "§eCombat" : "Combat"), b -> switchTab("combat")).bounds(cX - 100, 10, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("render") ? "§eRender" : "Render"), b -> switchTab("render")).bounds(cX + 5, 10, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(currentTab.equals("world") ? "§eWorld" : "World"), b -> switchTab("world")).bounds(cX + 110, 10, 95, 20).build());

        if (currentTab.equals("market")) {
            this.addRenderableWidget(Button.builder(Component.literal("Режим: " + (OptiConfig.settings.searchMode.equals("auto_farm") ? "Авто-Фарм" : OptiConfig.settings.searchMode.equals("auto_cheap") ? "Дешевое" : "Цели")), b -> { OptiConfig.settings.searchMode = OptiConfig.settings.searchMode.equals("auto_farm") ? "auto_cheap" : OptiConfig.settings.searchMode.equals("auto_cheap") ? "targeted" : "auto_farm"; switchTab("market"); }).bounds(cX - 100, sY, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Автопродажа: " + (OptiConfig.settings.autoSell ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.autoSell = !OptiConfig.settings.autoSell; switchTab("market"); }).bounds(cX - 100, sY + 25, 200, 20).build());

            if (!OptiConfig.settings.searchMode.equals("targeted")) {
                text1 = new EditBox(this.font, cX - 100, sY + 65, 200, 20, Component.literal(""));
                text2 = new EditBox(this.font, cX - 100, sY + 105, 200, 20, Component.literal(""));
                text1.setValue(String.valueOf(OptiConfig.settings.searchMode.equals("auto_farm") ? OptiConfig.settings.autoFarmBuyMultiplier : OptiConfig.settings.autoCheapBuyMultiplier));
                text2.setValue(String.valueOf(OptiConfig.settings.searchMode.equals("auto_farm") ? OptiConfig.settings.autoFarmSellMultiplier : OptiConfig.settings.autoCheapSellMultiplier));
                this.addRenderableWidget(text1); this.addRenderableWidget(text2);
            }

            if (OptiConfig.settings.searchMode.equals("auto_farm")) {
                text3 = new EditBox(this.font, cX - 100, sY + 145, 200, 20, Component.literal(""));
                text3.setValue(String.valueOf(OptiConfig.settings.autoFarmMinPrice));
                this.addRenderableWidget(text3);
            } else {
                this.addRenderableWidget(Button.builder(Component.literal("§eКаталог предметов"), b -> { saveSettings(); this.minecraft.setScreen(new OptiList(this, OptiConfig.settings.searchMode)); }).bounds(cX - 100, sY + (OptiConfig.settings.searchMode.equals("targeted") ? 65 : 145), 200, 20).build());
            }
        }
        else if (currentTab.equals("combat")) {
            this.addRenderableWidget(Button.builder(Component.literal("Auto-Totem: " + (OptiConfig.settings.autoTotem ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.autoTotem = !OptiConfig.settings.autoTotem; switchTab("combat"); }).bounds(cX - 100, sY, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Swap по Здоровью: " + (OptiConfig.settings.healthSwap ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.healthSwap = !OptiConfig.settings.healthSwap; switchTab("combat"); }).bounds(cX - 100, sY + 25, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Aim Assist (Магнит): " + (OptiConfig.settings.aimAssist ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.aimAssist = !OptiConfig.settings.aimAssist; switchTab("combat"); }).bounds(cX - 100, sY + 50, 200, 20).build());

            text1 = new EditBox(this.font, cX - 100, sY + 85, 200, 20, Component.literal(""));
            text1.setValue(String.valueOf(OptiConfig.settings.healthThreshold));
            this.addRenderableWidget(text1);

            text2 = new EditBox(this.font, cX - 100, sY + 125, 200, 20, Component.literal(""));
            text2.setValue(String.valueOf(OptiConfig.settings.aimAssistFov));
            this.addRenderableWidget(text2);

            text3 = new EditBox(this.font, cX - 100, sY + 165, 200, 20, Component.literal(""));
            text3.setValue(String.valueOf(OptiConfig.settings.aimAssistRange));
            this.addRenderableWidget(text3);
        }
        else if (currentTab.equals("render")) {
            this.addRenderableWidget(Button.builder(Component.literal("NoHurtCam (Анти-Тряска): " + (OptiConfig.settings.noHurtCam ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.noHurtCam = !OptiConfig.settings.noHurtCam; switchTab("render"); }).bounds(cX - 100, sY, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Анти-Слепота/Тошнота: " + (OptiConfig.settings.noBadEffects ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.noBadEffects = !OptiConfig.settings.noBadEffects; switchTab("render"); }).bounds(cX - 100, sY + 25, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("AH Tooltips: " + (OptiConfig.settings.marketTooltips ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.marketTooltips = !OptiConfig.settings.marketTooltips; switchTab("render"); }).bounds(cX - 100, sY + 50, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Dropped Item ESP: " + (OptiConfig.settings.itemEsp ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.itemEsp = !OptiConfig.settings.itemEsp; switchTab("render"); }).bounds(cX - 100, sY + 75, 200, 20).build());

            text1 = new EditBox(this.font, cX - 100, sY + 115, 200, 20, Component.literal(""));
            text1.setValue(String.valueOf(OptiConfig.settings.itemEspMinPrice));
            this.addRenderableWidget(text1);
        }
        else if (currentTab.equals("world")) {
            this.addRenderableWidget(Button.builder(Component.literal("Smart Loot: " + (OptiConfig.settings.autoStealOnOpen ? "Авто-Открытие" : "Ручной (V)")), b -> { OptiConfig.settings.autoStealOnOpen = !OptiConfig.settings.autoStealOnOpen; b.setMessage(Component.literal("Smart Loot: " + (OptiConfig.settings.autoStealOnOpen ? "Авто-Открытие" : "Ручной (V)"))); }).bounds(cX - 100, sY, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Warden ESP: " + (OptiConfig.settings.wardenEsp ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.wardenEsp = !OptiConfig.settings.wardenEsp; b.setMessage(Component.literal("Warden ESP: " + (OptiConfig.settings.wardenEsp ? "§aВКЛ" : "§cВЫКЛ"))); }).bounds(cX - 100, sY + 25, 200, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Warden Auto-Open: " + (OptiConfig.settings.wardenAutoOpen ? "§aВКЛ" : "§cВЫКЛ")), b -> { OptiConfig.settings.wardenAutoOpen = !OptiConfig.settings.wardenAutoOpen; b.setMessage(Component.literal("Warden Auto-Open: " + (OptiConfig.settings.wardenAutoOpen ? "§aВКЛ" : "§cВЫКЛ"))); }).bounds(cX - 100, sY + 50, 200, 20).build());
            
            Button fBtn = Button.builder(Component.literal("Safe Freecam (Кнопка C)"), b -> {}).bounds(cX - 100, sY + 75, 200, 20).build(); fBtn.active = false; this.addRenderableWidget(fBtn);

            text1 = new EditBox(this.font, cX - 100, sY + 115, 200, 20, Component.literal(""));
            text1.setValue(String.valueOf(OptiConfig.settings.smartLootMinPrice));
            this.addRenderableWidget(text1);

            text2 = new EditBox(this.font, cX - 100, sY + 155, 200, 20, Component.literal(""));
            text2.setValue(String.valueOf(OptiConfig.settings.wardenEspTime));
            this.addRenderableWidget(text2);
        }

        this.addRenderableWidget(Button.builder(Component.literal("§aПрименить и Закрыть"), b -> { saveSettings(); this.onClose(); }).bounds(cX - 100, this.height - 30, 200, 20).build());
    }

    private void switchTab(String tab) { saveSettings(); currentTab = tab; this.clearWidgets(); this.init(); }

    private void saveSettings() {
        try {
            if (currentTab.equals("market") && text1 != null) {
                if (OptiConfig.settings.searchMode.equals("auto_farm")) {
                    OptiConfig.settings.autoFarmBuyMultiplier = Double.parseDouble(text1.getValue());
                    OptiConfig.settings.autoFarmSellMultiplier = Double.parseDouble(text2.getValue());
                    OptiConfig.settings.autoFarmMinPrice = Long.parseLong(text3.getValue());
                } else if (OptiConfig.settings.searchMode.equals("auto_cheap")) {
                    OptiConfig.settings.autoCheapBuyMultiplier = Double.parseDouble(text1.getValue());
                    OptiConfig.settings.autoCheapSellMultiplier = Double.parseDouble(text2.getValue());
                }
            } else if (currentTab.equals("combat") && text1 != null) {
                OptiConfig.settings.healthThreshold = Double.parseDouble(text1.getValue());
                OptiConfig.settings.aimAssistFov = Double.parseDouble(text2.getValue());
                OptiConfig.settings.aimAssistRange = Double.parseDouble(text3.getValue());
            } else if (currentTab.equals("render") && text1 != null) {
                OptiConfig.settings.itemEspMinPrice = Long.parseLong(text1.getValue());
            } else if (currentTab.equals("world") && text1 != null) {
                OptiConfig.settings.smartLootMinPrice = Long.parseLong(text1.getValue());
                OptiConfig.settings.wardenEspTime = Integer.parseInt(text2.getValue());
            }
            OptiConfig.saveAll();
        } catch (Exception ignored) {}
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int sY = 45, cX = this.width / 2;

        if (currentTab.equals("market")) {
            if (!OptiConfig.settings.searchMode.equals("targeted")) {
                guiGraphics.drawString(this.font, "Коэффициент покупки:", cX - 100, sY + 53, 0xAAAAAA);
                guiGraphics.drawString(this.font, "Коэффициент продажи:", cX - 100, sY + 93, 0xAAAAAA);
            }
            if (OptiConfig.settings.searchMode.equals("auto_farm")) guiGraphics.drawString(this.font, "Минимальная цена лота:", cX - 100, sY + 133, 0xAAAAAA);
        } else if (currentTab.equals("combat")) {
            guiGraphics.drawString(this.font, "Порог здоровья (1 ХП = пол сердца):", cX - 100, sY + 73, 0xAAAAAA);
            guiGraphics.drawString(this.font, "Угол захвата магнита (градусы):", cX - 100, sY + 113, 0xAAAAAA);
            guiGraphics.drawString(this.font, "Дальность магнита (блоки):", cX - 100, sY + 153, 0xAAAAAA);
        } else if (currentTab.equals("render")) {
            guiGraphics.drawString(this.font, "Мин. цена для подсветки дропа:", cX - 100, sY + 103, 0xAAAAAA);
        } else if (currentTab.equals("world")) {
            guiGraphics.drawString(this.font, "Мин. цена для Умного Лута:", cX - 100, sY + 103, 0xAAAAAA);
            guiGraphics.drawString(this.font, "Время для подсветки Warden (сек):", cX - 100, sY + 143, 0xAAAAAA);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
package net.mcreator.funtime_mod.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class OptiItemsListScreen extends Screen {

    private final Screen parent;
    private final String mode;
    private EditBox searchBox;
    private EditBox maxPriceBox;

    private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();

    public OptiItemsListScreen(Screen parent, String mode) {
        super(Component.literal(""));
        this.parent = parent;
        this.mode = mode;
    }

    @Override
    protected void init() {
        super.init();
        dynamicWidgets.clear(); 
        
        int centerX = this.width / 2;

        searchBox = new EditBox(this.font, centerX - 100, 30, 200, 20, Component.literal(""));
        searchBox.setResponder(text -> updateDynamicList()); 
        this.addRenderableWidget(searchBox);

        if (mode.equals("targeted")) {
            maxPriceBox = new EditBox(this.font, centerX - 100, 65, 200, 20, Component.literal(""));
            maxPriceBox.setValue("100000"); 
            this.addRenderableWidget(maxPriceBox);
        }

        this.addRenderableWidget(Button.builder(Component.literal("§aДобавить"), button -> {
            String item = searchBox.getValue().trim();
            if (!item.isEmpty()) {
                if (mode.equals("auto_cheap") && !AutoBuyHandler.settings.autoCheapItems.contains(item)) {
                    AutoBuyHandler.settings.autoCheapItems.add(item);
                } else if (mode.equals("targeted")) {
                    try {
                        long price = Long.parseLong(maxPriceBox.getValue());
                        AutoBuyHandler.settings.targetedItems.removeIf(t -> t.name.equals(item));
                        AutoBuyHandler.settings.targetedItems.add(new AutoBuyHandler.BuyTarget(item, price));
                    } catch (Exception ignored) {}
                }
                AutoBuyHandler.saveSettings();
                searchBox.setValue(""); 
                updateDynamicList();
            }
        }).bounds(centerX + 105, 30, 60, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Назад"), button -> {
            this.minecraft.setScreen(parent);
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());
        
        updateDynamicList();
    }

    private void updateDynamicList() {
        for (AbstractWidget widget : dynamicWidgets) {
            this.removeWidget(widget);
        }
        dynamicWidgets.clear();

        int centerX = this.width / 2;
        String query = searchBox.getValue().toLowerCase().replaceAll("§[0-9a-fk-or]", "");

        int yOffset = mode.equals("targeted") ? 95 : 60;
        
        if (!query.isEmpty()) {
            List<String> matches = AutoBuyHandler.getCatalogNames().stream()
                .filter(name -> name.toLowerCase().replaceAll("§[0-9a-fk-or]", "").contains(query))
                .limit(3).collect(Collectors.toList());
            
            for (String match : matches) {
                Button btn = Button.builder(Component.literal(match), button -> {
                    searchBox.setValue(match); 
                }).bounds(centerX - 100, yOffset, 200, 20).build();
                
                this.addRenderableWidget(btn);
                dynamicWidgets.add(btn);
                yOffset += 22;
            }
        }

        yOffset += 10;
        if (mode.equals("auto_cheap")) {
            int startIdx = Math.max(0, AutoBuyHandler.settings.autoCheapItems.size() - 5);
            for (int i = startIdx; i < AutoBuyHandler.settings.autoCheapItems.size(); i++) {
                String item = AutoBuyHandler.settings.autoCheapItems.get(i);
                Button btn = Button.builder(Component.literal("§c[X] §r" + item), button -> {
                    AutoBuyHandler.settings.autoCheapItems.remove(item);
                    AutoBuyHandler.saveSettings();
                    updateDynamicList();
                }).bounds(centerX - 100, yOffset, 200, 20).build();
                
                this.addRenderableWidget(btn);
                dynamicWidgets.add(btn);
                yOffset += 22;
            }
        } else if (mode.equals("targeted")) {
            int startIdx = Math.max(0, AutoBuyHandler.settings.targetedItems.size() - 4);
            for (int i = startIdx; i < AutoBuyHandler.settings.targetedItems.size(); i++) {
                AutoBuyHandler.BuyTarget target = AutoBuyHandler.settings.targetedItems.get(i);
                Button btn = Button.builder(Component.literal("§c[X] §r" + target.name + " (<" + target.maxPrice + ")"), button -> {
                    AutoBuyHandler.settings.targetedItems.remove(target);
                    AutoBuyHandler.saveSettings();
                    updateDynamicList();
                }).bounds(centerX - 100, yOffset, 200, 20).build();
                
                this.addRenderableWidget(btn);
                dynamicWidgets.add(btn);
                yOffset += 22;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, "§lМенеджер списка (" + (mode.equals("targeted") ? "Цели" : "Дешевое") + ")", this.width / 2, 10, 0xFFFFFF);
        
        // Убрали false для включения теней шрифта
        guiGraphics.drawString(this.font, "Название предмета (или поиск по кэшу):", this.width / 2 - 100, 18, 0xAAAAAA);
        if (mode.equals("targeted")) {
            guiGraphics.drawString(this.font, "Максимальная цена покупки:", this.width / 2 - 100, 53, 0xAAAAAA);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
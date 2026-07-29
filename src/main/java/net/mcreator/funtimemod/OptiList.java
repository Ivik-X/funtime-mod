package net.mcreator.funtimemod.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class OptiList extends Screen {

    private final Screen parent;
    private final String mode;
    private EditBox searchBox, maxPriceBox;
    private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();

    public OptiList(Screen parent, String mode) { super(Component.literal("")); this.parent = parent; this.mode = mode; }

    @Override
    protected void init() {
        super.init();
        dynamicWidgets.clear(); 
        int cX = this.width / 2;

        searchBox = new EditBox(this.font, cX - 100, 30, 200, 20, Component.literal(""));
        searchBox.setResponder(text -> updateDynamicList()); 
        this.addRenderableWidget(searchBox);

        if (mode.equals("targeted")) {
            maxPriceBox = new EditBox(this.font, cX - 100, 65, 200, 20, Component.literal(""));
            maxPriceBox.setValue("100000"); 
            this.addRenderableWidget(maxPriceBox);
        }

        this.addRenderableWidget(Button.builder(Component.literal("§aДобавить"), b -> {
            String item = searchBox.getValue().trim();
            if (!item.isEmpty()) {
                if (mode.equals("auto_cheap") && !OptiConfig.settings.autoCheapItems.contains(item)) { OptiConfig.settings.autoCheapItems.add(item); } 
                else if (mode.equals("targeted")) { try { OptiConfig.settings.targetedItems.removeIf(t -> t.name.equals(item)); OptiConfig.settings.targetedItems.add(new OptiConfig.BuyTarget(item, Long.parseLong(maxPriceBox.getValue()))); } catch (Exception ignored) {} }
                OptiConfig.saveAll(); searchBox.setValue(""); updateDynamicList();
            }
        }).bounds(cX + 105, 30, 60, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Назад"), b -> this.minecraft.setScreen(parent)).bounds(cX - 100, this.height - 30, 200, 20).build());
        updateDynamicList();
    }

    private void updateDynamicList() {
        for (AbstractWidget widget : dynamicWidgets) this.removeWidget(widget);
        dynamicWidgets.clear();

        int cX = this.width / 2, yOffset = mode.equals("targeted") ? 95 : 60;
        String query = searchBox.getValue().toLowerCase().replaceAll("§[0-9a-fk-or]", "");
        
        if (!query.isEmpty()) {
            List<String> matches = OptiConfig.catalog.marketPrices.keySet().stream().filter(n -> n.toLowerCase().replaceAll("§[0-9a-fk-or]", "").contains(query)).limit(3).collect(Collectors.toList());
            for (String match : matches) {
                Button btn = Button.builder(Component.literal(match), b -> searchBox.setValue(match)).bounds(cX - 100, yOffset, 200, 20).build();
                this.addRenderableWidget(btn); dynamicWidgets.add(btn); yOffset += 22;
            }
        }

        yOffset += 10;
        if (mode.equals("auto_cheap")) {
            int startIdx = Math.max(0, OptiConfig.settings.autoCheapItems.size() - 5);
            for (int i = startIdx; i < OptiConfig.settings.autoCheapItems.size(); i++) {
                String item = OptiConfig.settings.autoCheapItems.get(i);
                Button btn = Button.builder(Component.literal("§c[X] §r" + item), b -> { OptiConfig.settings.autoCheapItems.remove(item); OptiConfig.saveAll(); updateDynamicList(); }).bounds(cX - 100, yOffset, 200, 20).build();
                this.addRenderableWidget(btn); dynamicWidgets.add(btn); yOffset += 22;
            }
        } else if (mode.equals("targeted")) {
            int startIdx = Math.max(0, OptiConfig.settings.targetedItems.size() - 4);
            for (int i = startIdx; i < OptiConfig.settings.targetedItems.size(); i++) {
                OptiConfig.BuyTarget target = OptiConfig.settings.targetedItems.get(i);
                Button btn = Button.builder(Component.literal("§c[X] §r" + target.name + " (<" + target.maxPrice + ")"), b -> { OptiConfig.settings.targetedItems.remove(target); OptiConfig.saveAll(); updateDynamicList(); }).bounds(cX - 100, yOffset, 200, 20).build();
                this.addRenderableWidget(btn); dynamicWidgets.add(btn); yOffset += 22;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, "§lМенеджер списка", this.width / 2, 10, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Название предмета:", this.width / 2 - 100, 18, 0xAAAAAA);
        if (mode.equals("targeted")) guiGraphics.drawString(this.font, "Макс. цена:", this.width / 2 - 100, 53, 0xAAAAAA);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
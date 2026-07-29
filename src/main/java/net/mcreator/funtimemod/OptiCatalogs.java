package net.mcreator.funtimemod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

public class OptiCatalogs extends Screen {
    private final Screen parent;
    private final String mode; 
    private EditBox searchBox;
    private List<ItemStack> displayItems = new ArrayList<>();
    private int scrollOffset = 0;

    public OptiCatalogs(Screen parent, String mode) {
        super(Component.literal("Catalog"));
        this.parent = parent; this.mode = mode;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(this.font, this.width / 2 - 100, 20, 200, 20, Component.literal("Поиск..."));
        this.addRenderableWidget(searchBox);
        this.addRenderableWidget(Button.builder(Component.literal("Назад"), b -> { OptiConfig.saveAll(); this.minecraft.setScreen(parent); }).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
        updateList("");
    }

    private void updateList(String query) {
        displayItems.clear(); scrollOffset = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mode.equals("blocks")) {
            for (Block block : BuiltInRegistries.BLOCK) {
                ItemStack stack = new ItemStack(block);
                if (stack.isEmpty() || stack.getItem() == Items.AIR) continue;
                String name = stack.getHoverName().getString().toLowerCase();
                if (query.isEmpty() || name.contains(query.toLowerCase())) displayItems.add(stack);
            }
        } else if (mode.equals("potions")) {
            if (mc.player != null) {
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (stack.getItem() instanceof net.minecraft.world.item.SplashPotionItem) {
                        boolean exists = false;
                        for (ItemStack d : displayItems) { if (d.getHoverName().getString().equals(stack.getHoverName().getString())) { exists = true; break; } }
                        if (!exists) displayItems.add(stack.copy());
                    }
                }
            }
        } else if (mode.equals("cheap") || mode.equals("targets")) {
            for (net.minecraft.world.item.Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty() || stack.getItem() == Items.AIR) continue;
                String name = stack.getHoverName().getString().toLowerCase();
                if (query.isEmpty() || name.contains(query.toLowerCase())) displayItems.add(stack);
            }
        }
    }

    @Override
    public boolean charTyped(char pCodePoint, int pModifiers) {
        boolean res = super.charTyped(pCodePoint, pModifiers); updateList(searchBox.getValue()); return res;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { boolean r = super.keyPressed(keyCode, scanCode, modifiers); updateList(searchBox.getValue()); return r; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int) (scrollY * 20); if (scrollOffset < 0) scrollOffset = 0; return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int startX = this.width / 2 - 120, startY = 50 - scrollOffset;
        int col = 0, row = 0;
        for (ItemStack stack : displayItems) {
            int x = startX + col * 24; int y = startY + row * 24;
            if (mouseY > 40 && mouseY < this.height - 40 && mouseX >= x && mouseX <= x + 20 && mouseY >= y && mouseY <= y + 20) {
                if (mode.equals("blocks")) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (OptiConfig.settings.blockEspList.contains(id)) OptiConfig.settings.blockEspList.remove(id);
                    else OptiConfig.settings.blockEspList.add(id);
                } else if (mode.equals("potions")) {
                    String name = stack.getHoverName().getString();
                    if (OptiConfig.settings.autoBuffPotions.contains(name)) OptiConfig.settings.autoBuffPotions.remove(name);
                    else OptiConfig.settings.autoBuffPotions.add(name);
                } else if (mode.equals("cheap")) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (OptiConfig.settings.autoCheapItems.contains(id)) OptiConfig.settings.autoCheapItems.remove(id);
                    else OptiConfig.settings.autoCheapItems.add(id);
                } else if (mode.equals("targets")) {
                    String name = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
                    boolean removed = false;
                    for (OptiConfig.BuyTarget bt : OptiConfig.settings.targetedItems) {
                        if (bt.name.equals(name)) { OptiConfig.settings.targetedItems.remove(bt); removed = true; break; }
                    }
                    if (!removed) OptiConfig.settings.targetedItems.add(new OptiConfig.BuyTarget(name, 1000000));
                }
                return true;
            }
            col++; if (col > 9) { col = 0; row++; }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        int startX = this.width / 2 - 120, startY = 50 - scrollOffset;
        int col = 0, row = 0;

        g.enableScissor(0, 50, this.width, this.height - 40);
        for (ItemStack stack : displayItems) {
            int x = startX + col * 24; int y = startY + row * 24;
            boolean isSelected = false;
            if (mode.equals("blocks")) isSelected = OptiConfig.settings.blockEspList.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            else if (mode.equals("potions")) isSelected = OptiConfig.settings.autoBuffPotions.contains(stack.getHoverName().getString());
            else if (mode.equals("cheap")) isSelected = OptiConfig.settings.autoCheapItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            else if (mode.equals("targets")) {
                String cleanName = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
                for (OptiConfig.BuyTarget bt : OptiConfig.settings.targetedItems) if (bt.name.equals(cleanName)) { isSelected = true; break; }
            }

            if (isSelected) g.fill(x - 2, y - 2, x + 18, y + 18, 0x6600FF00);
            
            g.renderItem(stack, x, y);
            if (mouseX >= x && mouseX <= x + 20 && mouseY >= y && mouseY <= y + 20) g.renderTooltip(this.font, stack, mouseX, mouseY);
            
            col++; if (col > 9) { col = 0; row++; }
        }
        g.disableScissor();
        g.drawString(this.font, "Выберите предметы:", this.width / 2 - 100, 5, 0xFFFFFF);
    }
}
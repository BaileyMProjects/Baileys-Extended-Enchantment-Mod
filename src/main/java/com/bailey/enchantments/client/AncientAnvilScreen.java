package com.bailey.enchantments.client;

import java.util.ArrayList;
import java.util.List;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.menu.AncientAnvilMenu;
import com.bailey.enchantments.network.ModNetwork;
import com.bailey.enchantments.network.RenameAnvilPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

// Tabbed GUI for the Ancient Anvil: a Forge tab (repair / rename / boosted-cap
// merge) and an Ultimate tab (gear + Echo Upgrade Template + Dark Crystal ->
// ultimate enchantment).
public class AncientAnvilScreen extends AbstractContainerScreen<AncientAnvilMenu> {
    private static final ResourceLocation ANVIL_LOCATION = new ResourceLocation(
            BaileysEnchantments.MODID, "textures/gui/container/ancient_anvil_forge.png");
    private static final ResourceLocation SMITHING_LOCATION = new ResourceLocation(
            BaileysEnchantments.MODID, "textures/gui/container/ancient_anvil_ultimate.png");

    // 3 tabs at 54 + 2 gap = 173px, which still fits inside the 176px panel. Widening
    // this pushes the third tab off the edge.
    private static final int TAB_COUNT = 3;
    private static final int TAB_W = 54;
    private static final int TAB_H = 18;


    // The Secret toggle, in panel-local coordinates. Sits in the strip between the ritual
    // slots (y=48..66) and the player inventory (y=84), which is empty on this tab.
    // ⚠️ mouseClicked converts these with leftPos/topPos; renderLabels draws them as-is,
    // because it runs inside the panel-translated matrix. Do not add the offsets twice.
    private static final int SECRET_TOGGLE_X = 8;
    private static final int SECRET_TOGGLE_Y = 68;
    private static final int SECRET_TOGGLE_W = 62;
    private static final int SECRET_TOGGLE_H = 12;

    // The Secret tier is red, against the Ultimates' pink 0xFF85C2 — Bailey, 2026-08-09:
    // "it should be like a red themed enchantment rather than pink".
    // Chrome drawn in code has to match the panel texture, which is deepslate, not vanilla
    // grey. These mirror GuiGen's palette in tools/GuiGen.java � change both together.
    private static final int PANEL_OUTLINE = 0xFF0E1014;
    private static final int PANEL_BASE    = 0xFF474E59;
    private static final int PANEL_LO      = 0xFF343A42;
    private static final int PANEL_HI      = 0xFF737E8D;
    private static final int PANEL_WELL    = 0xFF272C33;

    private static final int SECRET_RED = 0xFFE0293A;
    private static final int SECRET_RED_DIM = 0xFF7A1721;

    private EditBox nameField;
    private String lastBaseName = "";

    public AncientAnvilScreen(AncientAnvilMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        this.nameField = new EditBox(this.font, x + 62, y + 24, 103, 12, Component.translatable("container.repair"));
        this.nameField.setCanLoseFocus(true);
        this.nameField.setTextColor(0xFFFFFFFF);
        this.nameField.setBordered(false);
        this.nameField.setMaxLength(50);
        this.nameField.setResponder(this::onNameChanged);
        this.nameField.setValue("");
        this.addWidget(this.nameField);
    }

    private void onNameChanged(String name) {
        ModNetwork.CHANNEL.sendToServer(new RenameAnvilPayload(name));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        String baseName = this.menu.getSlot(0).getItem().getHoverName().getString();
        if (!baseName.equals(this.lastBaseName) && !this.nameField.isFocused()) {
            this.lastBaseName = baseName;
            this.nameField.setValue(this.menu.getSlot(0).hasItem() ? baseName : "");
        }
        this.nameField.setVisible(this.menu.getActiveTab() == AncientAnvilMenu.TAB_FORGE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tab = this.tabAt(mouseX, mouseY);
        if (tab != -1 && this.menu.clickMenuButton(this.minecraft.player, tab)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, tab);
            return true;
        }
        // The Secret toggle. Only live on the Ultimate tab, because that is the ritual it
        // modifies. Checked BEFORE the cycle button so the two can never fight over a click
        // if their rectangles are ever moved together.
        if (this.menu.getActiveTab() == AncientAnvilMenu.TAB_ULTIMATE) {
            int tx = this.leftPos + SECRET_TOGGLE_X, ty = this.topPos + SECRET_TOGGLE_Y;
            if (mouseX >= tx && mouseX < tx + SECRET_TOGGLE_W
                    && mouseY >= ty && mouseY < ty + SECRET_TOGGLE_H) {
                this.menu.clickMenuButton(this.minecraft.player, AncientAnvilMenu.BTN_TOGGLE_SECRET);
                this.minecraft.gameMode.handleInventoryButtonClick(
                        this.menu.containerId, AncientAnvilMenu.BTN_TOGGLE_SECRET);
                return true;
            }
        }
        int cycleButton = this.activeCycleButton();
        if (cycleButton != -1) {
            int bx = this.leftPos + 116, by = this.topPos + 50;
            if (mouseX >= bx && mouseX < bx + 14 && mouseY >= by && mouseY < by + 12) {
                this.menu.clickMenuButton(this.minecraft.player, cycleButton);
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, cycleButton);
                return true;
            }
        }
        if (this.menu.getActiveTab() == AncientAnvilMenu.TAB_FORGE && this.nameField.isVisible()
                && this.nameField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.nameField);
            this.nameField.setFocused(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.menu.getActiveTab() == AncientAnvilMenu.TAB_FORGE && this.nameField.isFocused()
                && this.nameField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.menu.getActiveTab() == AncientAnvilMenu.TAB_FORGE
                && this.nameField.isFocused() && keyCode != 256) {
            if (this.nameField.keyPressed(keyCode, scanCode, modifiers) || this.nameField.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // The Ultimate and Disenchant tabs share the same cycle button position; only one
    // can be on screen at a time. -1 means no cycle button is currently shown.
    private int activeCycleButton() {
        int tab = this.menu.getActiveTab();
        if (tab == AncientAnvilMenu.TAB_ULTIMATE && this.menu.getUpgradeCount() > 1) {
            return AncientAnvilMenu.BTN_CYCLE_UPGRADE;
        }
        if (tab == AncientAnvilMenu.TAB_DISENCHANT && this.menu.getDisenchantCount() > 1) {
            return AncientAnvilMenu.BTN_CYCLE_DISENCHANT;
        }
        return -1;
    }

    private int tabAt(double mouseX, double mouseY) {
        int x = this.leftPos + 5;
        int y = this.topPos - TAB_H + 1;
        for (int i = 0; i < TAB_COUNT; i++) {
            int tx = x + i * (TAB_W + 2);
            if (mouseX >= tx && mouseX < tx + TAB_W && mouseY >= y && mouseY < y + TAB_H) return i;
        }
        return -1;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        if (this.menu.getActiveTab() == AncientAnvilMenu.TAB_FORGE) {
            guiGraphics.blit(ANVIL_LOCATION, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
            if (this.menu.getSlot(0).hasItem()) {
                guiGraphics.fill(x + 59, y + 20, x + 169, y + 36, PANEL_OUTLINE);
                guiGraphics.fill(x + 60, y + 21, x + 168, y + 35, PANEL_WELL);
            }
        } else {
            guiGraphics.blit(SMITHING_LOCATION, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        }

        drawTab(guiGraphics, 0, "Forge", this.menu.getActiveTab() == AncientAnvilMenu.TAB_FORGE);
        drawTab(guiGraphics, 1, "Ultimate", this.menu.getActiveTab() == AncientAnvilMenu.TAB_ULTIMATE);
        drawTab(guiGraphics, 2, "Remove", this.menu.getActiveTab() == AncientAnvilMenu.TAB_DISENCHANT);

        if (this.activeCycleButton() != -1) {
            int bx = x + 116, by = y + 50;
            guiGraphics.fill(bx, by, bx + 14, by + 12, PANEL_OUTLINE);
            guiGraphics.fill(bx + 1, by + 1, bx + 13, by + 11, PANEL_HI);
            guiGraphics.drawString(this.font, "»", bx + 3, by + 2, 0xFFFFFFFF, false);
        }
    }

    private void drawTab(GuiGraphics guiGraphics, int index, String label, boolean active) {
        int tx = this.leftPos + 5 + index * (TAB_W + 2);
        int ty = this.topPos - TAB_H + 1;
        int bg = active ? PANEL_BASE : PANEL_LO;
        guiGraphics.fill(tx, ty, tx + TAB_W, ty + TAB_H + (active ? 1 : 0), PANEL_OUTLINE);
        guiGraphics.fill(tx + 1, ty + 1, tx + TAB_W - 1, ty + TAB_H + (active ? 1 : 0), bg);
        int textColor = active ? 0xFFE8EDF2 : 0xFF98A2AE;
        guiGraphics.drawString(this.font, label, tx + (TAB_W - this.font.width(label)) / 2, ty + 5, textColor, false);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        // Info "?" icon (top-right corner of panel)
        int ix = this.imageWidth - 15, iy = 6;
        guiGraphics.fill(ix, iy, ix + 9, iy + 9, PANEL_OUTLINE);
        guiGraphics.fill(ix + 1, iy + 1, ix + 8, iy + 8, PANEL_HI);
        guiGraphics.drawString(this.font, "?", ix + 2, iy + 1, 0xFFFFFFFF, false);

        int tab = this.menu.getActiveTab();
        if (tab == AncientAnvilMenu.TAB_FORGE) {
            int cost = this.menu.getForgeCost();
            if (cost > 0) {
                int color = canAfford(cost) ? 0xFF4AED4A : 0xFFFF6060;
                Component c = Component.translatable("container.repair.cost", cost);
                int w = this.font.width(c);
                int textX = this.imageWidth - 8 - w;
                int textY = 69;
                guiGraphics.fill(textX - 2, textY - 2, this.imageWidth - 6, textY + 10, 0x4F000000);
                guiGraphics.drawString(this.font, c, textX, textY, color, true);
            }
        } else if (tab == AncientAnvilMenu.TAB_DISENCHANT) {
            Component target = this.targetedEnchantment();
            if (target == null) {
                Component hint = Component.translatable("container." + BaileysEnchantments.MODID + ".disenchant.hint");
                guiGraphics.drawString(this.font, hint, 8, 18, 0xFFAAAAAA, false);
            } else {
                int cost = this.menu.getDisenchantCost();
                int color = canAfford(cost) ? 0xFF4AED4A : 0xFFFF6060;
                Component c = Component.translatable(
                        "container." + BaileysEnchantments.MODID + ".disenchant.cost", target, cost);
                guiGraphics.drawString(this.font, c, 8, 18, color, false);
                if (this.menu.getDisenchantCount() > 1) {
                    String indicator = (this.menu.getSelectedDisenchant() + 1) + "/" + this.menu.getDisenchantCount();
                    guiGraphics.drawString(this.font, indicator, 132, 52, 0xFFCCCCCC, false);
                }
            }
        } else if (this.menu.isUltimateReady()) {
            int cost = this.menu.getUltimateCost();
            int color = canAfford(cost) ? 0xFF4AED4A : 0xFFFF6060;
            Component c = Component.translatable("container." + BaileysEnchantments.MODID + ".ultimate.cost", cost);
            guiGraphics.drawString(this.font, c, 8, 18, color, false);
            if (this.menu.getUpgradeCount() > 1) {
                String indicator = (this.menu.getSelectedUpgrade() + 1) + "/" + this.menu.getUpgradeCount();
                guiGraphics.drawString(this.font, indicator, 132, 52, 0xFFCCCCCC, false);
            }
        } else {
            Component hint = Component.translatable("container." + BaileysEnchantments.MODID
                    + (this.menu.isSecretMode() ? ".secret.hint" : ".ultimate.hint"));
            guiGraphics.drawString(this.font, hint, 8, 18, 0xFFAAAAAA, false);
        }

        if (tab == AncientAnvilMenu.TAB_ULTIMATE) {
            drawSecretToggle(guiGraphics);
        }
    }

    // The toggle, plus the warning that has to be impossible to miss.
    //
    // ⚠️ THE WARNING IS NOT DECORATION. This ritual destroys an ULTIMATE on success
    // and on failure alike — and since 2026-09-21 an Ultimate is what it costs, the stake is
    // far higher than it was. Brain/Decisions/Secret enchantments.md is explicit: a player who
    // loses an Execute to a mechanic they did not understand will report it as a bug, not a
    // gamble — and they would be right to. The odds and the "you lose it either way" line must
    // both be on screen before the result can be taken.
    //
    // The percentage is read from the menu, which reads DeepDarkBalance, so the number shown
    // and the number rolled cannot drift apart.
    private void drawSecretToggle(GuiGraphics guiGraphics) {
        boolean on = this.menu.isSecretMode();

        guiGraphics.fill(SECRET_TOGGLE_X, SECRET_TOGGLE_Y,
                SECRET_TOGGLE_X + SECRET_TOGGLE_W, SECRET_TOGGLE_Y + SECRET_TOGGLE_H, 0xFF000000);
        guiGraphics.fill(SECRET_TOGGLE_X + 1, SECRET_TOGGLE_Y + 1,
                SECRET_TOGGLE_X + SECRET_TOGGLE_W - 1, SECRET_TOGGLE_Y + SECRET_TOGGLE_H - 1,
                on ? SECRET_RED_DIM : 0xFF555555);

        Component label = Component.translatable("container." + BaileysEnchantments.MODID + ".secret.toggle");
        int labelX = SECRET_TOGGLE_X + (SECRET_TOGGLE_W - this.font.width(label)) / 2;
        guiGraphics.drawString(this.font, label, labelX, SECRET_TOGGLE_Y + 2,
                on ? SECRET_RED : 0xFFBBBBBB, false);

        if (!on) return;

        Component odds = Component.translatable(
                "container." + BaileysEnchantments.MODID + ".secret.odds",
                AncientAnvilMenu.getSecretChancePercent());
        Component warn = Component.translatable("container." + BaileysEnchantments.MODID + ".secret.warning");
        guiGraphics.drawString(this.font, odds, SECRET_TOGGLE_X + SECRET_TOGGLE_W + 6,
                SECRET_TOGGLE_Y + 2, SECRET_RED, false);
        guiGraphics.drawString(this.font, warn, 8, 30, SECRET_RED, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (this.menu.getActiveTab() == AncientAnvilMenu.TAB_FORGE) {
            this.nameField.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        int cycleButton = this.activeCycleButton();
        if (cycleButton != -1 && this.isHovering(116, 50, 14, 12, mouseX, mouseY)) {
            String label = cycleButton == AncientAnvilMenu.BTN_CYCLE_DISENCHANT
                    ? "Cycle enchantment to remove"
                    : "Cycle upgrade enchantment";
            guiGraphics.renderComponentTooltip(this.font, List.of(Component.literal(label)), mouseX, mouseY);
        }
        if (this.isHovering(this.imageWidth - 15, 6, 9, 9, mouseX, mouseY)) {
            List<Component> info = switch (this.menu.getActiveTab()) {
                case AncientAnvilMenu.TAB_FORGE -> buildInfo("forge", "l1", "l2", "l3");
                case AncientAnvilMenu.TAB_DISENCHANT -> buildInfo("disenchant", "l1", "l2", "l3", "l4");
                default -> buildInfo("ultimate", "l1", "l2", "l3", "l4", "l5");
            };
            guiGraphics.renderComponentTooltip(this.font, info, mouseX, mouseY);
        }
    }

    private List<Component> buildInfo(String section, String... lines) {
        String base = "info." + BaileysEnchantments.MODID + "." + section + ".";
        List<Component> list = new ArrayList<>();
        list.add(Component.translatable(base + "title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (String line : lines) {
            list.add(Component.translatable(base + line).withStyle(ChatFormatting.GRAY));
        }
        return list;
    }

    // The option list itself never leaves the server; the menu syncs the targeted
    // enchantment as a registry id + level, which is enough to name it here.
    private Component targetedEnchantment() {
        int id = this.menu.getDisenchantClue();
        if (id < 0) return null;
        Enchantment ench = BuiltInRegistries.ENCHANTMENT.byId(id);
        if (ench == null) return null;
        return ench.getFullname(Math.max(1, this.menu.getDisenchantClueLevel()));
    }

    private boolean canAfford(int cost) {
        return this.minecraft.player.experienceLevel >= cost || this.minecraft.player.getAbilities().instabuild;
    }
}

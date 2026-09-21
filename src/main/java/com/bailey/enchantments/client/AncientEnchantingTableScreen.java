package com.bailey.enchantments.client;

import com.google.common.collect.Lists;
import java.util.List;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.menu.AncientEnchantmentMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;

// Client GUI for the Ancient Enchanting Table. Offers are transparent: each row
// shows the exact enchantment + level it grants. Hover tooltip reveals cost details.
public class AncientEnchantingTableScreen extends AbstractContainerScreen<AncientEnchantmentMenu> {
    private static final ResourceLocation ANCIENT_TABLE_LOCATION = new ResourceLocation(
            BaileysEnchantments.MODID, "textures/gui/container/ancient_enchanting_table.png");

    // Slot colors: disabled, enabled, highlighted
    private static final int COLOR_SLOT_DISABLED   = 0xFF444444;
    private static final int COLOR_SLOT_ENABLED     = 0xFF1A1A3A;
    private static final int COLOR_SLOT_HOVER       = 0xFF2A2A5A;
    private static final int COLOR_SLOT_BORDER      = 0xFF6644AA;

    // Orb icon colors per row: green (cheap), amber (mid), purple (expensive)
    private static final int[] ORB_RIM   = {0xFF0D5C0D, 0xFF5C4A0D, 0xFF3D1055};
    private static final int[] ORB_FILL  = {0xFF22AA22, 0xFFBBA000, 0xFF8833CC};
    private static final int[] ORB_GLINT = {0xFF55EE55, 0xFFFFDD44, 0xFFCC66FF};

    public AncientEnchantingTableScreen(AncientEnchantmentMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        for (int k = 0; k < 3; k++) {
            double d0 = mouseX - (i + 60);
            double d1 = mouseY - (j + 14 + 19 * k);
            if (d0 >= 0.0 && d1 >= 0.0 && d0 < 108.0 && d1 < 19.0
                    && this.menu.clickMenuButton(this.minecraft.player, k)) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, k);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Enchantment clueEnchantment(int row) {
        if (this.menu.enchantClue[row] == -1) return null;
        return BuiltInRegistries.ENCHANTMENT.byId(this.menu.enchantClue[row]);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(ANCIENT_TABLE_LOCATION, i, j, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        int shards = this.menu.getShardCount();
        EnchantmentNames.getInstance().initSeed((long) this.menu.getEnchantmentSeed());

        for (int l = 0; l < 3; l++) {
            int slotX = i + 60;
            int slotY = j + 14 + 19 * l;
            int requiredLevel = this.menu.costs[l];
            Enchantment clue = clueEnchantment(l);

            if (requiredLevel == 0 || clue == null) {
                // Disabled slot
                guiGraphics.fill(slotX, slotY, slotX + 108, slotY + 19, COLOR_SLOT_DISABLED);
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + 107, slotY + 18, 0xFF333333);
                continue;
            }

            boolean affordable = (shards >= AncientEnchantmentMenu.SHARD_COSTS[l]
                    && this.minecraft.player.experienceLevel >= requiredLevel)
                    || this.minecraft.player.getAbilities().instabuild;

            boolean hovered = mouseX >= slotX && mouseY >= slotY
                    && mouseX < slotX + 108 && mouseY < slotY + 19;

            int bgColor = !affordable ? COLOR_SLOT_DISABLED : hovered ? COLOR_SLOT_HOVER : COLOR_SLOT_ENABLED;
            guiGraphics.fill(slotX, slotY, slotX + 108, slotY + 19, COLOR_SLOT_BORDER);
            guiGraphics.fill(slotX + 1, slotY + 1, slotX + 107, slotY + 18, bgColor);

            // XP orb: 9x9 rounded gem, vertically centred in the 19px row
            int orbX = slotX + 3, orbY = slotY + 5;
            int orbRim   = affordable ? ORB_RIM[l]   : 0xFF2A2A2A;
            int orbFill  = affordable ? ORB_FILL[l]  : 0xFF404040;
            int orbGlint = affordable ? ORB_GLINT[l] : 0xFF484848;
            guiGraphics.fill(orbX + 1, orbY,     orbX + 8, orbY + 1, orbRim);
            guiGraphics.fill(orbX,     orbY + 1, orbX + 9, orbY + 8, orbRim);
            guiGraphics.fill(orbX + 1, orbY + 8, orbX + 8, orbY + 9, orbRim);
            guiGraphics.fill(orbX + 1, orbY + 1, orbX + 8, orbY + 8, orbFill);
            guiGraphics.fill(orbX + 2, orbY + 2, orbX + 4, orbY + 4, orbGlint);

            // Level badge (roman numeral roman) on right side
            String levelStr = requiredLevel + "";
            int textColor = affordable ? (hovered ? 0xFF77DDFF : 0xFFBBBBBB) : 0xFF666666;
            guiGraphics.drawString(this.font, levelStr, slotX + 100 - this.font.width(levelStr), slotY + 6, textColor, false);

            // Enchantment name as SGA gibberish
            int nameWidth = 80 - this.font.width(levelStr);
            FormattedText offerName = EnchantmentNames.getInstance().getRandomName(this.font, nameWidth);
            int nameColor = affordable ? (hovered ? 0xFFDDAA22 : 0xFF997733) : 0xFF555544;
            guiGraphics.drawWordWrap(this.font, offerName, slotX + 15, slotY + 6, nameWidth, nameColor);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, Component.translatable("container.enchant"),
                this.titleLabelX, this.titleLabelY, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, 0xFFFFFFFF, true);

        // Info "?" icon
        int ix = 8, iy = 16;
        guiGraphics.fill(ix, iy, ix + 9, iy + 9, 0xFF000000);
        guiGraphics.fill(ix + 1, iy + 1, ix + 8, iy + 8, 0xFF6A6A6A);
        guiGraphics.drawString(this.font, "?", ix + 2, iy + 1, 0xFFFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        boolean creative = this.minecraft.player.getAbilities().instabuild;
        int shards = this.menu.getShardCount();

        for (int row = 0; row < 3; row++) {
            int requiredLevel = this.menu.costs[row];
            Enchantment clue = clueEnchantment(row);
            if (this.isHovering(60, 14 + 19 * row, 108, 17, mouseX, mouseY) && requiredLevel > 0 && clue != null) {
                List<Component> list = Lists.newArrayList();
                list.add(Component.empty()
                        .append(clue.getFullname(this.menu.levelClue[row]))
                        .withStyle(ChatFormatting.WHITE));
                list.add(Component.translatable("container." + BaileysEnchantments.MODID + ".enchant.bonus")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
                if (!creative) {
                    list.add(CommonComponents.EMPTY);
                    if (this.minecraft.player.experienceLevel < requiredLevel) {
                        list.add(Component.translatable("container.enchant.level.requirement", requiredLevel)
                                .withStyle(ChatFormatting.RED));
                    } else {
                        int shardCost = AncientEnchantmentMenu.SHARD_COSTS[row];
                        MutableComponent shardLine = shardCost == 1
                                ? Component.translatable("container." + BaileysEnchantments.MODID + ".enchant.shard.one")
                                : Component.translatable("container." + BaileysEnchantments.MODID + ".enchant.shard.many", shardCost);
                        list.add(shardLine.withStyle(shards >= shardCost ? ChatFormatting.GRAY : ChatFormatting.RED));

                        int levelsConsumed = AncientEnchantmentMenu.LEVELS_CONSUMED[row];
                        MutableComponent levelLine = levelsConsumed == 1
                                ? Component.translatable("container.enchant.level.one")
                                : Component.translatable("container.enchant.level.many", levelsConsumed);
                        list.add(levelLine.withStyle(ChatFormatting.GRAY));
                    }
                }
                guiGraphics.renderComponentTooltip(this.font, list, mouseX, mouseY);
                break;
            }
        }

        if (this.isHovering(8, 16, 9, 9, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(this.font, enchantInfo(), mouseX, mouseY);
        }
    }

    private List<Component> enchantInfo() {
        String base = "info." + BaileysEnchantments.MODID + ".enchant.";
        List<Component> list = Lists.newArrayList();
        list.add(Component.translatable(base + "title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        list.add(Component.translatable(base + "l1").withStyle(ChatFormatting.GRAY));
        list.add(Component.translatable(base + "l2").withStyle(ChatFormatting.GRAY));
        list.add(Component.empty());
        list.add(Component.translatable(base + "t1").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        list.add(Component.translatable(base + "t2").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        return list;
    }
}

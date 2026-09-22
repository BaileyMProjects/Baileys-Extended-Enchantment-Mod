package com.bailey.enchantments.client;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.util.ItemCooldown;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Draws the cooldown sweep for a {@link ItemCooldown per-item cooldown}, on that item alone.
 *
 * <p>⚠️ Vanilla's own sweep comes from {@code ItemCooldowns}, which is keyed by <b>Item</b> — so
 * using it greyed out every sword of that type in the inventory. Our cooldowns live in the
 * stack's NBT instead, and nothing in vanilla knows to draw them, so the overlay is drawn here.
 *
 * <p>A decorator rather than a HUD overlay because Forge runs decorators for every slot that
 * renders an item — hotbar and open containers alike — so the sweep appears wherever the item
 * does, which is what vanilla's does.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CooldownDecorator {
    private CooldownDecorator() {}

    /** Matches vanilla's cooldown overlay: white at low alpha, filling from the bottom. */
    private static final int SWEEP = 0x7FFFFFFF;

    @SubscribeEvent
    public static void onRegister(RegisterItemDecorationsEvent event) {
        // ⚠️ Registered for every item, because any item can carry the NBT: a Requiem sword, a
        // corrupted chestplate, a Shortbow. The draw is skipped in one field read when there is
        // no cooldown, which is the overwhelmingly common case.
        ForgeRegistries.ITEMS.forEach(item -> event.register(item, CooldownDecorator::draw));
    }

    private static boolean draw(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || stack.isEmpty()) return false;

        float fraction = ItemCooldown.overlayFraction(stack, player);
        if (fraction <= 0.0F) return false;

        int height = Math.max(1, Math.round(16 * fraction));
        graphics.fill(x, y + 16 - height, x + 16, y + 16, SWEEP);
        return true;
    }
}

package com.bailey.enchantments;

import com.bailey.enchantments.block.ModBlockEntities;
import com.bailey.enchantments.client.AncientAnvilScreen;
import com.bailey.enchantments.client.AncientEnchantingTableRenderer;
import com.bailey.enchantments.client.AncientEnchantingTableScreen;
import com.bailey.enchantments.menu.ModMenuTypes;

import com.bailey.enchantments.enchantment.ModEnchantments;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BaileysEnchantmentsClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.ANCIENT_ENCHANTING_TABLE.get(), AncientEnchantingTableScreen::new);
            MenuScreens.register(ModMenuTypes.ANCIENT_ANVIL.get(), AncientAnvilScreen::new);
            registerResonanceOverride();
        });
        BaileysEnchantments.LOGGER.info("Bailey's Extended Enchantment Mod client setup complete");
    }

    /**
     * Swords whose model swaps to the Resonance variant when they carry it.
     *
     * ⚠️ VANILLA SWORDS ONLY, and that is a real limit, not an oversight. A model override has
     * to live in the item's own model JSON, so it can only be given to items whose model file
     * this mod ships. A modded sword carrying Resonance gets the enchantment and its behaviour,
     * but no ribbon. -> Brain/Decisions/Open decisions.md #30
     */
    private static final Item[] RESONANCE_SWORDS = {
            Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
            Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD
    };

    /**
     * Registers the predicate the sword models switch on.
     *
     * <p>The aura is a TEXTURE, not a render effect — Bailey, 2026-09-21: <i>"a drawn on aura on
     * the icon that translates to the hand because its apart of the icon."</i> So the whole
     * mechanism is: this property returns 1 when the stack has Resonance, the vanilla sword
     * model has an override keyed on it, and the variant model draws the vanilla sword texture
     * as layer0 with an animated ribbon as layer1.
     *
     * <p>✅ <b>No vanilla art is copied into this mod</b> — the variant model REFERENCES
     * {@code minecraft:item/<sword>}, it does not contain it.
     *
     * <p>⚠️ <b>This mod now ships {@code assets/minecraft/models/item/*_sword.json}.</b> Any
     * resource pack or other mod that overrides the same six files will conflict — last one
     * loaded wins, and the loser's change silently disappears. That is the accepted cost of
     * putting the aura on the icon at all.
     */
    private static void registerResonanceOverride() {
        ResourceLocation key = new ResourceLocation(BaileysEnchantments.MODID, "resonance");
        for (Item sword : RESONANCE_SWORDS) {
            ItemProperties.register(sword, key, (stack, level, entity, seed) ->
                    EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RESONANCE.get(), stack) > 0
                            ? 1.0F : 0.0F);
        }
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ANCIENT_ENCHANTING_TABLE.get(),
                AncientEnchantingTableRenderer::new);
    }
}

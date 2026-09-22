package com.bailey.enchantments;

import com.bailey.enchantments.block.ModBlockEntities;
import com.bailey.enchantments.client.AncientAnvilScreen;
import com.bailey.enchantments.client.AncientEnchantingTableRenderer;
import com.bailey.enchantments.client.AncientEnchantingTableScreen;
import com.bailey.enchantments.client.ShadowPlayerRenderer;
import com.bailey.enchantments.entity.ModEntities;
import com.bailey.enchantments.menu.ModMenuTypes;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.NoopRenderer;
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
        });
        BaileysEnchantments.LOGGER.info("Bailey's Extended Enchantment Mod client setup complete");
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ANCIENT_ENCHANTING_TABLE.get(),
                AncientEnchantingTableRenderer::new);
        // The remnant has no model; it is the particles the server sends.
        event.registerEntityRenderer(ModEntities.SHADOW_REMNANT.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.SHADOW_PLAYER.get(), ShadowPlayerRenderer::new);
    }
}

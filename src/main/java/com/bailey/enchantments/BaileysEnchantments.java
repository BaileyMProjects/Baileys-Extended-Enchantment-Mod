package com.bailey.enchantments;

import com.bailey.enchantments.block.ModBlockEntities;
import com.bailey.enchantments.block.ModBlocks;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.entity.ModEntities;
import com.bailey.enchantments.item.ModCreativeTabs;
import com.bailey.enchantments.item.ModItems;
import com.bailey.enchantments.loot.ModLootModifiers;
import com.bailey.enchantments.menu.ModMenuTypes;
import com.bailey.enchantments.network.ModNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(BaileysEnchantments.MODID)
public class BaileysEnchantments {
    public static final String MODID = "baileys_enchantments";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BaileysEnchantments() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEnchantments.ENCHANTMENTS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModNetwork::register);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Bailey's Extended Enchantment Mod loaded!");
    }
}

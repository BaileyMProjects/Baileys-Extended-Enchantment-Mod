package com.bailey.enchantments.item;

import com.bailey.enchantments.BaileysEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BaileysEnchantments.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            "main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.baileys_enchantments"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.ANCIENT_ENCHANTING_TABLE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.ANCIENT_ENCHANTING_TABLE.get());
                        output.accept(ModItems.ANCIENT_ANVIL.get());
                        output.accept(ModItems.CORRUPTED_BOOKSHELF.get());
                        output.accept(ModItems.WARDEN_HEART.get());
                        output.accept(ModItems.ECHO_UPGRADE_TEMPLATE.get());
                        output.accept(ModItems.DARK_CRYSTAL.get());
                        output.accept(ModItems.SCARLET_CRYSTAL.get());

                        // The corrupted set, in the order it is worn.
                        output.accept(ModItems.CORRUPTED_UPGRADE_SMITHING_TEMPLATE.get());
                        output.accept(ModItems.CORRUPTED_HELMET.get());
                        output.accept(ModItems.CORRUPTED_CHESTPLATE.get());
                        output.accept(ModItems.CORRUPTED_LEGGINGS.get());
                        output.accept(ModItems.CORRUPTED_BOOTS.get());
                    }).build());
}

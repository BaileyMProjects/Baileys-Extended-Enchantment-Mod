package com.bailey.enchantments.menu;

import com.bailey.enchantments.BaileysEnchantments;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, BaileysEnchantments.MODID);

    public static final RegistryObject<MenuType<AncientEnchantmentMenu>> ANCIENT_ENCHANTING_TABLE =
            MENU_TYPES.register("ancient_enchanting_table",
                    () -> IForgeMenuType.create((windowId, inv, data) -> new AncientEnchantmentMenu(windowId, inv)));

    public static final RegistryObject<MenuType<AncientAnvilMenu>> ANCIENT_ANVIL =
            MENU_TYPES.register("ancient_anvil",
                    () -> IForgeMenuType.create((windowId, inv, data) -> new AncientAnvilMenu(windowId, inv)));
}

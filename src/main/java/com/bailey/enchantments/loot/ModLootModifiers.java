package com.bailey.enchantments.loot;

import com.bailey.enchantments.BaileysEnchantments;
import com.mojang.serialization.Codec;

import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// Serializers for this mod's global loot modifiers.
//
// ⚠️ Registering the codec here is only half of it. A modifier does nothing until it is ALSO
// listed in data/forge/loot_modifiers/global_loot_modifiers.json — the codec makes the JSON
// readable, the global file is what makes Forge read it at all. Miss the second half and the
// loot simply never appears, with no error.
public class ModLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BaileysEnchantments.MODID);

    public static final RegistryObject<Codec<AddItemModifier>> ADD_ITEM =
            LOOT_MODIFIER_SERIALIZERS.register("add_item", () -> AddItemModifier.CODEC);
}

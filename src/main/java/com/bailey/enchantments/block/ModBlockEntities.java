package com.bailey.enchantments.block;

import com.bailey.enchantments.BaileysEnchantments;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BaileysEnchantments.MODID);

    public static final RegistryObject<BlockEntityType<AncientEnchantingTableBlockEntity>> ANCIENT_ENCHANTING_TABLE =
            BLOCK_ENTITIES.register("ancient_enchanting_table", () ->
                    BlockEntityType.Builder.of(AncientEnchantingTableBlockEntity::new,
                            ModBlocks.ANCIENT_ENCHANTING_TABLE.get()).build(null));
}

package com.bailey.enchantments.block;

import com.bailey.enchantments.BaileysEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, BaileysEnchantments.MODID);

    public static final RegistryObject<AncientEnchantingTableBlock> ANCIENT_ENCHANTING_TABLE = BLOCKS.register(
            "ancient_enchanting_table",
            () -> new AncientEnchantingTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .sound(SoundType.SCULK_CATALYST)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 7)
                    .strength(5.0F, 1200.0F)
                    .noOcclusion()));

    // Power block for the ancient table's structure. Deliberately plain: no block entity,
    // no light, no correct-tool requirement — it's infrastructure you place in bulk, and
    // the weight it carries lives in the scan, not in the block.
    public static final RegistryObject<Block> CORRUPTED_BOOKSHELF = BLOCKS.register(
            "corrupted_bookshelf",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .sound(SoundType.SCULK_CATALYST)
                    .strength(1.5F)));

    public static final RegistryObject<AncientAnvilBlock> ANCIENT_ANVIL = BLOCKS.register(
            "ancient_anvil",
            () -> new AncientAnvilBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .sound(SoundType.ANVIL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 5)
                    .strength(5.0F, 1200.0F)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()));
}

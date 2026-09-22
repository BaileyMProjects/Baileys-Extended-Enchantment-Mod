package com.bailey.enchantments.util;

import com.bailey.enchantments.BaileysEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

// ⚠️ The JSON behind every tag here lives on a PLURAL 1.20.1 path — tags/blocks, tags/items,
// tags/entity_types. The 1.21 → 1.20.1 port left singular copies on disk (tags/block,
// tags/item) that DO NOT LOAD; editing one is a silent no-op with no error and no warning.
// See Brain/Reference/Port cruft in resources.md before adding a tag file.
// The one exception is worldgen: tags/worldgen/biome is singular on 1.20.1 too, because
// worldgen registries use their registry path directly rather than a pluralised folder.
public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> ENCHANTMENT_POWER_PROVIDER = BlockTags.create(
                new ResourceLocation(BaileysEnchantments.MODID, "enchantment_power_provider"));
    }

    public static class Items {
        // What counts as a piece of the corrupted set for set-bonus purposes.
        // ✅ Filled with all four pieces on 2026-08-14 (Phase 4); it shipped empty before that
        // so hasFullCorruptedSet() was correctly false while there was no armor to wear.
        // Tag-driven rather than an item identity check
        // for the same reason ENCHANTMENT_POWER_PROVIDER is: a datapack can extend it
        // without a code change. The trade-off is that a datapack can also grant the set
        // bonuses to arbitrary armor — accepted, consistent with how this mod already works.
        public static final TagKey<Item> CORRUPTED_ARMOR = TagKey.create(
                Registries.ITEM, new ResourceLocation(BaileysEnchantments.MODID, "corrupted_armor"));
    }

    public static class EntityTypes {
        // Which mobs count as "deep dark" for the OR rule in DeepDark.bonusApplies.
        public static final TagKey<EntityType<?>> DEEP_DARK_MOBS = TagKey.create(
                Registries.ENTITY_TYPE, new ResourceLocation(BaileysEnchantments.MODID, "deep_dark_mobs"));

        // Types that can never be raised as shadows, whatever the classifier says. Empty by
        // default; a pack maker adds a modded boss whose AI misbehaves as a shadow here.
        // -> Brain/Decisions/The Smite line is the Shadow Monarch.md
        public static final TagKey<EntityType<?>> UNRAISABLE = TagKey.create(
                Registries.ENTITY_TYPE, new ResourceLocation(BaileysEnchantments.MODID, "unraisable"));
    }

    public static class Biomes {
        // Which biomes count as "deep dark" for the OR rule in DeepDark.bonusApplies.
        public static final TagKey<Biome> DEEP_DARK_BIOMES = TagKey.create(
                Registries.BIOME, new ResourceLocation(BaileysEnchantments.MODID, "deep_dark_biomes"));
    }
}

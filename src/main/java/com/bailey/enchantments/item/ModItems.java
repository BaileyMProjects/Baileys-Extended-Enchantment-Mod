package com.bailey.enchantments.item;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.block.ModBlocks;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BaileysEnchantments.MODID);

    public static final RegistryObject<BlockItem> ANCIENT_ENCHANTING_TABLE = ITEMS.register(
            "ancient_enchanting_table",
            () -> new BlockItem(ModBlocks.ANCIENT_ENCHANTING_TABLE.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> CORRUPTED_BOOKSHELF = ITEMS.register(
            "corrupted_bookshelf",
            () -> new BlockItem(ModBlocks.CORRUPTED_BOOKSHELF.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> ANCIENT_ANVIL = ITEMS.register(
            "ancient_anvil",
            () -> new BlockItem(ModBlocks.ANCIENT_ANVIL.get(), new Item.Properties()));

    public static final RegistryObject<Item> WARDEN_HEART = ITEMS.register(
            "warden_heart",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> ECHO_UPGRADE_TEMPLATE = ITEMS.register(
            "echo_upgrade_template",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> DARK_CRYSTAL = ITEMS.register(
            "dark_crystal",
            () -> new Item(new Item.Properties()));

    // The Secret tier's currency, and the single grind gate of the whole mod.
    //
    // ⚠️ THIS ITEM CARRIES THE "THIS TIER IS A GRIND" JOB ALONE. Bailey, 2026-08-14: asked
    // whether the shipped Dark Crystal should be repriced to match his own "put the expense on
    // the consumable" rule, he pointed here instead — "the scarlet crystal is what matters
    // here". So the Dark Crystal stays cheap as shipped and nothing else picks up the slack
    // if this is priced softly. See Brain/Decisions/Secret enchantments.md.
    //
    // Recipe: Warden Heart + amethyst + redstone. At the 20% Heart drop that is ~5 Wardens
    // per crystal, and at the 20% roll ~25 Wardens per Secret actually won.
    public static final RegistryObject<Item> SCARLET_CRYSTAL = ITEMS.register(
            "scarlet_crystal",
            () -> new Item(new Item.Properties()));

    // ---- The corrupted set (Phase 4) ----------------------------------------------------
    //
    // Diamond → Corrupted via smithing, the Deep Dark's parallel to Diamond → Netherite.
    // Stats come from ModArmorMaterials.CORRUPTED and are netherite-identical.
    //
    // ⚠️ NO .fireResistant() ON ANY OF THESE FOUR, ON PURPOSE. Netherite armour survives lava
    // and this deliberately does not — it is the one place the two endgame paths are NOT
    // equal, and it is what stops the corrupted set being a strict upgrade that retires
    // netherite entirely. Bailey's "diamond → corrupted is an alternative route, not a tier
    // above" from 2026-08-09. If a future edit adds .fireResistant() here for consistency
    // with the material's netherite stats, it silently deletes that trade-off.
    //
    // ⚠️ Registering these is only half of switching the set on — the other half is
    // data/baileys_enchantments/tags/items/corrupted_armor.json. Every set bonus in
    // DeepDarkCombatEvents gates on that tag, not on these items, so an unlisted piece is a
    // piece that grants no set bonus at all and reports no error.

    public static final RegistryObject<ArmorItem> CORRUPTED_HELMET = ITEMS.register(
            "corrupted_helmet",
            () -> new ArmorItem(ModArmorMaterials.CORRUPTED, ArmorItem.Type.HELMET, new Item.Properties()));

    public static final RegistryObject<ArmorItem> CORRUPTED_CHESTPLATE = ITEMS.register(
            "corrupted_chestplate",
            () -> new ArmorItem(ModArmorMaterials.CORRUPTED, ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    public static final RegistryObject<ArmorItem> CORRUPTED_LEGGINGS = ITEMS.register(
            "corrupted_leggings",
            () -> new ArmorItem(ModArmorMaterials.CORRUPTED, ArmorItem.Type.LEGGINGS, new Item.Properties()));

    public static final RegistryObject<ArmorItem> CORRUPTED_BOOTS = ITEMS.register(
            "corrupted_boots",
            () -> new ArmorItem(ModArmorMaterials.CORRUPTED, ArmorItem.Type.BOOTS, new Item.Properties()));

    // The smithing template for the four pieces above.
    //
    // ⚠️ NOT the same item as ECHO_UPGRADE_TEMPLATE, despite the similar name. That one is the
    // Ancient Anvil's Ultimate-ritual token (AncientAnvilMenu gates on it); this one is a
    // vanilla smithing-table template for the armour. Two different templates with two
    // different jobs — see Brain/Decisions/Open decisions.md on whether the display names are
    // too close for a player to tell apart.
    public static final RegistryObject<Item> CORRUPTED_UPGRADE_SMITHING_TEMPLATE = ITEMS.register(
            "corrupted_upgrade_smithing_template",
            () -> new Item(new Item.Properties()));
}

package com.bailey.enchantments.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

// The boosted level caps for vanilla enchantments. Over-cap levels are only handed
// out by the Ancient Enchanting Table and Ancient Anvil; vanilla max is the floor
// for vanilla enchant rolls.
//
// There is ONE cap per enchantment, and it is reachable. This class used to declare
// a second, higher "true cap" tier that supposedly required the Ancient Anvil, with
// tableCeiling returning boostedCap - 1. That tier never existed in game: nothing
// outside this class ever called boostedCap, and the anvil clamps merges to
// tableCeiling like everything else, so Sharpness VI + VI has always merged to VI.
// The numbers below are the values that were always actually in effect - they were
// lowered by one and the "- 1" was dropped in the same change, which is why this is
// a no-op for the ~270 published players rather than a nerf. Do not re-introduce the
// second tier by editing these numbers without also restoring the offset; and do not
// raise them to "restore" the old declared caps, which would be a silent live buff to
// existing worlds.
public class OverCaps {
    private static Map<Enchantment, Integer> BOOSTED_CAPS = null;

    private static Map<Enchantment, Integer> caps() {
        if (BOOSTED_CAPS != null) return BOOSTED_CAPS;
        BOOSTED_CAPS = new HashMap<>();
        BOOSTED_CAPS.put(Enchantments.SHARPNESS,             6);
        BOOSTED_CAPS.put(Enchantments.POWER_ARROWS,          9);
        BOOSTED_CAPS.put(Enchantments.BLOCK_EFFICIENCY,      6);
        BOOSTED_CAPS.put(Enchantments.ALL_DAMAGE_PROTECTION, 6);
        BOOSTED_CAPS.put(Enchantments.FALL_PROTECTION,       6);
        BOOSTED_CAPS.put(Enchantments.UNBREAKING,            4);
        BOOSTED_CAPS.put(Enchantments.BLOCK_FORTUNE,         4);
        BOOSTED_CAPS.put(Enchantments.MOB_LOOTING,           4);
        BOOSTED_CAPS.put(Enchantments.THORNS,                4);
        BOOSTED_CAPS.put(Enchantments.KNOCKBACK,             3);
        BOOSTED_CAPS.put(Enchantments.PUNCH_ARROWS,          3);
        return BOOSTED_CAPS;
    }

    // The cap for a boosted enchantment. Same number as tableCeiling - they are one
    // concept now, kept as two names only because tableCeiling reads better next to
    // tableFloor at the roll sites.
    public static int boostedCap(Enchantment enchantment) {
        return caps().getOrDefault(enchantment, enchantment.getMaxLevel());
    }

    private static boolean isVanilla(Enchantment enchantment) {
        ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        return key != null && "minecraft".equals(key.getNamespace());
    }

    // Floor for ancient table rolls:
    // - Vanilla enchants: always at their vanilla max level minimum
    // - Mod enchants: start from level 1
    public static int tableFloor(Enchantment enchantment) {
        return isVanilla(enchantment) ? enchantment.getMaxLevel() : 1;
    }

    // Ceiling for ancient table rolls, and the clamp the Ancient Anvil applies to
    // merges. Boosted enchants cap at the values above; everything else at its
    // normal max level.
    public static int tableCeiling(Enchantment enchantment) {
        return boostedCap(enchantment);
    }

    // The ceiling an ANVIL MERGE may reach — which is not the same as what the table may roll.
    //
    // ⚠️ THE RULE (Bailey, 2026-09-21, after playtesting): you may climb WITHIN the over-cap
    // range, but you may never step INTO it by merging.
    //
    //     "you can combine V and V to make VI on an ancient anvil, which i think shouldnt be a
    //      feature. I think you should be able to combine for example prot V and V to make VI
    //      but not IV and IV to make V. Vanilla levels should not be able to combine to one of
    //      our enchants."
    //
    // Protection's vanilla max is IV, so V and VI are already ours:
    //
    //     IV + IV -> IV   both at vanilla max; merging must not cross into our range
    //     V  + V  -> VI   both already over-cap; climbing is allowed
    //     V  + IV -> V    preserved, nothing created
    //     VI + VI -> VI   clamped by the boosted cap
    //
    // The first over-cap level of any enchantment therefore comes from the Ancient Enchanting
    // Table and nowhere else, which is the whole point -> Brain/Decisions/Enchantment sourcing.md
    //
    // ⚠️ Mod enchantments are unaffected by construction: their boostedCap falls through to
    // getMaxLevel(), so vanillaMax == tableCeiling and the "both over-cap" branch can never be
    // taken. Vampiric I + I -> II still works as an ordinary upgrade.
    public static int mergeCeiling(Enchantment enchantment, int leftLevel, int rightLevel) {
        int vanillaMax = enchantment.getMaxLevel();
        if (leftLevel > vanillaMax && rightLevel > vanillaMax) {
            return tableCeiling(enchantment);
        }
        return Math.max(vanillaMax, Math.max(leftLevel, rightLevel));
    }
}

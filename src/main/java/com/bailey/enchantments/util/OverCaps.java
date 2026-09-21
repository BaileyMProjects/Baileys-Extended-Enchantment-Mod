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
}

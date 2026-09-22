package com.bailey.enchantments.util;

import com.bailey.enchantments.enchantment.ModEnchantments;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Ultimates {

    // Maps base vanilla enchantment → ultimate replacement
    // Populated lazily after registration completes
    private static Map<Enchantment, Enchantment> baseToUltimate = null;
    private static Map<Enchantment, Enchantment> ultimateToBase = null;

    private static void ensureBuilt() {
        if (baseToUltimate != null) return;
        baseToUltimate = new HashMap<>();
        ultimateToBase = new HashMap<>();

        add(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS,       ModEnchantments.EXECUTE.get());
        add(net.minecraft.world.item.enchantment.Enchantments.POWER_ARROWS,   ModEnchantments.OBLITERATE.get());
        add(net.minecraft.world.item.enchantment.Enchantments.BLOCK_EFFICIENCY, ModEnchantments.OVERDRIVE.get());
        add(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, ModEnchantments.AEGIS.get());
        add(net.minecraft.world.item.enchantment.Enchantments.FALL_PROTECTION, ModEnchantments.ZEPHYR.get());
        add(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,   ModEnchantments.ETERNAL.get());
        add(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE,    ModEnchantments.ABUNDANCE.get());
        add(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING,  ModEnchantments.PLUNDER.get());
        add(net.minecraft.world.item.enchantment.Enchantments.THORNS,       ModEnchantments.RETRIBUTION.get());
        add(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK,    ModEnchantments.SHOCKWAVE.get());
        add(net.minecraft.world.item.enchantment.Enchantments.PUNCH_ARROWS, ModEnchantments.TEMPEST.get());
        add(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT,  ModEnchantments.LIGHTNING_STRIKE.get());
        add(net.minecraft.world.item.enchantment.Enchantments.INFINITY_ARROWS, ModEnchantments.MULTISHOT.get());
        // HOMING is a regular enchantment, not an ultimate

        // The deep ladder. ⚠️ THE FIRST NON-VANILLA BASES THIS MAP HAS EVER HELD — every
        // entry above upgrades from a minecraft: enchantment. Checked before adding them:
        // the ritual gate in AncientAnvilMenu.createUltimateResult compares the held level
        // against OverCaps.tableCeiling, which falls through to getMaxLevel() for anything
        // it has no boosted cap for. That asks for Sculkbane V and Deepward IV, which is
        // exactly the intent — no special-casing needed anywhere.
        add(ModEnchantments.SCULKBANE.get(), ModEnchantments.SHATTER.get());
        add(ModEnchantments.DEEPWARD.get(),  ModEnchantments.SILENCE.get());

        // The evasion line. ⚠️ Projectile Protection, not Swift Sneak: the base must exist on
        // all four armour pieces or a full Mirage set is unobtainable.
        add(net.minecraft.world.item.enchantment.Enchantments.PROJECTILE_PROTECTION, ModEnchantments.MIRAGE.get());

        // The Smite line. Smite has no boosted cap in OverCaps, so the ritual asks for Smite V.
        add(net.minecraft.world.item.enchantment.Enchantments.SMITE, ModEnchantments.UMBRA.get());

        // Timber had no base enchantment of its own to upgrade from — every vanilla enchant
        // it could plausibly use (Efficiency included) was already claimed above, so it was
        // unobtainable through the Ancient Anvil. It shares Efficiency with Overdrive instead,
        // disambiguated by item type in getUltimateForBase(base, gear): an axe at the
        // Efficiency ceiling offers Timber, anything else offers Overdrive. Only the reverse
        // (ultimate -> base) entry is registered here so removeAllUltimates/getBaseForUltimate
        // recognize it without baseToUltimate's Efficiency entry being overwritten.
        ultimateToBase.put(ModEnchantments.TIMBER.get(), Enchantments.BLOCK_EFFICIENCY);
    }

    private static void add(Enchantment base, Enchantment ultimate) {
        baseToUltimate.put(base, ultimate);
        ultimateToBase.put(ultimate, base);
    }

    public static boolean isUltimate(Enchantment ench) {
        ensureBuilt();
        return ultimateToBase.containsKey(ench) || ench instanceof ModEnchantments.UltimateEnchantment;
    }

    public static Enchantment getUltimateForBase(Enchantment base) {
        ensureBuilt();
        return baseToUltimate.get(base);
    }

    // Item-aware variant: Efficiency resolves to Timber on axes, Overdrive on everything else.
    public static Enchantment getUltimateForBase(Enchantment base, ItemStack gear) {
        ensureBuilt();
        if (base == Enchantments.BLOCK_EFFICIENCY && gear.getItem() instanceof AxeItem) {
            return ModEnchantments.TIMBER.get();
        }
        return baseToUltimate.get(base);
    }

    public static Enchantment getBaseForUltimate(Enchantment ultimate) {
        ensureBuilt();
        return ultimateToBase.get(ultimate);
    }

    public static boolean hasUltimate(ItemStack stack, Enchantment ultimate) {
        return EnchantmentHelper.getEnchantments(stack).getOrDefault(ultimate, 0) > 0;
    }

    public static int getLevel(ItemStack stack, Enchantment enchantment) {
        return EnchantmentHelper.getEnchantments(stack).getOrDefault(enchantment, 0);
    }


    /**
     * Does this weapon carry Execute's perks — the damage bonus, the bleed, and the instant
     * kill at or below 20% health?
     *
     * <p>⚠️ <b>RESONANCE COUNTS, and that is deliberate.</b> Bailey, 2026-09-21:
     * <i>"Execute should also still be an active perk on this enchant which would pair perfectly
     * with this move as it makes everyone vulnerable to the executes one shot perk."</i>
     *
     * <p>Resonance is gambled FROM Execute and destroys it, so keeping Execute's perks is what
     * makes the trade an upgrade rather than a sideways move. It also makes the right-click
     * ability coherent: that move exists to set everything to exactly the execute threshold, and
     * without this it would set up a kill the sword could no longer perform.
     *
     * <p>⚠️ Call this rather than testing for EXECUTE directly. Every site that checks for the
     * enchantment by hand is a place where Resonance silently loses a perk.
     */
    public static boolean hasExecutePerk(ItemStack weapon) {
        return hasUltimate(weapon, ModEnchantments.EXECUTE.get())
                || hasUltimate(weapon, ModEnchantments.RESONANCE.get());
    }
    public static boolean hasAnyUltimate(ItemStack stack) {
        ensureBuilt();
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        for (Enchantment ench : enchants.keySet()) {
            if (ultimateToBase.containsKey(ench) || ench instanceof ModEnchantments.UltimateEnchantment) return true;
        }
        return false;
    }

    // Remove all ultimates from the map — used in the Ancient Anvil upgrade ritual.
    // Goes through isUltimate() (map lookup OR instanceof) rather than just the reverse map,
    // so it can't silently miss an ultimate whose reverse mapping was left unregistered.
    public static void removeAllUltimates(Map<Enchantment, Integer> enchants) {
        ensureBuilt();
        enchants.keySet().removeIf(Ultimates::isUltimate);
    }
}

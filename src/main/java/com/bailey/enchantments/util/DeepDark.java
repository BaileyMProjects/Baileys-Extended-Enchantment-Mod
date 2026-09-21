package com.bailey.enchantments.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

// The single source of truth for "does the Deep Dark bonus apply right now".
//
// Sculkbane, Deepward, the corrupted set bonuses and both deep Ultimates all ask this same
// question, so it lives in exactly one place. Two copies of this rule would drift the first
// time one of them is tuned — the same reasoning that put powerFactor in EnchantPower.
//
// ⚠️ THE RULE IS "OR", NOT "AND". Bailey's call, 2026-08-09: the bonus applies when the
// wearer is IN the Deep Dark biome **or** the other party is a deep dark mob. This is not a
// nicety. Vanilla's Deep Dark biome has ZERO natural mob spawning — the Warden only arrives
// via shriekers, and ancient cities spawn nothing — so a purely mob-targeted enchant would
// have been a single-boss enchantment and dead weight everywhere else. The biome half is
// what gives it a life outside the Warden fight; the mob half is what makes a Warden lured
// out of the Deep Dark still count.
//
// Both halves are tag-driven so future mobs and biomes slot in with no code change.
public final class DeepDark {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private DeepDark() {}

    // Is this entity standing in a deep dark biome?
    public static boolean isInDeepDark(Entity entity) {
        if (entity == null) return false;
        return entity.level().getBiome(entity.blockPosition()).is(ModTags.Biomes.DEEP_DARK_BIOMES);
    }

    // Is this entity a deep dark mob? Null-tolerant on purpose — the "other party" is
    // absent for environmental damage, and that is a legitimate no rather than a crash.
    public static boolean isDeepDarkMob(Entity entity) {
        if (entity == null) return false;
        return entity.getType().is(ModTags.EntityTypes.DEEP_DARK_MOBS);
    }

    // The agreed OR rule, written once.
    //
    // `self` is whoever is wearing or holding the enchantment; `other` is the entity on the
    // far side of the exchange, and may be null. Deliberately symmetric so both sides use
    // it unchanged:
    //   Sculkbane — self = attacker, other = victim
    //   Deepward  — self = wearer,   other = whatever is hurting them
    //
    // ⚠️ The biome is checked at SELF's position, never at other's. In melee the two are
    // interchangeable, but for a ranged sonic boom they are not: the wearer standing in the
    // Deep Dark is what the bonus is meant to reward.
    public static boolean bonusApplies(LivingEntity self, Entity other) {
        if (self == null) return false;
        return isInDeepDark(self) || isDeepDarkMob(other);
    }

    // Full corrupted set, all four slots. Gate for every set bonus.
    //
    // ✅ Live since 2026-08-14 (Phase 4) — the armor is registered and corrupted_armor.json
    // lists all four pieces. Before that the tag was empty on purpose, so this returned false
    // for everyone and the Phase 2 set bonuses compiled and behaved correctly (nobody had the
    // set) before the set existed. ⚠️ It is still the TAG that decides, not the item ids: a
    // piece missing from that file grants no set bonus and reports no error.
    public static boolean hasFullCorruptedSet(LivingEntity entity) {
        if (entity == null) return false;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.is(ModTags.Items.CORRUPTED_ARMOR)) return false;
        }
        return true;
    }
}

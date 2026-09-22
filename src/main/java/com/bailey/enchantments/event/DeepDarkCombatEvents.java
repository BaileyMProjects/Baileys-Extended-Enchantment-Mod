package com.bailey.enchantments.event;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.DeepDark;
import com.bailey.enchantments.util.DeepDarkBalance;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Combat behaviour for the Deep Dark tier: Sculkbane's conditional damage and Deepward's
// conditional mitigation.
//
// ⚠️ THIS IS DELIBERATELY NOT IN ModUltimateEvents. That class is ~1000 lines and has never
// been split (Brain/Reference/Split ModUltimateEvents.md); more importantly the whole
// structure rework it sits in is code-complete and has never been launched. Keeping every
// piece of expansion combat logic in its own class is what keeps a failure in one separable
// from the other. New Deep Dark combat code goes HERE, not there.
//
// ⚠️ Sculkbane is NOT implemented via Enchantment#getDamageBonus. That hook keys off MobType,
// the Warden's MobType is UNDEFINED, and it cannot see the biome at all — so the agreed OR
// rule is unexpressible in it. It has to be a damage event.
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public class DeepDarkCombatEvents {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // -------------------------------------------------------------------------
    // Sculkbane: flat bonus damage, but ONLY where the Deep Dark rule applies.
    //
    // Outside it the bonus is zero — not reduced, zero. That is the whole trade: a Sculkbane
    // sword is strictly worse than a Sharpness sword everywhere except the place it is for,
    // and the two can never sit on the same weapon (see DeepDarkEnchantment).
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingHurtSculkbane(LivingHurtEvent event) {
        // Direct entity, matching every other melee handler in this mod: an arrow's direct
        // entity is the arrow, so this stays melee-only without a separate projectile check.
        Entity cause = event.getSource().getDirectEntity();
        if (!(cause instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide()) return;

        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.SCULKBANE.get(), attacker.getMainHandItem());
        if (level <= 0) return;

        // ✅ A baseline everywhere, so the sword is never dead weight. Reworked 2026-09-22 —
        // see DeepDarkBalance.SCULKBANE_BASE_DAMAGE_PER_LEVEL for why the old zero was a trap.
        float bonus = level * DeepDarkBalance.SCULKBANE_BASE_DAMAGE_PER_LEVEL;
        if (DeepDark.bonusApplies(attacker, event.getEntity())) {
            bonus += level * DeepDarkBalance.SCULKBANE_DAMAGE_PER_LEVEL;
        }
        event.setAmount(event.getAmount() + bonus);
    }

    // -------------------------------------------------------------------------
    // Deepward, plus the corrupted set's share of the same mitigation.
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingHurtDeepward(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        float reduction = deepDarkReduction(victim, event.getSource());
        if (reduction <= 0.0F) return;

        event.setAmount(event.getAmount() * (1.0F - reduction));
    }

    // Every point of Deep Dark damage mitigation this mod grants, summed and clamped ONCE.
    //
    // ⚠️ ADDITIVE, IN ONE PLACE, ON PURPOSE. The agreed targets only work if the sources add:
    // Deepward IV (40%) + full set (40% vs sonic boom) = 80%, which is what turns a 10-damage
    // sonic boom into ~2. Two independent handlers each multiplying by (1 - their share) would
    // give 64%, silently miss the target, and be very hard to spot as the cause. So the set's
    // damage reduction — nominally Phase 6 — is computed here rather than in
    // CorruptedArmorEvents. That class keeps the bonuses that are not damage maths (darkness
    // immunity, speed, sculk movement). Anything new that reduces damage belongs in THIS sum.
    //
    // ✅ The set terms below went LIVE on 2026-08-14 (Phase 4), when the armor was registered
    // and corrupted_armor.json was filled. They were inert code until that tag had values in
    // it — if these bonuses ever appear to do nothing again, check that file first.
    public static float deepDarkReduction(LivingEntity victim, DamageSource source) {
        // Deepward is an enchantment, so it respects the same bypass vanilla protection does.
        // Sonic boom passes this guard — it bypasses ARMOR, not enchantments, which is exactly
        // why enchantment-space reduction is the only answer to it.
        if (source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return 0.0F;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return 0.0F;

        // ✅ Immunity to the sonic boom, from the full set alone. Bailey, 2026-09-22. Returned
        // as the clamp rather than 1.0 so the "nothing reaches total immunity" rule still holds
        // for every other source — see DeepDarkBalance.SET_SONIC_BOOM_IMMUNE for the full note.
        boolean sonic = source.is(DamageTypes.SONIC_BOOM);
        if (sonic && DeepDarkBalance.SET_SONIC_BOOM_IMMUNE && DeepDark.hasFullCorruptedSet(victim)) {
            return 1.0F;
        }

        // ⚠️ The DEEP DARK check moved below the baseline. Deepward now mitigates everywhere —
        // it supersedes Protection, so anything less made wearing it a downgrade. Only the
        // bonus terms are gated on the deep.
        boolean deep = DeepDark.bonusApplies(victim, source.getEntity());

        float reduction = 0.0F;

        // Deepward is per-level at a FULL set, and scales down linearly with missing pieces:
        // four pieces of IV = 40%, one piece of IV = 10%. Vanilla's per-piece-EPF model would
        // have made a full set of IV worth 160% before clamping, which makes the 80% target
        // meaningless and every level past the first pointless.
        int levels = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            levels += EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.DEEPWARD.get(), victim.getItemBySlot(slot));
        }
        if (levels > 0) {
            // The baseline, everywhere: IV at a full set = 80%, matching boosted Protection VI.
            reduction += DeepDarkBalance.DEEPWARD_BASE_REDUCTION_PER_LEVEL * levels / ARMOR_SLOTS.length;
            // And the deep bonus on top, which the clamp then holds at 90%.
            if (deep) {
                reduction += DeepDarkBalance.DEEPWARD_REDUCTION_PER_LEVEL * levels / ARMOR_SLOTS.length;
            }
        }

        if (deep && DeepDark.hasFullCorruptedSet(victim)) {
            // ⚠️ The sonic-boom branch is gone: the set is now immune to it outright and
            // returned early above. What is left is the generic deep dark reduction.
            reduction += DeepDarkBalance.SET_DEEP_DARK_DAMAGE_REDUCTION;
        }

        // ⚠️ "Very easy, not immune" was an explicit call. Nothing here may reach 1.0.
        return Math.min(reduction, DeepDarkBalance.MAX_TOTAL_REDUCTION);
    }
}

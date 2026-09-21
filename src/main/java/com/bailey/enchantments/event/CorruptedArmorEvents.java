package com.bailey.enchantments.event;

import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.util.DeepDark;
import com.bailey.enchantments.util.DeepDarkBalance;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// The corrupted set's bonuses that are NOT damage maths: darkness immunity and the movement
// speed bonus. Phase 6, built 2026-09-19.
//
// ⚠️ THE SET'S DAMAGE REDUCTION IS NOT HERE AND MUST NEVER MOVE HERE. It lives in
// DeepDarkCombatEvents.deepDarkReduction, where every source of Deep Dark mitigation is summed
// and clamped exactly ONCE. Two handlers each multiplying by (1 - their share) would produce
// 64% where 80% is intended — the agreed "sonic boom 10 → ~2" target silently missed, and very
// hard to trace back to a cause. Anything new that reduces damage belongs in that sum. This
// class gets everything else.
//
//
// ✅ PHASE 6 IS FINISHED AT TWO BONUSES, NOT THREE. The design called for a third — "no sculk
// slowdown" — which was never built because the slowdown it removes does not exist: in the
// whole of vanilla Blocks, only soul_sand and honey_block set a speedFactor. Sculk is exactly
// as fast as stone. Bailey closed this on 2026-09-21: "that mustve just been a prompt error
// from me so ignore that non existent buff." -> Brain/Decisions/Open decisions.md #19
//
// ⚠️ Do not "restore" it. Adding a slowdown to sculk so the armor can remove it would change
// vanilla blocks for everyone with the mod installed, to invent a problem.
// Both bonuses gate on DeepDark.hasFullCorruptedSet, which reads tags/items/corrupted_armor.json.
// ⚠️ A piece missing from that tag grants nothing and reports no error.
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public class CorruptedArmorEvents {

    // Fixed so the modifier is recognisable across ticks, sessions and respawns. Generated
    // once and permanent — changing it would orphan the modifier on anyone wearing the set
    // at the moment of the change, leaving a speed boost that nothing can remove.
    private static final UUID SET_SPEED_UUID = UUID.fromString("7f3a1c4e-2b6d-4a19-9c05-8e7d21f4b6a3");
    private static final String SET_SPEED_NAME = "Corrupted set speed";

    // -------------------------------------------------------------------------
    // Darkness immunity.
    //
    // ⚠️ MobEffectEvent.Applicable is @HasResult and is NOT @Cancelable — verified against
    // Forge 47.x bytecode, not documentation. setResult(DENY) is how the effect is refused;
    // calling setCanceled() on it throws UnsupportedOperationException at runtime, which is a
    // crash on first Warden rather than a compile error.
    //
    // This fires when something TRIES to apply the effect, so it refuses new applications but
    // does not strip a Darkness already running from before the set was equipped. That one
    // expires on its own; stripping it too would mean a tick handler for a 10-second effect.
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onDarknessApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect() != MobEffects.DARKNESS) return;
        if (!DeepDark.hasFullCorruptedSet(event.getEntity())) return;

        event.setResult(Event.Result.DENY);
    }

    // -------------------------------------------------------------------------
    // The movement speed bonus.
    //
    // ⚠️ TRANSIENT, NOT PERMANENT. addTransientModifier lives in memory only;
    // addPermanentModifier would serialise to the player's NBT, so taking the armor off while
    // the chunk is unloaded — or a crash mid-session — would leave the bonus welded on with no
    // way to shed it. The set bonus must be exactly as removable as the set.
    //
    // MULTIPLY_TOTAL so 0.10 reads as "+10% movement speed" regardless of what else is
    // modifying it (soul speed, potions). ADDITION would add 0.10 to a base of ~0.1, i.e.
    // roughly double the player's speed.
    //
    // Checked every tick because there is no single reliable "armor changed" event that covers
    // death, respawn, /give, curse-of-binding breakage and creative-mode slot drags alike. The
    // work per tick is a UUID map lookup and, only when the two disagree, one add or remove.
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingTickSpeed(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        boolean shouldHave = DeepDark.hasFullCorruptedSet(entity);
        boolean hasIt = speed.getModifier(SET_SPEED_UUID) != null;

        if (shouldHave && !hasIt) {
            speed.addTransientModifier(new AttributeModifier(
                    SET_SPEED_UUID,
                    SET_SPEED_NAME,
                    DeepDarkBalance.SET_SPEED_BONUS,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!shouldHave && hasIt) {
            speed.removeModifier(SET_SPEED_UUID);
        }
    }

    // -------------------------------------------------------------------------
    // ⚠️ THE THIRD BONUS IS DELIBERATELY ABSENT: "no sculk slowdown".
    //
    // Phase 6 was specified as three bonuses. The third cannot be built because the thing it
    // removes does not exist: verified against the 1.20.1 mapped jar's bytecode, the ONLY two
    // blocks in all of vanilla Blocks that call BlockBehaviour$Properties.speedFactor are
    // soul_sand and honey_block. sculk, sculk_vein, sculk_catalyst, sculk_sensor and
    // sculk_shrieker have no speed factor — walking on sculk is exactly as fast as stone.
    //
    // Rather than ship a handler that provably does nothing, this is an open question for
    // Bailey → Brain/Decisions/Open decisions.md #19.
    // -------------------------------------------------------------------------
}

package com.bailey.enchantments.event;

import java.util.List;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.DeepDarkBalance;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Behaviour for the Secret tier. Today that is Resonance alone.
//
// ⚠️ Its own class, like every other expansion tier — see DeepDarkUltimateEvents for the
// reasoning. ModUltimateEvents is ~1000 lines and expansion code stays out of it.
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public class SecretEnchantmentEvents {

    // -------------------------------------------------------------------------
    // Resonance: a hit emits a sonic pulse that damages everything nearby.
    //
    // ⚠️ THE PULSE MUST NOT RE-ENTER ITSELF. hurt() on a nearby mob fires LivingHurtEvent
    // again; if that pass could also trigger a pulse, one swing into a crowd would recurse
    // through the whole crowd and could stack-overflow the server. Two things prevent it,
    // and BOTH are needed:
    //   1. the pulse damages via an indirect magic source with no direct entity, so the
    //      `instanceof LivingEntity attacker` check below fails on re-entry; and
    //   2. `pulsing` short-circuits the handler outright for the duration of the pulse.
    // Belt and braces on purpose — (1) alone would break the moment someone changed the
    // damage source to something attributed to the player.
    //
    // ⚠️ NOT gated on the Deep Dark rule, unlike Sculkbane and Shatter. Resonance was moved
    // OUT of the deep ladder precisely so it would not be a Deep-Dark-only enchantment —
    // "resonance is such a cool name for an enchantment that I think it would be wasted just
    // being a deep dark only enchant". Gating it here would quietly undo that.
    // -------------------------------------------------------------------------
    private static boolean pulsing = false;

    @SubscribeEvent
    public static void onLivingHurtResonance(LivingHurtEvent event) {
        if (pulsing) return;

        Entity cause = event.getSource().getDirectEntity();
        if (!(cause instanceof LivingEntity attacker)) return;
        if (!(attacker.level() instanceof ServerLevel level)) return;

        if (!Ultimates.hasUltimate(attacker.getMainHandItem(), ModEnchantments.RESONANCE.get())) return;

        LivingEntity struck = event.getEntity();
        AABB area = struck.getBoundingBox().inflate(DeepDarkBalance.RESONANCE_PULSE_RADIUS);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, area);

        pulsing = true;
        try {
            for (LivingEntity target : nearby) {
                // The attacker is spared for the obvious reason; the struck entity is spared
                // because it is already taking the weapon's own damage this tick and would
                // otherwise be hit twice by one swing.
                if (target == attacker || target == struck) continue;
                if (!target.isAlive()) continue;
                // Do not turn a duel into a brawl with the player's own pets or teammates.
                if (target.isAlliedTo(attacker)) continue;

                target.hurt(level.damageSources().magic(), DeepDarkBalance.RESONANCE_PULSE_DAMAGE);
            }
        } finally {
            // In a finally so a crash inside one target's hurt() cannot leave the enchantment
            // permanently disabled for the rest of the session.
            pulsing = false;
        }

        level.sendParticles(ParticleTypes.SONIC_BOOM,
                struck.getX(), struck.getY(0.5D), struck.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, struck.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 0.6F, 1.4F);
    }
}

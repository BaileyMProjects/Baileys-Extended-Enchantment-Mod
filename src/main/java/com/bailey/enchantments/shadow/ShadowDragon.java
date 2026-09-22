package com.bailey.enchantments.shadow;

import javax.annotation.Nullable;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.shadow.ai.ShadowTargeting;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * The dragon, tamed. Bailey: <i>"I want the dragon to be tamable."</i>
 *
 * <p>She is the one shadow that needs her own entity: the dragon is driven by <b>phases</b>, not
 * goals, and her goal selector never ticks because {@code EnderDragon.aiStep} replaces the whole
 * of {@code LivingEntity}'s. So this subclass keeps the vanilla body, model, renderer and
 * flight physics, and swaps only the thing that chooses where to fly.
 *
 * <h2>⚠️ The swap is reflective, and here is why</h2>
 * {@code EnderDragon.phaseManager} is private final and the phase registry is closed
 * ({@code EnderDragonPhase.create} is private), so there is no supported way to add a phase.
 * Replacing the manager with a subclass that always returns <b>our</b> phase instance is the
 * smallest possible intrusion: every vanilla read of {@code phaseManager.getCurrentPhase()} —
 * flight, hurt, save, the model's sitting check — now gets a phase that circles the owner or
 * strafes the target. The SRG name is {@code f_31074_}; if this ever fails the constructor logs
 * it and the dragon simply hovers, which is the worst case, not a crash.
 *
 * <h2>The other traps</h2>
 * <ul>
 *   <li><b>She smashes through blocks.</b> Cancelled by {@link ShadowEvents} via Forge's
 *       mob-griefing event, for every shadow.
 *   <li><b>Vanilla sets her health to 1 and enters DYING instead of dying.</b> With our manager
 *       ignoring phase changes she would fly forever on 1 HP; {@link #hurt} finishes the job.
 *   <li><b>Her death drops 12,000 XP</b> straight from {@code tickDeath}, not through the event
 *       the other shadows are silenced by. {@link #tickDeath} is replaced.
 * </ul>
 */
public class ShadowDragon extends EnderDragon {

    private static final String PHASE_MANAGER_SRG = "f_31074_";
    private static final int DEATH_TICKS = 60;

    private final ShadowPhase phase = new ShadowPhase();

    public ShadowDragon(EntityType<? extends EnderDragon> type, Level level) {
        super(type, level);
        try {
            ObfuscationReflectionHelper.setPrivateValue(EnderDragon.class, this, new ShadowPhaseManager(this), PHASE_MANAGER_SRG);
        } catch (RuntimeException e) {
            BaileysEnchantments.LOGGER.error("Shadow dragon could not take control of its phases; she will only hover. {}", e.toString());
        }
    }

    // ---- flight ----------------------------------------------------------------------------

    /** Always our phase. {@code setPhase} is a no-op so vanilla cannot switch her to landing or dying. */
    private final class ShadowPhaseManager extends EnderDragonPhaseManager {
        ShadowPhaseManager(EnderDragon dragon) {
            super(dragon);
        }

        @Override
        public void setPhase(EnderDragonPhase<?> newPhase) {}

        @Override
        public DragonPhaseInstance getCurrentPhase() {
            return phase;
        }
    }

    /**
     * Two behaviours: <b>circle the owner</b> at a slow, wide orbit, and <b>strafe the target</b>
     * — dive at it, pass through (the vanilla head/neck contact damage does the hitting), climb,
     * and come round again. Targets are chosen by the same rules as every other shadow.
     */
    private final class ShadowPhase implements DragonPhaseInstance {
        private static final double ORBIT_RADIUS = 14.0;
        private static final double ORBIT_HEIGHT = 12.0;
        private static final double STRAFE_CLIMB = 10.0;

        @Nullable private Vec3 flyTarget;
        private boolean climbing;
        private int retargetIn;

        @Override
        public void doServerTick() {
            Player owner = ShadowData.owner(ShadowDragon.this);
            if (owner == null) {
                flyTarget = position();
                return;
            }

            if (--retargetIn <= 0) {
                retargetIn = 10;
                LivingEntity next = ShadowTargeting.choose(ShadowDragon.this, owner, getTarget(),
                        com.bailey.enchantments.shadow.SoldierLevels.intelligenceOf(ShadowDragon.this));
                if (next != getTarget()) setTarget(next);
            }

            LivingEntity target = getTarget();
            if (target != null && ShadowTargeting.valid(ShadowDragon.this, target, owner)) {
                strafe(target);
            } else {
                orbit(owner);
            }
        }

        private void orbit(Player owner) {
            double t = tickCount * 0.02;
            double bob = Math.sin(tickCount * 0.05) * 2.0;
            flyTarget = owner.position().add(Math.cos(t) * ORBIT_RADIUS, ORBIT_HEIGHT + bob, Math.sin(t) * ORBIT_RADIUS);
            climbing = false;
        }

        private void strafe(LivingEntity target) {
            double dist = distanceTo(target);
            if (climbing) {
                // Climb away after the pass, then turn back in.
                Vec3 away = position().subtract(target.position()).normalize();
                flyTarget = target.position().add(away.scale(18.0)).add(0, STRAFE_CLIMB, 0);
                if (dist > 16.0) climbing = false;
            } else {
                flyTarget = target.position().add(0, 1.5, 0);
                if (dist < 5.0) climbing = true;
            }
        }

        @Override public boolean isSitting() { return false; }
        @Override public void doClientTick() {}
        @Override public void onCrystalDestroyed(EndCrystal crystal, BlockPos pos, DamageSource source, @Nullable Player player) {}
        @Override public void begin() {}
        @Override public void end() {}
        @Override public float getFlySpeed() { return getTarget() != null ? 1.1F : 0.6F; }
        @Override public float getTurnSpeed() { return getTarget() != null ? 0.8F : 0.6F; }
        @Override public EnderDragonPhase<? extends DragonPhaseInstance> getPhase() { return EnderDragonPhase.HOVERING; }
        @Override @Nullable public Vec3 getFlyTargetLocation() { return flyTarget; }
        @Override public float onHurt(DamageSource source, float amount) { return amount; }
    }

    // ---- dying properly --------------------------------------------------------------------

    @Override
    public boolean hurt(EnderDragonPart part, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && ShadowData.areAllies(this, attacker)) return false;

        boolean result = super.hurt(part, source, amount);
        // Vanilla parks a dead dragon at 1 HP in the DYING phase, which our manager refuses.
        // LivingEntity.die() has already run (roster, team, events); finish the death here.
        if (this.dead && getHealth() > 0.0F) setHealth(0.0F);
        return result;
    }

    @Override
    protected void tickDeath() {
        ++dragonDeathTime;
        if (level() instanceof ServerLevel level && dragonDeathTime % 5 == 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2.0, getZ(), 20, 4.0, 2.0, 4.0, 0.05);
        }
        if (dragonDeathTime >= DEATH_TICKS) {
            if (level() instanceof ServerLevel level) ShadowSpawner.puff(level, this, 40);
            remove(RemovalReason.KILLED);
        }
    }

    // ---- no crystals, no portal ------------------------------------------------------------

    @Override
    public void onCrystalDestroyed(EndCrystal crystal, BlockPos pos, DamageSource source) {}
}

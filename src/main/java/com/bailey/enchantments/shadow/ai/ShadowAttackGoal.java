package com.bailey.enchantments.shadow.ai;

import java.util.EnumSet;

import com.bailey.enchantments.shadow.ShadowBalance;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Close in and hit whatever the shadow's target is, on any body.
 *
 * <p>Vanilla's {@code MeleeAttackGoal} needs a {@code PathfinderMob} and an attack-damage
 * attribute, and crashes without the latter. This goal has neither requirement: movement goes
 * through {@link ShadowMovement}, and a mob with no attack attribute — a cow, a villager, a
 * modded critter — delivers the <b>shadow bite</b> instead, {@code 1 + health / 10}. A shadow
 * sheep does 2 damage and eats a hit, which is exactly the cheap filler an army wants.
 */
public class ShadowAttackGoal extends Goal {
    private static final double SPEED = 1.25;

    private final Mob mob;
    private int cooldown;
    private int repathIn;

    public ShadowAttackGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathIn = 0;
        cooldown = 0;
    }

    @Override
    public void stop() {
        ShadowMovement.stop(mob);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (--repathIn <= 0) {
            repathIn = 4 + mob.getRandom().nextInt(7);
            ShadowMovement.moveTo(mob, target, SPEED);
        }

        if (cooldown > 0) cooldown--;
        if (cooldown == 0 && mob.distanceToSqr(target) <= reachSqr(target)) {
            cooldown = ShadowBalance.ATTACK_INTERVAL_TICKS;
            mob.swing(InteractionHand.MAIN_HAND);
            strike(target);
        }
    }

    private double reachSqr(LivingEntity target) {
        double reach = mob.getBbWidth() * 2.0 + target.getBbWidth() + ShadowBalance.ATTACK_REACH_PADDING;
        return reach * reach;
    }

    private void strike(LivingEntity target) {
        if (mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
            // The body's own attack: a Warden's 30, a zombie's 3, with its own side effects.
            mob.doHurtTarget(target);
        } else {
            float bite = (float) (1.0 + mob.getMaxHealth() / ShadowBalance.BITE_HEALTH_DIVISOR);
            target.hurt(mob.damageSources().mobAttack(mob), bite);
        }
    }
}

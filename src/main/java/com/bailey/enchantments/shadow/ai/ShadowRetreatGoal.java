package com.bailey.enchantments.shadow.ai;

import java.util.EnumSet;

import com.bailey.enchantments.shadow.ShadowBalance;
import com.bailey.enchantments.shadow.ShadowData;
import com.bailey.enchantments.shadow.SoldierLevels;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Intelligence rung 3: <b>self-preservation</b>. A badly hurt shadow breaks off and falls back
 * behind its owner instead of dying where it stands.
 *
 * <p>Bailey, 2026-09-22: a level 1 shadow <i>"has no sort of intelligence and just blindly
 * kills"</i>. This is the goal that stops being true. Below the threshold the shadow drops its
 * target, runs to the far side of its owner, and only re-engages once it has mended — which,
 * combined with wounds being carried into storage, means a clever soldier survives fights a dull
 * one does not.
 */
public class ShadowRetreatGoal extends Goal {
    /** Intelligence at which a shadow learns it can die. */
    public static final int REQUIRED_INTELLIGENCE = 3;

    private static final float FLEE_BELOW = 0.20F;
    private static final float RETURN_ABOVE = 0.55F;
    private static final double SPEED = 1.45;

    private final Mob mob;
    private int repathIn;

    public ShadowRetreatGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.TARGET));
    }

    private boolean hurtBadly() {
        return mob.getHealth() / mob.getMaxHealth() < FLEE_BELOW;
    }

    @Override
    public boolean canUse() {
        if (SoldierLevels.intelligenceOf(mob) < REQUIRED_INTELLIGENCE) return false;
        if (!hurtBadly()) return false;
        return ShadowData.owner(mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        Player owner = ShadowData.owner(mob);
        if (owner == null) return false;
        return mob.getHealth() / mob.getMaxHealth() < RETURN_ABOVE;
    }

    @Override
    public void start() {
        // Drop the fight. Without this the attack goal keeps steering it back into the target.
        ShadowAiAccess.clearTarget(mob);
        repathIn = 0;
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
        Player owner = ShadowData.owner(mob);
        if (owner == null) return;

        // Keep refusing a target while wounded, or the target goal will hand it another one.
        if (mob.getTarget() != null) ShadowAiAccess.clearTarget(mob);

        if (--repathIn > 0) return;
        repathIn = 10;

        // Behind the owner, relative to whatever hurt us; the owner's own back if nothing did.
        LivingEntity threat = mob.getLastHurtByMob();
        Vec3 away = threat != null
                ? owner.position().subtract(threat.position())
                : owner.position().subtract(mob.position());
        if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
        Vec3 refuge = owner.position().add(away.normalize().scale(ShadowBalance.WANDER_RADIUS * 0.5));
        ShadowMovement.moveTo(mob, refuge, SPEED);
    }
}

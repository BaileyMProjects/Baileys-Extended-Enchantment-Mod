package com.bailey.enchantments.shadow.ai;

import java.util.EnumSet;

import com.bailey.enchantments.shadow.ShadowBalance;
import com.bailey.enchantments.shadow.ShadowData;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Idle drift within the vicinity of the owner. The destination is picked around the
 * <b>owner's</b> position, not the shadow's, so the whole army moves with the player rather
 * than each shadow wandering away from wherever it happens to be.
 */
public class ShadowWanderGoal extends Goal {
    private static final double SPEED = 0.9;
    private static final int CHANCE = 80;

    private final Mob mob;
    private Vec3 destination;

    public ShadowWanderGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        if (mob.getRandom().nextInt(CHANCE) != 0) return false;
        Player owner = ShadowData.owner(mob);
        if (owner == null || mob.distanceTo(owner) > ShadowBalance.RETURN_RADIUS) return false;

        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        double radius = 2.0 + mob.getRandom().nextDouble() * (ShadowBalance.WANDER_RADIUS - 2.0);
        destination = owner.position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null && !mob.getNavigation().isDone()
                && mob.distanceToSqr(destination) > 1.5;
    }

    @Override
    public void start() {
        ShadowMovement.moveTo(mob, destination, SPEED);
    }

    @Override
    public void stop() {
        ShadowMovement.stop(mob);
    }
}

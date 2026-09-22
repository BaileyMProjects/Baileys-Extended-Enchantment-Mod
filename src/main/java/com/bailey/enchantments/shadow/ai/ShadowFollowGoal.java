package com.bailey.enchantments.shadow.ai;

import java.util.EnumSet;

import com.bailey.enchantments.shadow.ShadowBalance;
import com.bailey.enchantments.shadow.ShadowData;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Stay within the vicinity of the owner: walk back past {@link ShadowBalance#RETURN_RADIUS},
 * teleport past {@link ShadowBalance#TELEPORT_RADIUS}. Bailey: <i>"randomly move around in a
 * vicinity of the player and then follow him to stay within that vicinity."</i>
 *
 * <p>Runs below the attack goal, so a fight is never abandoned just because it drifted — unless
 * it drifted past the teleport radius, at which point the shadow is pulled back regardless.
 * An army that can be kited away from its owner is no bodyguard.
 */
public class ShadowFollowGoal extends Goal {
    private static final double SPEED = 1.3;

    private final Mob mob;
    private int repathIn;

    public ShadowFollowGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player owner = ShadowData.owner(mob);
        if (owner == null) return false;
        double dist = mob.distanceTo(owner);
        if (dist > ShadowBalance.TELEPORT_RADIUS) return true;
        return dist > ShadowBalance.RETURN_RADIUS && mob.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        Player owner = ShadowData.owner(mob);
        if (owner == null) return false;
        if (mob.getTarget() != null && mob.distanceTo(owner) <= ShadowBalance.TELEPORT_RADIUS) return false;
        return mob.distanceTo(owner) > ShadowBalance.WANDER_RADIUS;
    }

    @Override
    public void start() {
        repathIn = 0;
    }

    @Override
    public void stop() {
        ShadowMovement.stop(mob);
    }

    @Override
    public void tick() {
        Player owner = ShadowData.owner(mob);
        if (owner == null) return;

        if (mob.distanceTo(owner) > ShadowBalance.TELEPORT_RADIUS) {
            if (ShadowMovement.teleportNear(mob, owner)) return;
        }

        mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());
        if (--repathIn <= 0) {
            repathIn = 10;
            ShadowMovement.moveTo(mob, owner, SPEED);
        }
    }
}

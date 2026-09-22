package com.bailey.enchantments.shadow.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

/**
 * The one place a shadow goal asks a body to move.
 *
 * <p>Every goal here has to drive an arbitrary mob it has never seen. A {@link PathfinderMob}
 * has navigation and that is the reliable path; anything else (ghasts, slimes, exotic modded
 * fliers) only has a move control, which is a wanted position and a hope. Both are tried in
 * that order so a shadow of anything at least attempts to go where it is told.
 */
public final class ShadowMovement {
    private ShadowMovement() {}

    public static void moveTo(Mob mob, Vec3 pos, double speed) {
        if (mob instanceof PathfinderMob pathfinder) {
            if (!pathfinder.getNavigation().moveTo(pos.x, pos.y, pos.z, speed)) {
                mob.getMoveControl().setWantedPosition(pos.x, pos.y, pos.z, speed);
            }
        } else {
            mob.getMoveControl().setWantedPosition(pos.x, pos.y, pos.z, speed);
        }
    }

    public static void moveTo(Mob mob, Entity target, double speed) {
        if (mob instanceof PathfinderMob pathfinder && pathfinder.getNavigation().moveTo(target, speed)) return;
        mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
    }

    public static void stop(Mob mob) {
        mob.getNavigation().stop();
    }

    /**
     * Teleport beside the owner — the wolf rule, on any body. Tries a ring of offsets for a
     * spot the mob's hitbox fits in and that has something to stand on; gives up silently
     * rather than dropping a shadow into a wall.
     */
    public static boolean teleportNear(Mob mob, Entity owner) {
        if (!(owner.level() instanceof ServerLevel level)) return false;
        BlockPos centre = owner.blockPosition();
        for (int i = 0; i < 12; i++) {
            int dx = mob.getRandom().nextInt(7) - 3;
            int dz = mob.getRandom().nextInt(7) - 3;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos pos = centre.offset(dx, dy, dz);
                if (!standable(level, mob, pos)) continue;
                mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, mob.getYRot(), mob.getXRot());
                stop(mob);
                return true;
            }
        }
        // Fliers and swimmers may have nothing to stand on; put them at the owner's height.
        if (!(mob instanceof PathfinderMob)) {
            mob.moveTo(owner.getX(), owner.getY() + 1, owner.getZ(), mob.getYRot(), mob.getXRot());
            return true;
        }
        return false;
    }

    private static boolean standable(ServerLevel level, Mob mob, BlockPos pos) {
        BlockPathTypes type = WalkNodeEvaluator.getBlockPathTypeStatic(level, pos.mutable());
        if (type != BlockPathTypes.WALKABLE) return false;
        Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return level.noCollision(mob, mob.getBoundingBox().move(at.subtract(mob.position())));
    }
}

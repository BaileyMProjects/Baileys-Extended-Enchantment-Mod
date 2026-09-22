package com.bailey.enchantments.shadow.ai;

import java.util.List;

import javax.annotation.Nullable;

import com.bailey.enchantments.shadow.ShadowBalance;
import com.bailey.enchantments.shadow.ShadowClassifier;
import com.bailey.enchantments.shadow.ShadowData;
import com.bailey.enchantments.shadow.ShadowStance;
import com.bailey.enchantments.shadow.ShadowStances;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * The bodyguard's eyes, as a routine rather than a goal, so a body whose goals never tick (the
 * dragon drives itself from {@code aiStep}) applies exactly the same rules as everything else.
 * {@link ShadowTargetGoal} is a thin wrapper around {@link #choose}.
 *
 * <p>Bailey's rules, in order:
 * <ol>
 *   <li><b>A player who hit the owner</b> — overrides whatever the shadow is doing.
 *   <li><b>What the owner attacks</b> — how the army is directed.
 *   <li><b>Threats</b> — anything targeting the owner or a sibling, or that recently hurt one.
 *       Several → the classifier's highest score, so a Warden is engaged before the zombie.
 *   <li>Aggressive stance only: any hostile within the vicinity.
 * </ol>
 * Guard leaves idle hostiles alone; Passive takes no target at all.
 */
public final class ShadowTargeting {
    private ShadowTargeting() {}

    private static final double SCAN_RADIUS = 24.0;
    private static final double AGGRESSIVE_RADIUS = 12.0;
    private static final double GIVE_UP_RADIUS = 40.0;

    /**
     * The target this shadow should have now, given what it has. Returns the current target
     * when it is still valid and nothing outranks it, so the army does not flicker.
     */
    @Nullable
    public static LivingEntity choose(Mob mob, Player owner, @Nullable LivingEntity current) {
        ShadowStance stance = ShadowStances.get(owner.getUUID());
        if (stance == ShadowStance.PASSIVE) return null;

        if (current != null && !valid(mob, current, owner)) current = null;

        LivingEntity playerAttacker = recentPlayerAttacker(mob, owner);
        if (playerAttacker != null) return playerAttacker;

        if (current != null) return current;

        LivingEntity next = ownerVictim(mob, owner);
        if (next == null) next = worstThreat(mob, owner);
        if (next == null && stance == ShadowStance.AGGRESSIVE) next = nearestHostile(mob, owner);
        return next;
    }

    // ---- the rules -------------------------------------------------------------------------

    @Nullable
    private static LivingEntity recentPlayerAttacker(Mob mob, Player owner) {
        LivingEntity by = owner.getLastHurtByMob();
        if (by instanceof Player && recent(owner.tickCount, owner.getLastHurtByMobTimestamp()) && valid(mob, by, owner)) {
            return by;
        }
        return null;
    }

    @Nullable
    private static LivingEntity ownerVictim(Mob mob, Player owner) {
        LivingEntity victim = owner.getLastHurtMob();
        if (victim != null && recent(owner.tickCount, owner.getLastHurtMobTimestamp()) && valid(mob, victim, owner)) {
            return victim;
        }
        return null;
    }

    @Nullable
    private static LivingEntity worstThreat(Mob mob, Player owner) {
        AABB area = owner.getBoundingBox().inflate(SCAN_RADIUS);
        List<LivingEntity> nearby = owner.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != owner && e != mob && valid(mob, e, owner));

        LivingEntity best = null;
        double bestScore = -1;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : nearby) {
            if (!threatens(e, owner)) continue;
            double score = ShadowClassifier.slotsFor(e);
            double dist = e.distanceToSqr(mob);
            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                best = e;
                bestScore = score;
                bestDist = dist;
            }
        }
        return best;
    }

    private static boolean threatens(LivingEntity e, Player owner) {
        if (e instanceof Mob m) {
            LivingEntity t = m.getTarget();
            if (t == owner) return true;
            if (t != null && ShadowData.isOwnedBy(t, owner.getUUID())) return true;
        }
        LivingEntity hurt = e.getLastHurtMob();
        if (hurt != null && recent(e.tickCount, e.getLastHurtMobTimestamp())) {
            return hurt == owner || ShadowData.isOwnedBy(hurt, owner.getUUID());
        }
        return false;
    }

    @Nullable
    private static LivingEntity nearestHostile(Mob mob, Player owner) {
        AABB area = owner.getBoundingBox().inflate(AGGRESSIVE_RADIUS);
        List<LivingEntity> nearby = owner.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e instanceof Enemy && valid(mob, e, owner));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : nearby) {
            double d = e.distanceToSqr(mob);
            if (d < bestDist) { best = e; bestDist = d; }
        }
        return best;
    }

    // ---- validity --------------------------------------------------------------------------

    public static boolean valid(Mob mob, @Nullable LivingEntity e, Player owner) {
        if (e == null || !e.isAlive() || e == owner || e == mob) return false;
        if (e.level() != mob.level()) return false;
        if (ShadowData.areAllies(e, mob)) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return e.distanceToSqr(owner) <= GIVE_UP_RADIUS * GIVE_UP_RADIUS;
    }

    private static boolean recent(int now, int stamp) {
        return stamp > 0 && now - stamp <= ShadowBalance.THREAT_MEMORY_TICKS;
    }
}

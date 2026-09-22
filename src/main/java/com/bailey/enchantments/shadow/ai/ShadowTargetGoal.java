package com.bailey.enchantments.shadow.ai;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.bailey.enchantments.shadow.ShadowBalance;
import com.bailey.enchantments.shadow.ShadowClassifier;
import com.bailey.enchantments.shadow.ShadowData;
import com.bailey.enchantments.shadow.ShadowStance;
import com.bailey.enchantments.shadow.ShadowStances;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * The bodyguard's eyes. Picks the shadow's target from Bailey's rules, in this order:
 *
 * <ol>
 *   <li><b>A player who hit the owner</b> — overrides whatever the shadow is doing. <i>"If a
 *       player hits the player with an army summoned the army always targets the player first."</i>
 *   <li><b>What the owner attacks</b> — <i>"Army will attack whatever the player attacks."</i>
 *       This is how the army is directed.
 *   <li><b>Threats</b> — anything currently targeting the owner or one of their shadows, or
 *       that recently hurt them. Several → the one the classifier scores highest, so a Warden is
 *       engaged before the zombie beside it. <i>"roughly gauge threat level of that entity and
 *       target based off that if there are multiple."</i>
 *   <li>In the Aggressive stance only: any hostile within the vicinity.
 * </ol>
 *
 * <p>Nothing else. In the Guard stance an idle hostile fifteen blocks away is left alone; that is
 * what makes this a bodyguard rather than a grinder. Passive takes no target at all.
 *
 * <p>Re-evaluated every ten ticks. A current target is kept while it stays valid and no
 * higher rule produces something else, so the army does not flicker between two attackers.
 */
public class ShadowTargetGoal extends Goal {
    private static final int INTERVAL = 10;
    private static final double SCAN_RADIUS = 24.0;
    private static final double AGGRESSIVE_RADIUS = 12.0;
    private static final double GIVE_UP_RADIUS = 40.0;

    private final Mob mob;
    private int timer;

    public ShadowTargetGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return ShadowData.owner(mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (++timer < INTERVAL) return;
        timer = 0;

        Player owner = ShadowData.owner(mob);
        if (owner == null) return;

        ShadowStance stance = ShadowStances.get(owner.getUUID());
        if (stance == ShadowStance.PASSIVE) {
            if (mob.getTarget() != null) mob.setTarget(null);
            return;
        }

        LivingEntity current = mob.getTarget();
        if (current != null && !valid(current, owner)) {
            current = null;
            mob.setTarget(null);
        }

        // Rule 1 always wins, even over a target already engaged.
        LivingEntity playerAttacker = recentPlayerAttacker(owner);
        if (playerAttacker != null) {
            if (playerAttacker != current) mob.setTarget(playerAttacker);
            return;
        }

        if (current != null) return;

        LivingEntity next = ownerVictim(owner);
        if (next == null) next = worstThreat(owner);
        if (next == null && stance == ShadowStance.AGGRESSIVE) next = nearestHostile(owner);
        if (next != null) mob.setTarget(next);
    }

    // ---- the rules -------------------------------------------------------------------------

    @Nullable
    private LivingEntity recentPlayerAttacker(Player owner) {
        LivingEntity by = owner.getLastHurtByMob();
        if (by instanceof Player && recent(owner.tickCount, owner.getLastHurtByMobTimestamp()) && valid(by, owner)) {
            return by;
        }
        return null;
    }

    @Nullable
    private LivingEntity ownerVictim(Player owner) {
        LivingEntity victim = owner.getLastHurtMob();
        if (victim != null && recent(owner.tickCount, owner.getLastHurtMobTimestamp()) && valid(victim, owner)) {
            return victim;
        }
        return null;
    }

    @Nullable
    private LivingEntity worstThreat(Player owner) {
        AABB area = owner.getBoundingBox().inflate(SCAN_RADIUS);
        List<LivingEntity> nearby = owner.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != owner && e != mob && valid(e, owner));

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

    /** Targeting the owner or a sibling, or hurt one of them lately. */
    private boolean threatens(LivingEntity e, Player owner) {
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
    private LivingEntity nearestHostile(Player owner) {
        AABB area = owner.getBoundingBox().inflate(AGGRESSIVE_RADIUS);
        List<LivingEntity> nearby = owner.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e instanceof Enemy && valid(e, owner));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : nearby) {
            double d = e.distanceToSqr(mob);
            if (d < bestDist) { best = e; bestDist = d; }
        }
        return best;
    }

    // ---- validity --------------------------------------------------------------------------

    private boolean valid(LivingEntity e, Player owner) {
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

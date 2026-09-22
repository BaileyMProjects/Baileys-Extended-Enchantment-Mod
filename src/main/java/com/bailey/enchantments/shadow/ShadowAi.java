package com.bailey.enchantments.shadow;

import com.bailey.enchantments.shadow.ai.ShadowAttackGoal;
import com.bailey.enchantments.shadow.ai.ShadowFollowGoal;
import com.bailey.enchantments.shadow.ai.ShadowTargetGoal;
import com.bailey.enchantments.shadow.ai.ShadowWanderGoal;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/**
 * Turns any mob's brain into a shadow's.
 *
 * <h2>🔴 Both selectors are wiped, and the behaviour brain with them</h2>
 * Not optional. A piglin brute keeps "attack players", a creeper keeps "swell beside the
 * target" — beside the owner — and an enderman keeps teleport-on-look. Brain-driven mobs (the
 * Warden, piglins, villagers, axolotls) have empty goal selectors and run behaviours instead, so
 * those are removed too; their goal selectors still tick, and ours go in there. The body is
 * theirs; the brain is ours.
 *
 * <p>Idempotent on purpose: it runs on the fresh spawn and again on every world load, because
 * the game rebuilds the vanilla goals from the constructor each time the entity is loaded.
 */
public final class ShadowAi {
    private ShadowAi() {}

    public static void install(Mob mob) {
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);
        mob.getBrain().removeAllBehaviors();
        mob.setTarget(null);

        int p = 0;
        if (mob instanceof RangedAttackMob) {
            mob.goalSelector.addGoal(p++, rangedGoal(mob));
        }
        mob.goalSelector.addGoal(p++, new ShadowAttackGoal(mob));
        mob.goalSelector.addGoal(p++, new ShadowFollowGoal(mob));
        mob.goalSelector.addGoal(p++, new ShadowWanderGoal(mob));
        mob.goalSelector.addGoal(p, new RandomLookAroundGoal(mob));

        mob.targetSelector.addGoal(0, new ShadowTargetGoal(mob));

        body(mob);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RangedAttackGoal rangedGoal(Mob mob) {
        // RangedAttackGoal wants <T extends Mob & RangedAttackMob>; the instanceof above is the
        // real check and the raw construction is the only way to express it for an unknown type.
        return new RangedAttackGoal((RangedAttackMob) mob, 1.1, 40, 14.0F);
    }

    /** The things a body must stop doing to be a shadow, whatever it was before. */
    private static void body(Mob mob) {
        mob.setPersistenceRequired();
        mob.setCanPickUpLoot(false);

        // ✅ Bailey: "give the undead permanent resistance" — a shadow zombie standing guard at
        // noon must not burn. Infinite, hidden, no particles.
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));

        // A shadow zombie taking a hit would otherwise call vanilla reinforcements.
        AttributeInstance reinforcements = mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        if (reinforcements != null) reinforcements.setBaseValue(0.0);

        // Piglins and hoglins convert in the Overworld; a converted mob is a NEW entity and the
        // shadow mark would not follow it.
        if (mob instanceof AbstractPiglin piglin) piglin.setImmuneToZombification(true);
        if (mob instanceof Hoglin hoglin) hoglin.setImmuneToZombification(true);
    }
}

package com.bailey.enchantments.shadow;

import com.bailey.enchantments.shadow.ai.ShadowAttackGoal;
import com.bailey.enchantments.shadow.ai.ShadowFollowGoal;
import com.bailey.enchantments.shadow.ai.ShadowRetreatGoal;
import com.bailey.enchantments.shadow.ai.ShadowTargetGoal;
import com.bailey.enchantments.shadow.ai.ShadowWanderGoal;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import javax.annotation.Nullable;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
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
        if (mob instanceof ShadowDragon) {
            // Her goals never tick (EnderDragon.aiStep replaces LivingEntity's); she drives
            // herself from her phase. Only the body rules apply.
            mob.getBrain().removeAllBehaviors();
            body(mob);
            return;
        }
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);
        mob.getBrain().removeAllBehaviors();
        setTarget(mob, null);

        int p = 0;
        // ⚠️ Above the attack goal: a shadow clever enough to run must not be dragged back into
        // the fight by a goal that outranks it.
        mob.goalSelector.addGoal(p++, new ShadowRetreatGoal(mob));
        if (shouldShoot(mob)) {
            mob.goalSelector.addGoal(p++, rangedGoal(mob));
        }
        mob.goalSelector.addGoal(p++, new ShadowAttackGoal(mob));
        mob.goalSelector.addGoal(p++, new ShadowFollowGoal(mob));
        mob.goalSelector.addGoal(p++, new ShadowWanderGoal(mob));
        mob.goalSelector.addGoal(p, new RandomLookAroundGoal(mob));

        mob.targetSelector.addGoal(0, new ShadowTargetGoal(mob));

        body(mob);
    }

    /**
     * 🐛 FOUND IN PLAY 2026-09-22: the Warden, piglins and hoglins override {@code getTarget()} to
     * read their brain's ATTACK_TARGET memory. {@code Mob.setTarget} writes a plain field they
     * never read, so a shadow Warden "had" a target it could not see and stood there while its
     * owner died. Set both. {@code Brain.setMemory} silently ignores a type the brain does not
     * register, so this is safe on every body.
     */
    public static void setTarget(Mob mob, @Nullable LivingEntity target) {
        mob.setTarget(target);
        if (target != null) mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        else mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }

    /**
     * Should this shadow shoot rather than swing?
     *
     * <h3>🐛 Found in play 2026-09-22: a wither skeleton firing arrows with a stone sword</h3>
     * Implementing {@code RangedAttackMob} is <b>not</b> the same as being able to shoot. The
     * whole skeleton family inherits it, wither skeletons included, and vanilla resolves this at
     * runtime in {@code AbstractSkeleton.reassessWeaponGoal} by looking at what the mob is
     * actually holding. Worse, {@code performRangedAttack} conjures an arrow whether or not there
     * is a bow — so the wrong goal does not fail, it fires.
     *
     * <p>So: shoot if <b>holding</b> something to shoot with. A mob that shoots with no weapon at
     * all — a witch's potions, a snow golem's snowballs, an illusioner's magic — is allowed to,
     * but only if its hands are empty, and only if it is not from a family that needs a weapon.
     */
    private static boolean shouldShoot(Mob mob) {
        if (!(mob instanceof RangedAttackMob)) return false;

        ItemStack weapon = mob.getMainHandItem();
        if (weapon.getItem() instanceof ProjectileWeaponItem || weapon.getItem() instanceof TridentItem) {
            return true;
        }

        // ⚠️ These two fabricate ammunition they do not have. A skeleton with a sword must swing
        // it, and an empty-handed drowned must not throw phantom tridents.
        if (mob instanceof AbstractSkeleton || mob instanceof Drowned) return false;

        return weapon.isEmpty();
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
        ShadowTeam.join(mob);

        // ⚠️ NO POTION EFFECT FOR FIRE. Bailey, 2026-09-22: "fire resistance on a zombie still
        // sets them on fire which looks ugly I do not want them to even set on fire when its
        // apart of the army." Fire Resistance stops the damage but not the flames, and it puts
        // swirls on the model besides. Immunity is now built into the body instead:
        // ShadowEvents refuses fire damage, refuses every harmful effect, and clears the burning
        // flag every tick so a shadow never so much as flickers.

        // A shadow zombie taking a hit would otherwise call vanilla reinforcements.
        AttributeInstance reinforcements = mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        if (reinforcements != null) reinforcements.setBaseValue(0.0);

        // Piglins and hoglins convert in the Overworld; a converted mob is a NEW entity and the
        // shadow mark would not follow it.
        if (mob instanceof AbstractPiglin piglin) piglin.setImmuneToZombification(true);
        if (mob instanceof Hoglin hoglin) hoglin.setImmuneToZombification(true);
    }
}

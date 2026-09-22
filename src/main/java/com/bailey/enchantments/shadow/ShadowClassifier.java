package com.bailey.enchantments.shadow;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.bailey.enchantments.util.ModTags;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Classifies EVERY entity type — vanilla or modded — into a slot cost and a shadow role.
 *
 * <p>Bailey, 2026-09-22: <i>"categorisation for all mobs based on some math that calculated
 * their value between their base damage and health... most mods arent used singularly its within
 * a pack of mods. So it only applying to vanilla would be kind of boring."</i> And later:
 * <i>"I want to be able to categorise EVERY SINGLE TYPE OF MOB."</i>
 *
 * <h2>Why nothing here needs an instance</h2>
 * Everything is read from the registry: {@link DefaultAttributes} holds the base attributes every
 * living type registered (modded mobs register theirs the same way), and
 * {@link EntityType#getBaseClass()} gives the Java class without spawning one. So the whole pack
 * can be classified at server start and written to a report, and a type nobody has ever seen in
 * this world still has a cost.
 *
 * <h2>Cost — one formula, no special cases</h2>
 * <pre>
 *   effHealth = health / (1 − 0.04 × min(armor, 20))     what it actually takes to kill
 *   damage    = ATTACK_DAMAGE if present, else 0          a cow scores 0; the floor handles it
 *   threat    = effHealth × (1 + damage / 4)
 *   slots     = max(1, round(log2(threat / 30)) + 1)      each doubling of threat = +1 slot
 * </pre>
 * Zombie 1 · Enderman 3 · Iron Golem 5 · Wither 5 · Warden 8. Two attribute traps the formula
 * absorbs rather than special-casing: the <b>Wither reports 2.0 damage</b> (its skulls are not an
 * attribute) so health carries the weight; the <b>dragon has no attack attribute at all</b>.
 *
 * <h2>⚠️ Live values at kill time, base values in the report</h2>
 * {@link #slotsFor(LivingEntity)} reads the dead entity's own max health, damage and armour, so a
 * mod that scales health per-instance — or a player in full netherite — is charged for what was
 * actually fought. The report is labelled base values for that reason.
 *
 * <h2>Overrides, in priority order</h2>
 * {@code #baileys_enchantments:unraisable} tag → {@code config/baileys_enchantments/shadow_overrides.json}
 * → built-in overrides (the dragon) → the formula.
 */
public final class ShadowClassifier {
    private ShadowClassifier() {}

    /** What a shadow of this type can actually do. Decided by capability, worst case first. */
    public enum Role {
        /** No attribute supplier: arrows, boats, item frames. Never a candidate. */
        NOT_LIVING("not living"),
        /** A goal-driven mob that can shoot. Keeps a ranged goal, re-targeted. */
        RANGED("ranged"),
        /** A goal-driven mob. Melee, or the shadow bite if it has no attack attribute. */
        MELEE("melee"),
        /** A player. Needs its own entity so it can wear the death gear. */
        PLAYER("player — own entity"),
        /** The Ender Dragon. Phase-driven, not goal-driven; needs its own entity. */
        DRAGON("dragon — own entity"),
        /** Living but not a Mob (armour stands, exotic modded entities). Costed, never raised. */
        UNDRIVABLE("cannot be driven");

        public final String label;
        Role(String label) { this.label = label; }

        /** Whether a shadow of this role can be produced right now. Flipped as phases land. */
        public boolean supported() {
            return switch (this) {
                case RANGED, MELEE -> true;
                case PLAYER -> ShadowSupport.PLAYERS;
                case DRAGON -> ShadowSupport.DRAGON;
                default -> false;
            };
        }
    }

    /** The threat tier, which is just the slot cost bucketed. Drives the ritual and the theatre. */
    public enum Tier {
        RUNT, SOLDIER, ELITE, MONARCH;

        public static Tier fromSlots(int slots) {
            if (slots >= ShadowBalance.MONARCH_MIN_SLOTS) return MONARCH;
            if (slots >= ShadowBalance.ELITE_MIN_SLOTS) return ELITE;
            if (slots >= ShadowBalance.SOLDIER_MIN_SLOTS) return SOLDIER;
            return RUNT;
        }

        /** Runts and Soldiers always rise. Elites and Monarchs roll. */
        public boolean needsRitual() { return this == ELITE || this == MONARCH; }

        public float ariseChance() {
            return switch (this) {
                case ELITE -> ShadowBalance.ELITE_ARISE_CHANCE;
                case MONARCH -> ShadowBalance.MONARCH_ARISE_CHANCE;
                default -> 1.0F;
            };
        }

        /** Ticks the player is frozen for the cutscene. Zero means no cutscene. */
        public int cutsceneTicks() {
            return switch (this) {
                case ELITE -> ShadowBalance.ELITE_CUTSCENE_TICKS;
                case MONARCH -> ShadowBalance.MONARCH_CUTSCENE_TICKS;
                default -> 0;
            };
        }

        public String label() {
            return switch (this) {
                case RUNT -> "Runt";
                case SOLDIER -> "Soldier";
                case ELITE -> "Elite";
                case MONARCH -> "Monarch";
            };
        }
    }

    /** One row of the report: everything the classifier knows about a type, and why. */
    public record Classification(ResourceLocation id, Role role, boolean raisable,
                                 double health, double damage, double armor,
                                 double threat, int slots, Tier tier, String decidedBy) {}

    // ---- overrides ------------------------------------------------------------------------

    private static final Map<ResourceLocation, Integer> BUILT_IN = Map.of(
            new ResourceLocation("minecraft", "ender_dragon"), ShadowBalance.DRAGON_SLOTS);

    private static Map<ResourceLocation, Integer> configured = Collections.emptyMap();

    /** Installed by {@link ShadowReport} once the config file has been read. */
    static void setConfiguredOverrides(Map<ResourceLocation, Integer> overrides) {
        configured = new HashMap<>(overrides);
    }

    // ---- the formula ----------------------------------------------------------------------

    public static double threat(double health, double damage, double armor) {
        return threat(health, damage, armor, 0, 0, 0);
    }

    /**
     * The full formula, with the signals a mob declares beyond the first three.
     *
     * <p>⚠️ <b>Knockback resistance is the big one.</b> A mob you cannot stagger fights a
     * completely different fight from one you can shove off a ledge, and the original three-term
     * formula was blind to it. Toughness and attack knockback are smaller corrections in the
     * same spirit: measurable things that change how hard a mob is to handle, which health and
     * damage alone do not capture.
     *
     * <p>🔶 None of this catches an ability like petrification. That needs
     * {@link ShadowObservations}, which watches rather than reads.
     */
    public static double threat(double health, double damage, double armor,
                                double toughness, double knockbackResist, double attackKnockback) {
        double reduction = 0.04 * Math.min(Math.max(armor, 0), 20);
        // Toughness blunts heavy hits, which is exactly what a player brings to a boss.
        reduction += 0.01 * Math.min(Math.max(toughness, 0), 20);
        double effHealth = health / (1.0 - Math.min(reduction, 0.9));

        double t = effHealth * (1.0 + Math.max(damage, 0) / ShadowBalance.DAMAGE_WEIGHT);

        // 0..1 in vanilla; 1.0 means "cannot be knocked back at all", which doubles the trouble.
        t *= 1.0 + Math.min(Math.max(knockbackResist, 0), 1.0);
        // A mob that throws you around controls the fight's spacing.
        t *= 1.0 + Math.min(Math.max(attackKnockback, 0), 4.0) * 0.1;
        return t;
    }

    public static int slotsFromThreat(double threat) {
        if (threat <= 0) return ShadowBalance.MIN_SLOTS;
        double log = Math.log(threat / ShadowBalance.ONE_SLOT_THREAT) / Math.log(2);
        int slots = (int) Math.round(log) + 1;
        return Math.max(ShadowBalance.MIN_SLOTS, Math.min(ShadowBalance.MAX_SLOTS, slots));
    }

    // ---- role -----------------------------------------------------------------------------

    public static Role roleOf(EntityType<?> type) {
        if (!DefaultAttributes.hasSupplier(type)) return Role.NOT_LIVING;
        if (type == EntityType.PLAYER) return Role.PLAYER;
        Class<? extends Entity> cls = type.getBaseClass();
        if (EnderDragon.class.isAssignableFrom(cls)) return Role.DRAGON;
        if (!Mob.class.isAssignableFrom(cls)) return Role.UNDRIVABLE;
        if (RangedAttackMob.class.isAssignableFrom(cls)) return Role.RANGED;
        return Role.MELEE;
    }

    /** The role of a living thing that exists, which is more reliable than the registry class. */
    public static Role roleOf(LivingEntity entity) {
        if (entity instanceof Player) return Role.PLAYER;
        if (entity instanceof EnderDragon) return Role.DRAGON;
        if (!(entity instanceof Mob)) return Role.UNDRIVABLE;
        return entity instanceof RangedAttackMob ? Role.RANGED : Role.MELEE;
    }

    // ---- classification -------------------------------------------------------------------

    private static Integer override(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) return null;
        Integer configuredSlots = configured.get(id);
        if (configuredSlots != null) return configuredSlots;
        return BUILT_IN.get(id);
    }

    private static boolean tagged(EntityType<?> type) {
        return type.is(ModTags.EntityTypes.UNRAISABLE);
    }

    /** Base-value classification from the registry. This is what the report prints. */
    @SuppressWarnings("unchecked")
    public static Classification classify(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        Role role = roleOf(type);
        if (role == Role.NOT_LIVING) {
            return new Classification(id, role, false, 0, 0, 0, 0, 0, Tier.RUNT, "no attributes");
        }

        AttributeSupplier attrs = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
        double health = attrs.hasAttribute(Attributes.MAX_HEALTH) ? attrs.getBaseValue(Attributes.MAX_HEALTH) : 0;
        double damage = attrs.hasAttribute(Attributes.ATTACK_DAMAGE) ? attrs.getBaseValue(Attributes.ATTACK_DAMAGE) : 0;
        double armor = attrs.hasAttribute(Attributes.ARMOR) ? attrs.getBaseValue(Attributes.ARMOR) : 0;
        double threat = threat(health, damage, armor);

        String decidedBy = "formula";
        int slots = slotsFromThreat(threat);
        Integer override = override(type);
        if (override != null) {
            slots = Math.max(ShadowBalance.MIN_SLOTS, Math.min(ShadowBalance.MAX_SLOTS, override));
            decidedBy = configured.containsKey(id) ? "shadow_overrides.json" : "built-in override";
        }

        boolean raisable = role.supported();
        if (tagged(type)) {
            raisable = false;
            decidedBy = "#unraisable tag";
        }
        return new Classification(id, role, raisable, health, damage, armor, threat, slots,
                Tier.fromSlots(slots), decidedBy);
    }

    /**
     * Slot cost of a specific living thing, from its live values.
     *
     * <p>⚠️ Overrides still win — a pack maker who pins a modded boss at 9 gets 9 regardless of
     * how that boss scaled its health. Only the formula path reads the instance.
     */
    public static int slotsFor(LivingEntity entity) {
        Integer override = override(entity.getType());
        if (override != null) {
            return Math.max(ShadowBalance.MIN_SLOTS, Math.min(ShadowBalance.MAX_SLOTS, override));
        }
        double health = entity.getMaxHealth();
        double damage = entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? entity.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        double armor = entity.getArmorValue();
        double toughness = entity.getAttributes().hasAttribute(Attributes.ARMOR_TOUGHNESS)
                ? entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS) : 0;
        double kbResist = entity.getAttributes().hasAttribute(Attributes.KNOCKBACK_RESISTANCE)
                ? entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0;
        double attackKb = entity.getAttributes().hasAttribute(Attributes.ATTACK_KNOCKBACK)
                ? entity.getAttributeValue(Attributes.ATTACK_KNOCKBACK) : 0;

        MinecraftServer server = entity.getServer();
        if (server != null) {
            ShadowObservations seen = ShadowObservations.get(server);

            // ⚠️ The attack attribute is only a claim. A mob that has been watched hitting for
            // more than it declares is priced on what it actually did.
            damage = Math.max(damage, seen.observedDamage(entity.getType()));

            double t = threat(health, damage, armor, toughness, kbResist, attackKb);
            int slots = slotsFromThreat(t * experienceFactor(entity)) + seen.extraSlots(entity.getType());
            return Math.max(ShadowBalance.MIN_SLOTS, Math.min(ShadowBalance.MAX_SLOTS, slots));
        }
        return slotsFromThreat(threat(health, damage, armor, toughness, kbResist, attackKb));
    }

    /**
     * The mod author's own verdict on how big a deal this mob is, as a multiplier that can only
     * <b>raise</b> a cost.
     *
     * <p>⚠️ <b>Near-useless in vanilla and worth having anyway.</b> A Warden gives the same 5
     * experience as a zombie, so this does nothing for the base game. Modded bosses, by contrast,
     * almost always set a large reward deliberately — it is the one place a mod author states
     * "this thing is significant" in a form we can read. Raise-only, so a mob that drops nothing
     * is never cheapened by it.
     */
    private static double experienceFactor(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return 1.0;
        int xp = mob.getExperienceReward();
        if (xp <= ShadowBalance.XP_FACTOR_FLOOR) return 1.0;
        return Math.min(ShadowBalance.MAX_XP_FACTOR,
                1.0 + (xp - ShadowBalance.XP_FACTOR_FLOOR) / ShadowBalance.XP_FACTOR_DIVISOR);
    }

    /**
     * The cost of a type by name, used when no instance is to hand — the recosting pass works
     * from a roster, which stores a type id rather than a body.
     *
     * <p>⚠️ Base attributes plus everything observed. It cannot know the per-instance scaling a
     * live entity would have shown, so this is a floor, and recosting only ever raises.
     */
    public static int slotsForType(MinecraftServer server, String typeId) {
        ResourceLocation id = ResourceLocation.tryParse(typeId);
        EntityType<?> type = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) return ShadowBalance.MIN_SLOTS;

        Integer override = override(type);
        if (override != null) {
            return Math.max(ShadowBalance.MIN_SLOTS, Math.min(ShadowBalance.MAX_SLOTS, override));
        }

        Classification base = classify(type);
        ShadowObservations seen = ShadowObservations.get(server);
        double damage = Math.max(base.damage(), seen.observedDamage(type));
        int slots = slotsFromThreat(threat(base.health(), damage, base.armor())) + seen.extraSlots(type);
        return Math.max(ShadowBalance.MIN_SLOTS, Math.min(ShadowBalance.MAX_SLOTS, slots));
    }

    /** Can a shadow of this specific entity be made at all? Role support plus the tag. */
    public static boolean isRaisable(LivingEntity entity) {
        if (tagged(entity.getType())) return false;
        return roleOf(entity).supported();
    }
}

package com.bailey.enchantments.shadow;

/**
 * Every number in the Shadow Monarch system, in one place.
 *
 * <p>⚠️ <b>ALL FIRST GUESSES.</b> Designed 2026-09-22 and built the same night on Bailey's
 * authority, never played. Nothing here has been tuned against the game.
 * → Brain/Decisions/The Smite line is the Shadow Monarch.md
 */
public final class ShadowBalance {
    private ShadowBalance() {}

    // ---- the classifier -----------------------------------------------------------------

    /**
     * Threat at which a mob costs exactly one slot. A zombie (20hp, 3 dmg) scores 35, a spider
     * 24, so the whole runt tier lands on 1 by construction.
     */
    public static final double ONE_SLOT_THREAT = 30.0;

    /** Damage is divided by this before it multiplies health. 4 → a 30-damage Warden × 8.5. */
    public static final double DAMAGE_WEIGHT = 4.0;

    /** Slot cost floor and ceiling. The ceiling only matters for absurd modded bosses. */
    public static final int MIN_SLOTS = 1;
    public static final int MAX_SLOTS = 20;

    /** Tier boundaries, in slots. Runt is everything below SOLDIER. */
    public static final int SOLDIER_MIN_SLOTS = 3;
    public static final int ELITE_MIN_SLOTS = 5;
    public static final int MONARCH_MIN_SLOTS = 8;

    /**
     * The dragon's cost. The formula prices her at 4 because her damage is not an attribute; a
     * dragon is most of an army by design. Applied as a built-in override.
     */
    public static final int DRAGON_SLOTS = 10;

    // ---- Requiem, the army ----------------------------------------------------------------

    /** Total slots a Requiem army may hold. A Warden is 8 of these. */
    public static final int ARMY_CAP = 12;

    /** How long a shadow remnant lingers after a kill before it fades, in ticks. */
    public static final int REMNANT_TICKS = 200;

    /** Attempts a single remnant allows before it fades. Never recorded against the player. */
    public static final int RITUAL_ATTEMPTS = 3;

    /** Per-attempt success for the Elite tier (5–7 slots). */
    public static final float ELITE_ARISE_CHANCE = 0.50F;

    /** Per-attempt success for the Monarch tier (8+ slots). ~73% from one kill over 3 tries. */
    public static final float MONARCH_ARISE_CHANCE = 0.35F;

    /** Cutscene length per tier, in ticks. Runt and Soldier never freeze the player. */
    public static final int ELITE_CUTSCENE_TICKS = 80;
    public static final int MONARCH_CUTSCENE_TICKS = 120;

    /** How long the shadow takes to rise out of the ground on success, in ticks. */
    public static final int RISE_TICKS = 20;

    // ---- Umbra, the proc ------------------------------------------------------------------

    /** Chance a kill with Umbra spawns a temporary clone. */
    public static final float UMBRA_CLONE_CHANCE = 0.20F;

    /** How long an Umbra clone lasts, in ticks. */
    public static final int UMBRA_CLONE_TICKS = 600;

    /** Umbra clones alive at once, per owner. A count, not slots — Umbra has no army. */
    public static final int UMBRA_MAX_CLONES = 2;

    // ---- the AI ----------------------------------------------------------------------------

    /** Shadows wander within this many blocks of the owner. */
    public static final double WANDER_RADIUS = 8.0;

    /** Beyond this they stop what they are doing and walk back. */
    public static final double RETURN_RADIUS = 16.0;

    /** Beyond this (or in another dimension) they teleport to the owner's side. */
    public static final double TELEPORT_RADIUS = 32.0;

    /** How long an attacker stays "a threat" after hurting the owner or a shadow, in ticks. */
    public static final int THREAT_MEMORY_TICKS = 100;

    /** Shadows without an attack attribute bite for {@code 1 + health / this}. */
    public static final double BITE_HEALTH_DIVISOR = 10.0;

    /** Attack reach for the generic shadow attack goal, added to the two hitbox widths. */
    public static final double ATTACK_REACH_PADDING = 0.5;

    /** Ticks between attacks for the generic shadow attack goal. */
    public static final int ATTACK_INTERVAL_TICKS = 20;
}

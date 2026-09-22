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

    /**
     * Slot cost floor and ceiling.
     *
     * <p>⚠️ Raised to 30 on 2026-09-22. A modded mob with a fight-ending ability should be able
     * to cost most of a maxed army on its own; 20 was not enough headroom once observation
     * started multiplying threat.
     */
    public static final int MIN_SLOTS = 1;
    public static final int MAX_SLOTS = 30;

    // ---- what a mob is seen to DO ------------------------------------------------------------
    //
    // 🔴 The formula only reads what a mob DECLARES. Bailey found the hole in a modpack: Medusa
    // cost 4 slots because her health and damage are unremarkable, while she turns everything to
    // stone. No equation reads that out of another mod's code — but she demonstrates it on the
    // player, so it can be watched. -> ShadowObservations

    // ⚠️ SLOTS, NOT MULTIPLIERS. The cost curve is logarithmic — a doubling of threat buys one
    // slot — so multiplying threat by 2.6 for a petrify-everything mob moved Medusa from 4 slots
    // to 5, which answers nothing. Added slots have teeth, and read plainly.

    /** Per distinct effect that takes the fight away from you: blindness, slowness, weakness. */
    public static final int OBSERVED_DISABLING_SLOTS = 2;

    /** Per distinct effect that mostly hurts: poison, wither. */
    public static final int OBSERVED_DAMAGING_SLOTS = 1;

    /**
     * Per distinct MODDED effect. ⚠️ Deliberately the highest. Mods do not register trivial
     * status effects, and guessing high is recoverable through shadow_overrides.json while
     * guessing low is how a four-slot Medusa happens.
     */
    public static final int OBSERVED_UNKNOWN_SLOTS = 3;

    /** However much it does, observation may not add more than this to a mob's cost. */
    public static final int MAX_OBSERVED_SLOTS = 10;

    /**
     * The mod author's own verdict, read from the mob's experience reward. Vanilla hostiles sit
     * at 5, so the floor is 5: below that this does nothing at all, which is the intent for the
     * base game. A modded boss dropping 200 lands near the ceiling.
     */
    public static final int XP_FACTOR_FLOOR = 5;
    public static final double XP_FACTOR_DIVISOR = 60.0;
    public static final double MAX_XP_FACTOR = 3.0;

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

    // ---- the necromancer ladder -----------------------------------------------------------

    /**
     * Slots at necromancer level 1, and at max. Bailey, 2026-09-22: <i>"you start with 3 slots
     * and you level up just like the anime... have a way higher cap then 12 once you hit max
     * level so max level army users are way more broken but it takes a lot of time to reach."</i>
     */
    public static final int BASE_ARMY_SLOTS = 3;
    public static final int MAX_ARMY_SLOTS = 40;

    /** ✅ Bailey's pick. Level 100 would have been ~15,000 zombie kills. */
    public static final int MAX_NECROMANCER_LEVEL = 50;

    /**
     * A victim's slot cost is multiplied by this, then divided by {@link #XP_DIVISOR}, to give
     * the XP its death feeds. Zombie 2, enderman 6, Warden 16.
     */
    public static final int XP_PER_SLOT = 8;

    /** ✅ Bailey: <i>"all xp taken in by the shadow army is divided by 4."</i> */
    public static final int XP_DIVISOR = 4;

    /**
     * 🔴 THE ANTI-AFK GATE. Bailey: <i>"after lets say 10 kills with your army you no longer gain
     * army xp until you kill a mob. Removes afk mob farming to max level overnight."</i>
     */
    public static final int SHADOW_KILLS_PER_OWN_KILL = 10;

    /**
     * ⚠️ Deployed bodies, whatever the slots allow. 40 slots of runts would be 40 mobs with AI
     * and auras per player; this keeps a big army meaning big soldiers rather than a swarm.
     */
    public static final int MAX_DEPLOYED_BODIES = 20;

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

    // ---- soldier levels and intelligence -----------------------------------------------------

    /** Extra max health per level above 1, as a fraction of base. Level 50 = +98%. */
    public static final double SOLDIER_HEALTH_PER_LEVEL = 0.02;

    /** Extra attack damage per level above 1, as a fraction of base. Level 50 = +147%. */
    public static final double SOLDIER_DAMAGE_PER_LEVEL = 0.03;

    /** The intelligence ceiling. Each rung is a thing the AI learns to do. */
    public static final int MAX_INTELLIGENCE = 5;

    /**
     * A downed shadow mends this fraction of its max health per second in storage. 0.5% ≈ three
     * and a half minutes from nothing to full — a cooldown, not a punishment.
     */
    public static final float DOWNED_REGEN_PER_SECOND = 0.005F;

    /** A downed shadow cannot be deployed until it has mended this much of its health. */
    public static final float DEPLOYABLE_AT = 0.35F;

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

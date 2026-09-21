package com.bailey.enchantments.util;

// Every tunable number for the Deep Dark expansion, in one place.
//
// This exists because there IS no config system — Config.java is 9 dead lines and nothing
// reads it. Until that changes, editing this file and recompiling is how the expansion gets
// balanced. Keep it free of logic: constants only, so anyone tuning it never has to reason
// about behaviour.
//
// ⚠️ EVERY NUMBER HERE IS A STARTING POINT FOR PLAYTESTING, NOT A DECISION. The design
// target Bailey agreed on 2026-08-09 is "a comfortable win in a max set — very easy, but
// not immune". Only in-game results move these.
//
// ⚠️ The single most important fact behind these numbers: minecraft:sonic_boom is in the
// bypasses_armor tag, verified against the vanilla jar. ARMOR POINTS DO NOTHING against the
// Warden's signature attack. bypasses_invulnerability holds only out_of_world/generic_kill,
// so Protection-style REDUCTION still applies. That is why the sonic boom levers below are
// the load-bearing ones, and why "make the armor stronger" cannot mean bigger armor values.
// If a playtest still feels dangerous, raise SET_SONIC_BOOM_REDUCTION before anything else.
public final class DeepDarkBalance {

    private DeepDarkBalance() {}

    // ---- Sculkbane (Phase 2) -------------------------------------------------------
    // Agreed target: netherite sword ~38/hit vs a 500 HP Warden → ~14 hits, ~10 with crits.
    public static final int SCULKBANE_MAX_LEVEL = 5;
    public static final float SCULKBANE_DAMAGE_PER_LEVEL = 6.0F;

    // ---- Deepward (Phase 2) --------------------------------------------------------
    public static final int DEEPWARD_MAX_LEVEL = 4;

    // Split of the agreed "~80% total sonic boom reduction at Deepward IV + full set":
    // 40% from Deepward IV, 40% from the set. ⚠️ The 80% total is from the plan; THE SPLIT
    // BETWEEN THE TWO IS MINE and has never been reviewed. If the set should carry more of
    // it (it is the harder thing to obtain), move the weight to SET_SONIC_BOOM_REDUCTION.
    //
    // ⚠️ READ AS: this much per level AT A FULL SET OF ARMOR. Deepward IV on all four pieces
    // is 40%; the same enchant on one piece is 10%. DeepDarkCombatEvents scales by pieces
    // worn, NOT by vanilla's sum-of-EPF-per-piece model, which would have made a full set of
    // IV worth 160% before clamping and every level after the first meaningless.
    public static final float DEEPWARD_REDUCTION_PER_LEVEL = 0.10F;

    // ---- Corrupted armor set bonuses (Phase 6) -------------------------------------
    // Full set only. Applied on top of armor points, which are netherite-identical.
    public static final float SET_SONIC_BOOM_REDUCTION = 0.40F;
    public static final float SET_DEEP_DARK_DAMAGE_REDUCTION = 0.10F;
    public static final float SET_SPEED_BONUS = 0.10F;

    // ⚠️ Hard ceiling on ALL stacked reduction from this mod. Nothing may reach 1.0 —
    // "very easy, NOT immune" was an explicit call, and an immune player is a different
    // game. Every reduction site must clamp against this, not just the ones that look
    // like they could overflow today.
    public static final float MAX_TOTAL_REDUCTION = 0.90F;

    // ---- Acceptance targets --------------------------------------------------------
    // Not used in code — these are what the numbers above are supposed to PRODUCE, kept
    // here so a future tuning pass can tell "wrong number" from "wrong target".
    // Warden: 500 HP, melee ~30 on hard, sonic boom 10 armor-ignoring and ranged.
    // With a full corrupted set + Deepward IV, in the Deep Dark:
    //   sonic boom  10 → ~2
    //   melee       30 → ~5
    // ✅ SETTLED IN PHASE 2, 2026-08-12 — the question this comment used to leave open:
    //   • Deepward reduces ALL incoming damage while the Deep Dark rule applies, not just
    //     sonic boom. It is an alternative to Protection, and Protection is not type-specific.
    //     It respects the same bypasses_enchantments guard vanilla protection does.
    //   • Deepward and the set bonuses ADD, then clamp once, in DeepDarkCombatEvents. They do
    //     not each multiply — that would give 64% where 80% is intended.
    //   • The sum is applied at LivingHurtEvent, i.e. BEFORE vanilla's armor absorption, like
    //     Aegis and over-cap Protection already are. Both targets above land on this ordering.
    //   • The set's sonic-boom reduction replaces its generic deep dark reduction for sonic
    //     damage rather than stacking with it.

    // ---- Secret enchantments (Phase 7) ---------------------------------------------
    // All or nothing: on both success and failure the ULTIMATE is destroyed and the crystal
    // is consumed.
    //
    // ✅ 0.50 — BAILEY'S NUMBER, set 2026-09-21 ("the odds should be a lot higher like 50/50
    // but you have to get the ultimate to make it a secret"). It was 0.20, set 2026-09-19,
    // when the input was a maxed BASE enchantment rather than an Ultimate.
    //
    // ⚠️ THE ODDS AND THE INPUT MOVED TOGETHER. Reading this number without the redesign
    // beside it will badly mislead: the Secret tier now sits ABOVE the Ultimate tier, so
    // every attempt spends a finished Ultimate, not a maxed Sharpness. Raising the odds was
    // not a softening on its own — it paid for a much more expensive input.
    //
    // ⚠️ WHAT THIS IS WORTH, so nobody re-tunes it blind: at 20% Heart drop, 1 Heart per
    // crystal and this roll, one SUCCESS costs ~15 Wardens (2 gambles + rebuilding the one
    // Ultimate a failed roll destroyed = 3 Echo Upgrade Templates, 2 Hearts, both 20% drops),
    // down from 25 — AND ~2 Ultimates, up from 0. ⚠️ THE BINDING DROP IS THE TEMPLATE, NOT THE
    // HEART: you need more templates than Hearts, so the Heart dials are nearly inert in the
    // easier direction. The template is shared with every Ultimate — see Open decisions #18.
    public static final float SECRET_SUCCESS_CHANCE = 0.50F;


    // ---- Mirage and Dissonance (the evasion armour line) ---------------------------
    //
    // Mirage is the Ultimate on Projectile Protection; Dissonance is the Secret gambled from
    // it. Designed by Bailey 2026-09-22 -> Brain/Decisions/Open decisions.md #31.
    //
    // ⚠️ EVERY PASSIVE SCALES PER PIECE. Four independent rolls at a flat rate would stack
    // toward total immunity, which is why nothing here is a per-piece constant applied four
    // times -- they are multiplied by the piece count and capped.

    /** Movement speed per piece, MULTIPLY_TOTAL. Four pieces = +24%. */
    public static final double MIRAGE_SPEED_PER_PIECE = 0.06;

    /** Attack speed per piece, MULTIPLY_TOTAL. Four pieces = +20%. */
    public static final double MIRAGE_ATTACK_SPEED_PER_PIECE = 0.05;

    /** Chance to ignore a hit entirely, per piece. Four pieces = 20%. */
    public static final float MIRAGE_EVADE_PER_PIECE = 0.05F;

    /** Extra evade while Dissonance's mode is on, on top of the per-piece total. */
    public static final float DISSONANCE_EVADE_BONUS = 0.10F;

    /**
     * ⚠️ THE CAP EXISTS SO EVASION CAN NEVER BECOME IMMUNITY. Bailey's design stacks a
     * per-piece chance with a mode bonus, and without a ceiling a future buff to either
     * silently produces an unkillable player.
     */
    public static final float EVADE_CAP = 0.45F;

    /** Damage taken while the mode is on. Bailey: "it makes you 50% weaker to hits". */
    public static final float DISSONANCE_DAMAGE_TAKEN = 1.5F;

    /** Extra block-selection range while the mode is on, for the flash step. */
    public static final double DISSONANCE_REACH_BONUS = 3.5;

    /** Between toggles, so the mode cannot be flickered to dodge a single hit. */
    public static final int DISSONANCE_TOGGLE_COOLDOWN = 20;

    /**
     * Teleport lockout after the held item changes.
     *
     * ⚠️ THIS IS THE WHOLE COST OF THE MODE. Bailey, 2026-09-22: a weapon swap "just gives a 1s
     * cooldown before you can teleport again". Without it: weapon out, hit, swap to empty hand,
     * flash away, swap back -- an untouchable kiting loop with no downside, because "+50% damage
     * taken" only matters if something lands.
     */
    public static final int DISSONANCE_SWAP_LOCKOUT = 20;
    // ---- Placeholders, NOT yet designed --------------------------------------------
    // ⚠️ Shatter and Resonance have agreed *identities* but no agreed numbers — the plan
    // says only "large bonus vs deep dark targets" and "a sonic pulse damaging nearby
    // enemies". These exist so Phases 3 and 7 have a home to tune in, and so nobody
    // hardcodes a literal at the call site. Treat them as unreviewed.
    //
    // ⚠️ SHATTER_DAMAGE_BONUS is now LIVE (Phase 3, 2026-08-12) and is still my number, not
    // Bailey's. For scale: it puts a netherite sword at ~53 per hit against a 500 HP Warden,
    // about 10 hits, against Sculkbane V's ~38 and ~14. Silence has no number to tune — it is
    // absolute by design.
    public static final float SHATTER_DAMAGE_BONUS = 45.0F;
    public static final float RESONANCE_PULSE_DAMAGE = 6.0F;
    public static final double RESONANCE_PULSE_RADIUS = 4.0D;
}

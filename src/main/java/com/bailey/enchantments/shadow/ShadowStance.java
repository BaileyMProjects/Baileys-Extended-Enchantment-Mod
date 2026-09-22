package com.bailey.enchantments.shadow;

/**
 * How an owner's army behaves. Guard is the bodyguard Bailey designed; the other two are the
 * opt-in ends of it — Passive for a village or a build site, Aggressive for the grinder that
 * was deliberately not made the default.
 */
public enum ShadowStance {
    /** Fights what fights you, and what you fight. Leaves idle hostiles alone. */
    GUARD,
    /** Follows only. Never takes a target. */
    PASSIVE,
    /** Guard, plus any hostile within the vicinity, unprovoked. */
    AGGRESSIVE;

    public ShadowStance next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static ShadowStance byName(String name) {
        for (ShadowStance s : values()) if (s.name().equalsIgnoreCase(name)) return s;
        return GUARD;
    }
}

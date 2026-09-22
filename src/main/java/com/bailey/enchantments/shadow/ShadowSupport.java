package com.bailey.enchantments.shadow;

/**
 * Which shadow roles have an entity behind them yet. Flipped as the build phases land, so the
 * classifier and the report tell the truth about what can actually be raised today.
 */
final class ShadowSupport {
    private ShadowSupport() {}

    /** Phase 6: {@code ShadowPlayer}. */
    static final boolean PLAYERS = true;

    /** Phase 7: {@code ShadowDragon}. */
    static final boolean DRAGON = true;
}

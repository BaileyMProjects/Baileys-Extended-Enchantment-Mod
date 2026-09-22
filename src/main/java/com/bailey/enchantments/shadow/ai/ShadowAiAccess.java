package com.bailey.enchantments.shadow.ai;

import com.bailey.enchantments.shadow.ShadowAi;

import net.minecraft.world.entity.Mob;

/**
 * Lets the goals in this package clear a target the way {@link ShadowAi} sets one — through the
 * brain as well as the field, because the Warden and the piglins read only the brain.
 */
final class ShadowAiAccess {
    private ShadowAiAccess() {}

    static void clearTarget(Mob mob) {
        ShadowAi.setTarget(mob, null);
    }
}

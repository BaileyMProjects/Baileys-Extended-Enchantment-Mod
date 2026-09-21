package com.bailey.enchantments.util;

/**
 * Lets an operator's {@code /enchant} command bypass the same restriction that keeps mod
 * enchantments out of vanilla loot/fishing/trades — running {@code /enchant} is a deliberate,
 * permission-gated admin action, the same category of "intentional" as the Ancient Anvil
 * ritual, not something that should silently happen to a player.
 *
 * Unlike {@link AncientEnchantContext}, this can't be bracketed with a try/finally around a
 * single call: Forge's {@code CommandEvent} fires *before* the vanilla command actually runs,
 * and the run happens back in vanilla code we don't control, on the same thread, later in the
 * same tick. So this flag is set when an /enchant command is seen, and cleared at the end of
 * that same server tick (see ModUltimateEvents#onLevelTick) rather than via a narrow scope.
 */
public final class EnchantCommandOverride {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private EnchantCommandOverride() {}

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static void markActiveForThisTick() {
        ACTIVE.set(true);
    }

    public static void clear() {
        ACTIVE.set(false);
    }
}

package com.bailey.enchantments.util;

/**
 * Marks the current thread as being inside a deliberate player action — the Ancient
 * Enchanting Table or the Ancient Anvil's Forge tab — that is allowed to put a mod
 * (non-ultimate) enchantment directly onto a piece of gear instead of only a book.
 *
 * Every other path (vanilla loot generation, fishing, villager trades, dispensers, ...)
 * leaves this flag false, so {@code ModEnchantments.SimpleModEnchantment#canEnchant}
 * only ever reports "yes" for a non-book item when one of our own menus set it.
 *
 * Ultimates ignore this flag entirely — they are never appliable via canEnchant at all,
 * see {@code ModEnchantments.UltimateEnchantment}.
 */
public final class AncientEnchantContext {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private AncientEnchantContext() {}

    public static boolean isActive() {
        return ACTIVE.get();
    }

    /** Runs {@code action} with the context flag active, always restoring it afterwards. */
    public static void runActive(Runnable action) {
        boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try {
            action.run();
        } finally {
            ACTIVE.set(previous);
        }
    }

    /** Same as {@link #runActive(Runnable)} but for a value-returning computation. */
    public static <T> T runActive(java.util.function.Supplier<T> action) {
        boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try {
            return action.get();
        } finally {
            ACTIVE.set(previous);
        }
    }
}

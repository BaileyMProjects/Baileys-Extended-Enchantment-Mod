package com.bailey.enchantments.shadow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each owner's current stance. Server-side. Persisted with the army in
 * {@link ShadowArmy}; this is just the live lookup the target goal reads every ten ticks
 * without touching NBT.
 */
public final class ShadowStances {
    private ShadowStances() {}

    private static final Map<UUID, ShadowStance> STANCES = new ConcurrentHashMap<>();

    public static ShadowStance get(UUID owner) {
        return STANCES.getOrDefault(owner, ShadowStance.GUARD);
    }

    public static void set(UUID owner, ShadowStance stance) {
        STANCES.put(owner, stance);
    }

    public static void forget(UUID owner) {
        STANCES.remove(owner);
    }
}

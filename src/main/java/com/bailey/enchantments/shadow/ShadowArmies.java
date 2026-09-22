package com.bailey.enchantments.shadow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every army in the world, saved with the Overworld.
 *
 * <p>World saved data rather than player NBT on purpose: player NBT is rebuilt on death and on
 * dimension change and has to be copied across by hand (see {@code SecretReveal}), and an army
 * that vanished on the first death would be exactly the invisible failure this mod keeps
 * getting bitten by. Saved data has one copy, keyed by the owner's UUID, and none of that.
 */
public final class ShadowArmies extends SavedData {
    private static final String NAME = "baileys_shadow_armies";

    private final Map<UUID, ShadowArmy> armies = new HashMap<>();

    public static ShadowArmies get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ShadowArmies::load, ShadowArmies::new, NAME);
    }

    public ShadowArmy armyOf(UUID owner) {
        return armies.computeIfAbsent(owner, id -> new ShadowArmy());
    }

    public boolean has(UUID owner) {
        return armies.containsKey(owner);
    }

    /** Call after any mutation. Cheap; the world save does the real work later. */
    public void markDirty() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, ShadowArmy> e : armies.entrySet()) {
            all.put(e.getKey().toString(), e.getValue().save());
        }
        tag.put("Armies", all);
        return tag;
    }

    private static ShadowArmies load(CompoundTag tag) {
        ShadowArmies data = new ShadowArmies();
        CompoundTag all = tag.getCompound("Armies");
        for (String key : all.getAllKeys()) {
            if (all.get(key) instanceof CompoundTag armyTag && all.getTagType(key) == Tag.TAG_COMPOUND) {
                try {
                    data.armies.put(UUID.fromString(key), ShadowArmy.load(armyTag));
                } catch (IllegalArgumentException ignored) {
                    // not a UUID key; skip rather than refuse to load the world
                }
            }
        }
        return data;
    }
}

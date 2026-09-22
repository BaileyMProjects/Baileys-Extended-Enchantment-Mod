package com.bailey.enchantments.shadow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What mobs have actually been seen to <b>do</b>, remembered per entity type.
 *
 * <h2>🔴 Why this exists</h2>
 * Bailey, 2026-09-22, testing in a modpack: <i>"for 4 slots i was able to tame medusa who doesnt
 * do much damage but she turns everything to stone, which not only makes u unable to level up
 * but its also insanely broken for 4 slots."</i>
 *
 * <p>He is right and the formula cannot answer it alone. Health, damage and armour are the only
 * things a mob <b>declares</b>, and "turns everything to stone" is arbitrary Java behind an
 * interface we cannot inspect. No equation reads that. But the mob will happily <b>show</b> us,
 * because it does it to the player.
 *
 * <p>⚠️ <b>Every mob is watched, not only raised ones.</b> That ordering is the whole point:
 * Medusa petrifies you while you are fighting her, so by the time you stand over her corpse the
 * cost is already right, and you never get to bind her for four slots at all.
 *
 * <h2>What is recorded</h2>
 * <ul>
 *   <li><b>Harmful effects it applies.</b> Petrification, in most mods, <i>is</i> a MobEffect.
 *   <li><b>The most damage it has ever dealt in one hit</b>, which catches a mob whose attack
 *       attribute is a polite fiction because it hurts you through its own code.
 * </ul>
 *
 * <p>⚠️ <b>An unknown modded effect is priced HIGH, not low.</b> Mods do not add trivial status
 * effects; if something bothered to register one and put it on its attacks, assume it matters.
 * Guessing high is recoverable — the pack maker lowers it in {@code shadow_overrides.json}.
 * Guessing low is how a four-slot Medusa happens.
 */
public final class ShadowObservations extends SavedData {
    private static final String NAME = "baileys_shadow_observations";

    /** Per type: which harmful effects it has applied, and the hardest hit it has landed. */
    public static final class Record {
        public final Set<String> effects = new HashSet<>();
        public float maxDamage;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (String id : effects) list.add(StringTag.valueOf(id));
            tag.put("Effects", list);
            tag.putFloat("MaxDamage", maxDamage);
            return tag;
        }

        static Record load(CompoundTag tag) {
            Record r = new Record();
            for (Tag t : tag.getList("Effects", Tag.TAG_STRING)) r.effects.add(t.getAsString());
            r.maxDamage = tag.getFloat("MaxDamage");
            return r;
        }
    }

    private final Map<ResourceLocation, Record> seen = new HashMap<>();

    public static ShadowObservations get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ShadowObservations::load, ShadowObservations::new, NAME);
    }

    // ---- recording ---------------------------------------------------------------------------

    /** Returns true if this is something we had not seen this type do before. */
    public boolean observeEffect(EntityType<?> type, MobEffect effect) {
        if (effect.getCategory() != MobEffectCategory.HARMFUL) return false;
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(type);
        ResourceLocation effectId = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (typeId == null || effectId == null) return false;

        Record record = seen.computeIfAbsent(typeId, k -> new Record());
        if (!record.effects.add(effectId.toString())) return false;
        setDirty();
        return true;
    }

    /** Returns true if this beat the hardest hit we had recorded for the type. */
    public boolean observeDamage(EntityType<?> type, float amount) {
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (typeId == null || amount <= 0) return false;

        Record record = seen.computeIfAbsent(typeId, k -> new Record());
        if (amount <= record.maxDamage) return false;
        record.maxDamage = amount;
        setDirty();
        return true;
    }

    // ---- reading -----------------------------------------------------------------------------

    public Record of(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id == null ? new Record() : seen.getOrDefault(id, new Record());
    }

    /**
     * Extra slots this type has earned by what it has been seen to do.
     *
     * <h3>⚠️ SLOTS, not a threat multiplier — and that matters enormously</h3>
     * The cost curve is <b>logarithmic</b>: every doubling of threat buys one slot. So
     * multiplying threat by 2.6 for a mob that petrifies everything moved Medusa from 4 slots to
     * 5, which is no answer at all. Adding slots outright is the only thing with teeth on a log
     * scale, and it reads plainly besides: "this blinds you, that is worth two slots."
     *
     * <p>Control is what the formula was blind to, so control is what this prices. A mob that
     * takes the fight away from you is worth more than one that merely hits hard — exactly the
     * case a health-and-damage equation gets backwards.
     */
    public int extraSlots(EntityType<?> type) {
        Record record = of(type);
        int extra = 0;
        for (String id : record.effects) {
            extra += weightOf(id);
        }
        return Math.min(extra, ShadowBalance.MAX_OBSERVED_SLOTS);
    }

    /** The hardest hit seen from this type, for use when its attack attribute understates it. */
    public float observedDamage(EntityType<?> type) {
        return of(type).maxDamage;
    }

    /**
     * What one observed effect is worth.
     *
     * <p>⚠️ Ordered by how much of the fight it takes away from you, not by how much damage it
     * does. Blindness ends an encounter more decisively than Poison ever will.
     */
    private static int weightOf(String effectId) {
        if (!effectId.startsWith("minecraft:")) {
            // A modded effect. No way to judge it, so judge it expensive — see the class note.
            return ShadowBalance.OBSERVED_UNKNOWN_SLOTS;
        }
        ResourceLocation id = ResourceLocation.tryParse(effectId);
        MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
        if (effect == null) return ShadowBalance.OBSERVED_UNKNOWN_SLOTS;

        if (effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS
                || effect == MobEffects.LEVITATION || effect == MobEffects.CONFUSION
                || effect == MobEffects.DIG_SLOWDOWN || effect == MobEffects.WEAKNESS
                || effect == MobEffects.MOVEMENT_SLOWDOWN) {
            return ShadowBalance.OBSERVED_DISABLING_SLOTS;
        }
        return ShadowBalance.OBSERVED_DAMAGING_SLOTS;
    }

    // ---- persistence ---------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<ResourceLocation, Record> e : seen.entrySet()) {
            all.put(e.getKey().toString(), e.getValue().save());
        }
        tag.put("Seen", all);
        return tag;
    }

    private static ShadowObservations load(CompoundTag tag) {
        ShadowObservations data = new ShadowObservations();
        CompoundTag all = tag.getCompound("Seen");
        for (String key : all.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) data.seen.put(id, Record.load(all.getCompound(key)));
        }
        return data;
    }
}

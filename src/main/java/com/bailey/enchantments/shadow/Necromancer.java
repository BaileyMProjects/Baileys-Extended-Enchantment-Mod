package com.bailey.enchantments.shadow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The player's own ladder: how many slots their army may hold, and what carrying it gives them.
 *
 * <p>Bailey, 2026-09-22: <i>"it feels weird to be a master of necromancy the moment you get the
 * enchantment I think it should scale like it does in the show."</i>
 *
 * <p>✅ <b>Kept on the PLAYER, not the blade</b> — Bailey, asked directly. The army stays
 * blade-bound, so the two come apart on purpose: pick up someone else's sword and you command
 * their roster <b>at your own level</b>.
 *
 * <p>World saved data, like the armies and the Secret ledger: one copy keyed by UUID, immune to
 * the death-and-dimension-change rebuild that player NBT suffers.
 */
public final class Necromancer extends SavedData {
    private static final String NAME = "baileys_necromancers";

    /** One record per player. */
    public static final class Record {
        public int xp;
        public int level = 1;
        /** Shadow kills since this player last killed something themselves. The AFK gate. */
        public int killsSinceOwn;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Xp", xp);
            tag.putInt("Level", level);
            tag.putInt("KillsSinceOwn", killsSinceOwn);
            return tag;
        }

        static Record load(CompoundTag tag) {
            Record r = new Record();
            r.xp = tag.getInt("Xp");
            r.level = Math.max(1, tag.getInt("Level"));
            r.killsSinceOwn = tag.getInt("KillsSinceOwn");
            return r;
        }
    }

    private final Map<UUID, Record> records = new HashMap<>();

    public static Necromancer get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Necromancer::load, Necromancer::new, NAME);
    }

    public Record of(UUID player) {
        return records.computeIfAbsent(player, p -> new Record());
    }

    public int levelOf(UUID player) {
        return of(player).level;
    }

    public int slotsOf(UUID player) {
        return slotsForLevel(levelOf(player));
    }

    // ---- the curve --------------------------------------------------------------------------

    /**
     * XP to go from {@code level} to the next, using <b>vanilla's own player curve</b> — Bailey:
     * <i>"the levelling scales like player xp."</i> Verified against {@code Player.getXpNeededForNextLevel}.
     */
    public static int xpToNext(int level) {
        if (level >= ShadowBalance.MAX_NECROMANCER_LEVEL) return Integer.MAX_VALUE;
        if (level >= 31) return 9 * level - 158;
        if (level >= 16) return 5 * level - 38;
        return 2 * level + 7;
    }

    /** Slots at a level: a straight line from 3 at level 1 to the cap at max. */
    public static int slotsForLevel(int level) {
        int max = Math.max(1, ShadowBalance.MAX_NECROMANCER_LEVEL);
        int span = ShadowBalance.MAX_ARMY_SLOTS - ShadowBalance.BASE_ARMY_SLOTS;
        int clamped = Math.max(1, Math.min(max, level));
        return ShadowBalance.BASE_ARMY_SLOTS + Math.round(span * (clamped - 1) / (float) (max - 1));
    }

    // ---- earning ----------------------------------------------------------------------------

    /**
     * Award XP for a shadow's kill. Returns the number of levels gained, so the caller can make
     * a noise about it.
     *
     * <p>⚠️ The caller checks the AFK gate; this does not, because the soldier's own ladder is
     * awarded from the same place and the gate applies to both.
     */
    public int award(UUID player, int amount) {
        if (amount <= 0) return 0;
        Record r = of(player);
        if (r.level >= ShadowBalance.MAX_NECROMANCER_LEVEL) return 0;

        r.xp += amount;
        int gained = 0;
        while (r.level < ShadowBalance.MAX_NECROMANCER_LEVEL) {
            int need = xpToNext(r.level);
            if (r.xp < need) break;
            r.xp -= need;
            r.level++;
            gained++;
        }
        if (r.level >= ShadowBalance.MAX_NECROMANCER_LEVEL) r.xp = 0;
        setDirty();
        return gained;
    }

    /** A shadow killed something. Returns false if the AFK gate is shut. */
    public boolean noteShadowKill(UUID player) {
        Record r = of(player);
        if (r.killsSinceOwn >= ShadowBalance.SHADOW_KILLS_PER_OWN_KILL) return false;
        r.killsSinceOwn++;
        setDirty();
        return true;
    }

    /** The player killed something themselves. The gate reopens. */
    public void noteOwnKill(UUID player) {
        Record r = of(player);
        if (r.killsSinceOwn == 0) return;
        r.killsSinceOwn = 0;
        setDirty();
    }

    public boolean gateShut(UUID player) {
        return of(player).killsSinceOwn >= ShadowBalance.SHADOW_KILLS_PER_OWN_KILL;
    }

    public void markDirty() {
        setDirty();
    }

    // ---- persistence ----------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, Record> e : records.entrySet()) {
            all.put(e.getKey().toString(), e.getValue().save());
        }
        tag.put("Necromancers", all);
        return tag;
    }

    private static Necromancer load(CompoundTag tag) {
        Necromancer data = new Necromancer();
        CompoundTag all = tag.getCompound("Necromancers");
        for (String key : all.getAllKeys()) {
            try {
                data.records.put(UUID.fromString(key), Record.load(all.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // not a UUID key; skip rather than refuse to load the world
            }
        }
        return data;
    }
}

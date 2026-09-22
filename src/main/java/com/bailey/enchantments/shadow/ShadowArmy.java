package com.bailey.enchantments.shadow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * One owner's roster: the soldiers bound by <i>Arise</i>, whether each is out in the world, and
 * the stance they fight under.
 *
 * <p>A soldier is a <b>snapshot</b>, not an entity — the entity is made from it on deploy and
 * discarded on recall. That is what lets the army survive the owner's death, logout, and a
 * server restart with nothing left standing in the world, which Bailey asked for: <i>"Owner dies
 * or logs out the army is sent to storage."</i>
 */
public final class ShadowArmy {

    public static final class Soldier {
        public final UUID id;
        public final String typeId;
        /**
         * ⚠️ Not final: observation can re-price a soldier upward after it was raised.
         * -> ShadowObserver.recost
         */
        public int slots;
        public final CompoundTag snapshot;
        public String name;
        public boolean deployed;
        @Nullable public UUID entityId;

        /** The soldier's own ladder. It keeps this through death — ✅ Bailey, asked directly. */
        public int level = 1;
        public int xp;

        /**
         * Health carried between deployments, and while downed.
         *
         * <p>⚠️ A killed shadow is <b>downed, not destroyed</b> — Bailey, 2026-09-22: <i>"when
         * your soldier dies it just goes back into your army and doesnt dissapear but they just
         * sit at 0 health and slowly regenerate... kind of like a pokemon."</i> −1 means "full",
         * so a soldier that has never fought does not need a max-health lookup to store one.
         */
        public float storedHealth = -1.0F;

        /**
         * The body's max health when it was last seen, so the roster can judge "how hurt is it"
         * without spawning one. 0 until the soldier has been deployed once.
         */
        public float maxHealth;

        /** Max health as best the roster knows it. Never zero, so a fraction is always safe. */
        public float maxHealthEstimate() { return maxHealth > 0 ? maxHealth : 20.0F; }

        public boolean downed() { return storedHealth == 0.0F; }

        public Soldier(UUID id, String typeId, String name, int slots, CompoundTag snapshot) {
            this.id = id;
            this.typeId = typeId;
            this.name = name;
            this.slots = slots;
            this.snapshot = snapshot;
        }

        public ShadowClassifier.Tier tier() {
            return ShadowClassifier.Tier.fromSlots(slots);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Type", typeId);
            tag.putString("Name", name);
            tag.putInt("Slots", slots);
            tag.put("Snapshot", snapshot);
            tag.putBoolean("Deployed", deployed);
            if (entityId != null) tag.putUUID("Entity", entityId);
            tag.putInt("Level", level);
            tag.putInt("Xp", xp);
            tag.putFloat("StoredHealth", storedHealth);
            tag.putFloat("MaxHealth", maxHealth);
            return tag;
        }

        static Soldier load(CompoundTag tag) {
            Soldier s = new Soldier(tag.getUUID("Id"), tag.getString("Type"), tag.getString("Name"),
                    tag.getInt("Slots"), tag.getCompound("Snapshot"));
            s.deployed = tag.getBoolean("Deployed");
            s.entityId = tag.hasUUID("Entity") ? tag.getUUID("Entity") : null;
            s.level = Math.max(1, tag.getInt("Level"));
            s.xp = tag.getInt("Xp");
            s.storedHealth = tag.contains("StoredHealth") ? tag.getFloat("StoredHealth") : -1.0F;
            s.maxHealth = tag.getFloat("MaxHealth");
            return s;
        }
    }

    private final List<Soldier> soldiers = new ArrayList<>();
    private ShadowStance stance = ShadowStance.GUARD;

    /** Who has this army deployed right now: the blade's holder. Null while everything is stored. */
    @Nullable private UUID holder;

    @Nullable
    public UUID holder() { return holder; }

    public void setHolder(@Nullable UUID holder) { this.holder = holder; }

    public List<Soldier> soldiers() {
        return Collections.unmodifiableList(soldiers);
    }

    @Nullable
    public Soldier find(UUID id) {
        for (Soldier s : soldiers) if (s.id.equals(id)) return s;
        return null;
    }

    public void add(Soldier soldier) {
        soldiers.add(soldier);
    }

    public boolean remove(UUID id) {
        return soldiers.removeIf(s -> s.id.equals(id));
    }

    public int usedSlots() {
        int n = 0;
        for (Soldier s : soldiers) n += s.slots;
        return n;
    }

    /**
     * ⚠️ Free slots depend on WHO IS ASKING. The army lives in the blade, the cap lives with the
     * necromancer, so a roster can exceed its current holder's cap — pick up a veteran's sword
     * as a novice and you simply cannot field all of it.
     */
    public int freeSlots(int cap) {
        return Math.max(0, cap - usedSlots());
    }

    public boolean anyDeployed() {
        for (Soldier s : soldiers) if (s.deployed) return true;
        return false;
    }

    public ShadowStance stance() {
        return stance;
    }

    public void setStance(ShadowStance stance) {
        this.stance = stance;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Soldier s : soldiers) list.add(s.save());
        tag.put("Soldiers", list);
        tag.putString("Stance", stance.name());
        if (holder != null) tag.putUUID("Holder", holder);
        return tag;
    }

    static ShadowArmy load(CompoundTag tag) {
        ShadowArmy army = new ShadowArmy();
        for (Tag t : tag.getList("Soldiers", Tag.TAG_COMPOUND)) {
            army.soldiers.add(Soldier.load((CompoundTag) t));
        }
        army.stance = ShadowStance.byName(tag.getString("Stance"));
        army.holder = tag.hasUUID("Holder") ? tag.getUUID("Holder") : null;
        return army;
    }
}

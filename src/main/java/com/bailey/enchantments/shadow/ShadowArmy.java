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
        public final int slots;
        public final CompoundTag snapshot;
        public String name;
        public boolean deployed;
        @Nullable public UUID entityId;

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
            return tag;
        }

        static Soldier load(CompoundTag tag) {
            Soldier s = new Soldier(tag.getUUID("Id"), tag.getString("Type"), tag.getString("Name"),
                    tag.getInt("Slots"), tag.getCompound("Snapshot"));
            s.deployed = tag.getBoolean("Deployed");
            s.entityId = tag.hasUUID("Entity") ? tag.getUUID("Entity") : null;
            return s;
        }
    }

    private final List<Soldier> soldiers = new ArrayList<>();
    private ShadowStance stance = ShadowStance.GUARD;

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

    public int freeSlots() {
        return Math.max(0, ShadowBalance.ARMY_CAP - usedSlots());
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
        return tag;
    }

    static ShadowArmy load(CompoundTag tag) {
        ShadowArmy army = new ShadowArmy();
        for (Tag t : tag.getList("Soldiers", Tag.TAG_COMPOUND)) {
            army.soldiers.add(Soldier.load((CompoundTag) t));
        }
        army.stance = ShadowStance.byName(tag.getString("Stance"));
        return army;
    }
}

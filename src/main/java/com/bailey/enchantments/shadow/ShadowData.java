package com.bailey.enchantments.shadow;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * What makes an entity a shadow: a compound in its Forge persistent data.
 *
 * <p>Persistent data is used rather than a capability or a synched entity field because it is
 * the one thing that survives on <b>any</b> entity class, ours or a stranger's, and is saved and
 * loaded with the entity for free. That is exactly the property a system that must work on every
 * modded mob needs. The cost is that it is server-only and not synched — the renderer learns who
 * is a shadow from a tracking packet instead.
 */
public final class ShadowData {
    private ShadowData() {}

    private static final String ROOT = "BaileysShadow";
    private static final String OWNER = "Owner";
    private static final String ROSTER_ID = "RosterId";
    private static final String SLOTS = "Slots";
    private static final String TEMPORARY = "Temporary";
    private static final String EXPIRES = "ExpiresAt";

    public static boolean isShadow(@Nullable Entity entity) {
        return entity != null && entity.getPersistentData().contains(ROOT);
    }

    @Nullable
    public static UUID ownerId(Entity entity) {
        CompoundTag tag = entity.getPersistentData().getCompound(ROOT);
        return tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
    }

    /** The owner, if they are online in the same level. Goals need exactly this and nothing more. */
    @Nullable
    public static Player owner(Entity entity) {
        UUID id = ownerId(entity);
        if (id == null) return null;
        Level level = entity.level();
        Player owner = level.getPlayerByUUID(id);
        return owner != null && owner.isAlive() ? owner : null;
    }

    public static boolean isOwnedBy(Entity entity, UUID ownerId) {
        return isShadow(entity) && ownerId.equals(ownerId(entity));
    }

    /** Are these two shadows of the same owner, or a shadow and its owner? Never harm each other. */
    public static boolean areAllies(Entity a, Entity b) {
        if (a == b) return true;
        boolean aShadow = isShadow(a), bShadow = isShadow(b);
        if (aShadow && bShadow) {
            UUID ao = ownerId(a), bo = ownerId(b);
            return ao != null && ao.equals(bo);
        }
        if (aShadow) return isOwnedBy(a, b.getUUID());
        if (bShadow) return isOwnedBy(b, a.getUUID());
        return false;
    }

    /** The army roster entry this shadow belongs to. Null for Umbra clones. */
    @Nullable
    public static UUID rosterId(Entity entity) {
        CompoundTag tag = entity.getPersistentData().getCompound(ROOT);
        return tag.hasUUID(ROSTER_ID) ? tag.getUUID(ROSTER_ID) : null;
    }

    public static int slots(Entity entity) {
        return entity.getPersistentData().getCompound(ROOT).getInt(SLOTS);
    }

    public static boolean isTemporary(Entity entity) {
        return entity.getPersistentData().getCompound(ROOT).getBoolean(TEMPORARY);
    }

    public static long expiresAt(Entity entity) {
        return entity.getPersistentData().getCompound(ROOT).getLong(EXPIRES);
    }

    /** Mark a freshly made entity as a permanent army shadow. */
    public static void markArmy(LivingEntity entity, UUID ownerId, UUID rosterId, int slots) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(OWNER, ownerId);
        tag.putUUID(ROSTER_ID, rosterId);
        tag.putInt(SLOTS, slots);
        tag.putBoolean(TEMPORARY, false);
        entity.getPersistentData().put(ROOT, tag);
    }

    /** Mark a freshly made entity as an Umbra clone that dissolves at {@code expiresAt}. */
    public static void markTemporary(LivingEntity entity, UUID ownerId, long expiresAt) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(OWNER, ownerId);
        tag.putBoolean(TEMPORARY, true);
        tag.putLong(EXPIRES, expiresAt);
        entity.getPersistentData().put(ROOT, tag);
    }

    /** Persistent-data key, so the snapshot code can strip a victim's own mark before copying. */
    static String rootKey() { return ROOT; }
}

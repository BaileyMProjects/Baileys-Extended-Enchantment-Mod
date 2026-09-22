package com.bailey.enchantments.shadow;

import java.util.Set;

import javax.annotation.Nullable;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import com.bailey.enchantments.entity.ModEntities;
import net.minecraft.world.phys.Vec3;

/**
 * Makes bodies. A shadow is a fresh entity of the victim's type, loaded from a
 * <b>snapshot</b> of the victim's NBT with the transient parts stripped.
 *
 * <h2>Why NBT and not {@code EntityType.create}</h2>
 * A bare new entity is the default variant: a normal-size slime, an unemployed villager, a
 * white sheep, a bareheaded zombie. The snapshot keeps the size, profession, colour, gear, baby
 * flag and every modded variant field, because it keeps everything the game itself would have
 * saved — without knowing what any of it means. That is the only way to be faithful to a mob
 * from a mod this code has never heard of.
 *
 * <p>The same snapshot is what the army stores between deployments, so an Umbra clone and a
 * Requiem soldier are made by exactly the same code.
 */
public final class ShadowSpawner {
    private ShadowSpawner() {}

    /**
     * Keys that describe a moment rather than a creature. Position, motion and health are
     * reset; conversions, anger, sleeping, leashes and riders are dropped; our own persistent
     * data is dropped because the shadow gets fresh marks.
     */
    private static final Set<String> STRIP = Set.of(
            "UUID", "Pos", "Motion", "Rotation", "OnGround", "FallDistance", "Fire", "Air",
            "PortalCooldown", "Dimension", "TicksFrozen", "HasVisualFire",
            "Health", "DeathTime", "HurtTime", "HurtByTimestamp", "ActiveEffects", "Brain",
            "Passengers", "Leash", "Sleeping", "SleepingX", "SleepingY", "SleepingZ",
            "AngerTime", "AngryAt", "InWaterTime", "DrownedConversionTime",
            "StrayConversionTime", "ConversionTime", "ConversionPlayer", "IsImmuneToZombification",
            "TimeInOverworld", "Tags", "ForgeData", "ForgeCaps", "NoAI",
            "PersistenceRequired", "CanPickUpLoot", "LastDeathLocation", "Invulnerable",
            "Silent", "Glowing", "CustomNameVisible");

    /** A storable description of this creature: its type id plus everything durable about it. */
    public static CompoundTag snapshot(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) return ShadowPlayer.snapshotOf(player);
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        for (String key : STRIP) tag.remove(key);
        // The dragon's body is ours, not vanilla's: hers cannot be told where to fly.
        EntityType<?> bodyType = entity instanceof EnderDragon ? ModEntities.SHADOW_DRAGON.get() : entity.getType();
        tag.putString("id", EntityType.getKey(bodyType).toString());
        return tag;
    }

    /**
     * A new, un-added body built from a snapshot at a position, full health, no target. The
     * caller marks it, installs the AI and adds it to the level.
     */
    @Nullable
    public static Mob materialise(ServerLevel level, CompoundTag snapshot, Vec3 pos) {
        EntityType<?> type = EntityType.by(snapshot).orElse(null);
        if (type == null) {
            BaileysEnchantments.LOGGER.warn("Shadow snapshot names an unknown entity type: {}", snapshot.getString("id"));
            return null;
        }
        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) {
            if (created != null) created.discard();
            return null;
        }

        CompoundTag data = snapshot.copy();
        data.remove("id");
        try {
            mob.load(data);
        } catch (RuntimeException e) {
            // A modded entity that cannot be rebuilt from its own save data is still a body;
            // it just loses its variant. Better a plain shadow than none.
            BaileysEnchantments.LOGGER.warn("Shadow of {} could not load its snapshot: {}", type, e.toString());
        }

        mob.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360.0F, 0.0F);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.setHealth(mob.getMaxHealth());
        mob.setTarget(null);
        return mob;
    }

    /** Dark particles and the soul-escape sound where a shadow appears or dissolves. */
    public static void puff(ServerLevel level, Entity at, int count) {
        double y = at.getY() + at.getBbHeight() * 0.5;
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.getX(), y, at.getZ(), count,
                at.getBbWidth() * 0.5, at.getBbHeight() * 0.4, at.getBbWidth() * 0.5, 0.03);
        level.sendParticles(ParticleTypes.SMOKE, at.getX(), y, at.getZ(), count / 2,
                at.getBbWidth() * 0.4, at.getBbHeight() * 0.3, at.getBbWidth() * 0.4, 0.01);
        level.playSound(null, at.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.0F, 0.6F);
    }
}

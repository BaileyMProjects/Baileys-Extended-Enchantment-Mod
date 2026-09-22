package com.bailey.enchantments.shadow;

import java.util.UUID;

import javax.annotation.Nullable;

import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * What a kill leaves behind for a Requiem holder: a lingering, clickable shadow at the death
 * spot. Sneak + right-click it with the Requiem weapon to say <i>Arise</i>.
 *
 * <p>It has no model. Its presence is the particles the server streams from it, which thin out
 * as ritual attempts fail — so a remnant on its last chance visibly looks like one. It cannot be
 * hurt, pushed or collided with; it is a place, not a creature.
 *
 * <p>The lifetime clock pauses while a ritual runs on it, so a remnant can never fade out from
 * under a rite in progress.
 */
public class ShadowRemnant extends Entity {

    private CompoundTag snapshot = new CompoundTag();
    @Nullable private UUID killer;
    private int slots = 1;
    private int attemptsLeft = ShadowBalance.RITUAL_ATTEMPTS;
    private int life = ShadowBalance.REMNANT_TICKS;
    private boolean ritualRunning;

    public ShadowRemnant(EntityType<? extends ShadowRemnant> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    /** Set up a fresh remnant before it is added to the level. */
    public void bind(CompoundTag snapshot, UUID killer, int slots) {
        this.snapshot = snapshot;
        this.killer = killer;
        this.slots = slots;
    }

    // ---- state ------------------------------------------------------------------------------

    public CompoundTag snapshot() { return snapshot; }
    public int slots() { return slots; }
    public int attemptsLeft() { return attemptsLeft; }
    @Nullable public UUID killer() { return killer; }
    public ShadowClassifier.Tier tier() { return ShadowClassifier.Tier.fromSlots(slots); }

    public void setRitualRunning(boolean running) {
        this.ritualRunning = running;
    }

    /** A failed attempt. Returns true if the remnant has no attempts left and has faded. */
    public boolean fail() {
        attemptsLeft--;
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.6, getZ(), 30, 0.4, 0.4, 0.4, 0.08);
            level.playSound(null, blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.8F, 0.5F);
        }
        if (attemptsLeft <= 0) {
            fade();
            return true;
        }
        return false;
    }

    public void fade() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.6, getZ(), 12, 0.3, 0.4, 0.3, 0.03);
            level.playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 0.8F, 0.4F);
        }
        discard();
    }

    // ---- ticking -----------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;

        if (!ritualRunning && --life <= 0) {
            fade();
            return;
        }

        if (tickCount % 3 == 0) {
            // Denser for stronger shadows, thinner with every failed attempt.
            int base = 1 + tier().ordinal();
            int count = Math.max(1, base * attemptsLeft / ShadowBalance.RITUAL_ATTEMPTS);
            level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 0.2, getZ(), count, 0.3, 0.2, 0.3, 0.02);
            if (tickCount % 6 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 1, 0.3, 0.1, 0.3, 0.0);
            }
        }
    }

    // ---- interaction -------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!Ultimates.hasUltimate(player.getItemInHand(hand), ModEnchantments.REQUIEM.get())) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.PASS;

        if (killer != null && !killer.equals(player.getUUID())) {
            server.displayClientMessage(Component.translatable("baileys_enchantments.requiem.not_yours")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        AriseRitual.attempt(server, this);
        return InteractionResult.CONSUME;
    }

    // ---- a place, not a creature -------------------------------------------------------------

    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        snapshot = tag.getCompound("Snapshot");
        killer = tag.hasUUID("Killer") ? tag.getUUID("Killer") : null;
        slots = tag.getInt("Slots");
        attemptsLeft = tag.getInt("Attempts");
        life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Snapshot", snapshot);
        if (killer != null) tag.putUUID("Killer", killer);
        tag.putInt("Slots", slots);
        tag.putInt("Attempts", attemptsLeft);
        tag.putInt("Life", life);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

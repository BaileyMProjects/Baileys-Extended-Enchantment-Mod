package com.bailey.enchantments.shadow;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.bailey.enchantments.entity.ModEntities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The shadow of a player: a humanoid body wearing the gear they died in, with their skin.
 *
 * <p>Bailey, 2026-09-22: <i>"a player summoned into the army should have the armor and weapon
 * of the player when they died and should carry the buffs it gives."</i>
 *
 * <p>A player is not a {@code Mob}, so it cannot be driven by goals — this is the body the
 * classifier's {@code PLAYER} role points at. It is a plain {@link PathfinderMob} with no goals
 * of its own; {@link ShadowAi} installs the bodyguard set like on any other shadow.
 *
 * <p>The gear's vanilla buffs come free: armour, toughness, Protection, Sharpness and the rest
 * all apply to any living entity wearing them. Our own enchantments apply only where their
 * handlers accept a non-player — "whatever carries over, carries over".
 */
public class ShadowPlayer extends PathfinderMob {

    private static final EntityDataAccessor<String> PROFILE_NAME =
            SynchedEntityData.defineId(ShadowPlayer.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> PROFILE_ID =
            SynchedEntityData.defineId(ShadowPlayer.class, EntityDataSerializers.OPTIONAL_UUID);

    public ShadowPlayer(EntityType<? extends ShadowPlayer> type, Level level) {
        super(type, level);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 0.0);
    }

    // ---- the snapshot: what a dead player leaves for Arise --------------------------------

    /**
     * Built from the dying player <b>before</b> their inventory drops — {@code LivingDeathEvent}
     * fires ahead of the drops, so the six equipment slots are still there to copy. The real
     * items drop as normal; these copies are unlootable because shadows drop nothing.
     */
    public static CompoundTag snapshotOf(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", EntityType.getKey(ModEntities.SHADOW_PLAYER.get()).toString());
        tag.putString("ProfileName", player.getGameProfile().getName());
        tag.putUUID("ProfileId", player.getUUID());

        // Mob's own equipment format, so plain Mob.load restores it.
        ListTag armor = new ListTag();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            armor.add(saved(player.getItemBySlot(slot)));
        }
        ListTag hands = new ListTag();
        hands.add(saved(player.getItemBySlot(EquipmentSlot.MAINHAND)));
        hands.add(saved(player.getItemBySlot(EquipmentSlot.OFFHAND)));
        tag.put("ArmorItems", armor);
        tag.put("HandItems", hands);

        // Base health only: the gear re-applies its own modifiers when worn.
        tag.putDouble("BaseHealth", player.getAttributeBaseValue(Attributes.MAX_HEALTH));
        return tag;
    }

    private static CompoundTag saved(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        if (!stack.isEmpty()) stack.save(tag);
        return tag;
    }

    // ---- identity ------------------------------------------------------------------------

    public String profileName() {
        return entityData.get(PROFILE_NAME);
    }

    /** Client-side only: give a roster preview the right skin. */
    public void setProfileForPreview(String name) {
        entityData.set(PROFILE_NAME, name);
    }

    @Nullable
    public UUID profileId() {
        return entityData.get(PROFILE_ID).orElse(null);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PROFILE_NAME, "");
        entityData.define(PROFILE_ID, Optional.empty());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PROFILE_NAME, tag.getString("ProfileName"));
        entityData.set(PROFILE_ID, tag.hasUUID("ProfileId") ? Optional.of(tag.getUUID("ProfileId")) : Optional.empty());
        if (tag.contains("BaseHealth")) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(tag.getDouble("BaseHealth"));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ProfileName", profileName());
        UUID id = profileId();
        if (id != null) tag.putUUID("ProfileId", id);
        tag.putDouble("BaseHealth", getAttributeBaseValue(Attributes.MAX_HEALTH));
    }

    // ---- body ---------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        // None. ShadowAi installs the bodyguard set; a player shadow has no life of its own.
    }

    @Override
    protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose, net.minecraft.world.entity.EntityDimensions size) {
        return 1.62F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }
}

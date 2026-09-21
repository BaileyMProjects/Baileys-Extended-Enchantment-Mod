package com.bailey.enchantments.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.DeepDark;
import com.bailey.enchantments.util.DeepDarkBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mirage and its Secret, Dissonance — the evasion armour line.
 *
 * <p>Designed by Bailey 2026-09-22 (Open decisions #31).
 *
 * <ul>
 *   <li><b>Mirage</b> — movement speed, attack speed and a chance to ignore a hit outright, all
 *       scaling with how many pieces carry it.
 *   <li><b>Dissonance</b> — everything Mirage does, plus a toggleable mode: sharper evasion,
 *       <b>+50% damage taken</b>, extended block reach, and a spammable <b>flash step</b>.
 * </ul>
 *
 * <h2>⚠️ "Corruption spreads" — the mode needs ONE Dissonance piece, not four</h2>
 * A full corrupted set plus <b>any</b> Dissonance piece unlocks the mode. Requiring all four to
 * be won separately would have cost ~80 Wardens against ~15 — and spreading through adjacent
 * corrupted material is what sculk does anyway, so the discount explains itself.
 *
 * <h2>⚠️ Why everything scales per piece</h2>
 * A flat per-piece evade chance rolled four times converges on immunity. Every passive here is
 * multiplied by the piece count and the total is capped, so no future buff can quietly produce
 * an unkillable player.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class MirageEvents {

    private static final UUID SPEED_UUID = UUID.fromString("c1e9a7d2-3f84-4b66-9a15-2d7c8e0b4f31");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("5b2d6f10-8c47-4e93-a0b8-6f1d3a9c72e4");
    private static final UUID REACH_UUID = UUID.fromString("9a4f2c81-7d36-42b5-8e0c-1b5a7f3d96c8");

    /** Who currently has the mode on. Server-side only, and deliberately not persisted. */
    private static final Map<UUID, Boolean> MODE_ON = new HashMap<>();

    /** Game time each player may next toggle, and next teleport. */
    private static final Map<UUID, Long> TOGGLE_READY = new HashMap<>();
    private static final Map<UUID, Long> TELEPORT_READY = new HashMap<>();

    /** Last seen main-hand item, so a swap can start the teleport lockout. */
    private static final Map<UUID, Item> LAST_HELD = new HashMap<>();

    private MirageEvents() {}

    /** Tiny holder so a null main hand and an empty one are distinguishable. */
    private record Item(String id) {}

    // ---- what the armour is worth ----------------------------------------------------

    /** Armour pieces carrying Mirage OR Dissonance. A Secret keeps its Ultimate's passives. */
    private static int piecesWorn(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.MIRAGE.get(), stack) > 0
                    || EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.DISSONANCE.get(), stack) > 0) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasDissonancePiece(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.DISSONANCE.get(), entity.getItemBySlot(slot)) > 0) return true;
        }
        return false;
    }

    /**
     * Can this player use the Dissonance mode at all?
     *
     * ⚠️ Both halves are required. Without a full corrupted set the Secret behaves exactly as
     * Mirage — Bailey: "until then it just does what the ultimate does". That is what makes a
     * single Secret piece never a downgrade.
     */
    private static boolean canUseMode(Player player) {
        return hasDissonancePiece(player) && DeepDark.hasFullCorruptedSet(player);
    }

    private static boolean modeOn(Player player) {
        return canUseMode(player) && MODE_ON.getOrDefault(player.getUUID(), false);
    }

    // ---- passives --------------------------------------------------------------------

    /**
     * ⚠️ Transient modifiers, exactly as the corrupted set's speed bonus is. A permanent
     * modifier serialises into player NBT, so taking the armour off during a crash would weld
     * the bonus on with no way to shed it.
     */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        Player player = event.player;
        int pieces = piecesWorn(player);

        applyModifier(player, Attributes.MOVEMENT_SPEED, SPEED_UUID, "Mirage speed",
                pieces * DeepDarkBalance.MIRAGE_SPEED_PER_PIECE,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        applyModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_UUID, "Mirage attack speed",
                pieces * DeepDarkBalance.MIRAGE_ATTACK_SPEED_PER_PIECE,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        // Reach only while the mode is on — it is what makes the flash step reach further.
        applyModifier(player, ForgeMod.BLOCK_REACH.get(), REACH_UUID, "Dissonance reach",
                modeOn(player) ? DeepDarkBalance.DISSONANCE_REACH_BONUS : 0,
                AttributeModifier.Operation.ADDITION);

        // Losing the set drops the mode, so it can never linger on a player who took the
        // armour off.
        if (!canUseMode(player)) MODE_ON.remove(player.getUUID());

        trackHandSwap(player);
    }

    private static void applyModifier(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                      UUID id, String name, double amount,
                                      AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        AttributeModifier existing = instance.getModifier(id);
        if (amount <= 0) {
            if (existing != null) instance.removeModifier(id);
            return;
        }
        if (existing != null) {
            if (existing.getAmount() == amount) return;
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    /**
     * ⚠️ THE ONLY REAL COST OF THE MODE. Bailey, 2026-09-22: a weapon swap "just gives a 1s
     * cooldown before you can teleport again". Without it the loop is: weapon out, hit, swap to
     * empty hand, flash away, swap back — untouchable, and "+50% damage taken" never bites
     * because nothing ever lands.
     */
    private static void trackHandSwap(Player player) {
        ItemStack held = player.getMainHandItem();
        Item now = new Item(held.isEmpty() ? "" : held.getItem().toString());
        Item before = LAST_HELD.put(player.getUUID(), now);

        if (before != null && !before.equals(now)) {
            TELEPORT_READY.put(player.getUUID(),
                    player.level().getGameTime() + DeepDarkBalance.DISSONANCE_SWAP_LOCKOUT);
        }
    }

    // ---- evasion ---------------------------------------------------------------------

    /**
     * The chance to ignore a hit outright.
     *
     * ⚠️ Cancelled at {@link LivingAttackEvent}, not reduced at LivingHurtEvent, because Bailey
     * asked to "evade a hit and take no damage" — armour, resistance and enchantments never get
     * to touch it, and no damage number appears at all.
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        int pieces = piecesWorn(player);
        if (pieces == 0) return;

        // ⚠️ Never dodge the void. Evading /kill or falling out of the world would strand a
        // player somewhere they cannot leave.
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        float chance = pieces * DeepDarkBalance.MIRAGE_EVADE_PER_PIECE;
        if (modeOn(player)) chance += DeepDarkBalance.DISSONANCE_EVADE_BONUS;
        chance = Math.min(chance, DeepDarkBalance.EVADE_CAP);

        if (player.getRandom().nextFloat() >= chance) return;

        event.setCanceled(true);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(),
                    10, 0.3, 0.4, 0.3, 0.02);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.5F, 1.8F);
        }
    }

    /** The mode's drawback: everything that does land hits harder. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!modeOn(player)) return;
        event.setAmount(event.getAmount() * DeepDarkBalance.DISSONANCE_DAMAGE_TAKEN);
    }

    // ---- the mode, and the flash step ------------------------------------------------

    /**
     * Sneak + right-click a block toggles the mode. A plain right-click flash-steps.
     *
     * <p>⚠️ Both require an <b>empty main hand</b>, and both are driven from
     * {@code RightClickBlock} because that is the only one of these events that fires on the
     * SERVER. {@code RightClickEmpty} — clicking air — is client-only and would have needed a
     * packet; Bailey's design says "the block you right click on", which sidesteps it.
     *
     * <p>⚠️ Sneaking with an empty hand suppresses vanilla block interaction, so the toggle
     * cannot steal a chest-opening or a block placement.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getMainHandItem().isEmpty()) return;
        if (!canUseMode(player)) return;

        long now = player.level().getGameTime();

        if (player.isShiftKeyDown()) {
            if (now < TOGGLE_READY.getOrDefault(player.getUUID(), 0L)) return;
            TOGGLE_READY.put(player.getUUID(), now + DeepDarkBalance.DISSONANCE_TOGGLE_COOLDOWN);
            toggle(player);
            event.setCanceled(true);
            return;
        }

        if (!modeOn(player)) return;
        if (now < TELEPORT_READY.getOrDefault(player.getUUID(), 0L)) return;

        flashStep(player, event.getPos());
        event.setCanceled(true);
    }

    private static void toggle(ServerPlayer player) {
        boolean on = !MODE_ON.getOrDefault(player.getUUID(), false);
        MODE_ON.put(player.getUUID(), on);

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(),
                on ? SoundEvents.SCULK_SHRIEKER_SHRIEK : SoundEvents.SCULK_CLICKING_STOP,
                SoundSource.PLAYERS, 0.8F, on ? 1.6F : 0.9F);
        level.sendParticles(on ? ParticleTypes.SCULK_SOUL : ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.6, 0.4, 0.03);
    }

    /**
     * The flash step: appear on top of the block that was clicked.
     *
     * <p>⚠️ Landing spot is checked for headroom before the teleport, or a spammable ability
     * becomes a spammable way to suffocate inside terrain.
     *
     * <p>⚠️ Fall distance is reset. Without it, flashing upward and then dropping deals the fall
     * damage of the whole climb — which, at +50% damage taken, is lethal.
     */
    private static void flashStep(ServerPlayer player, BlockPos clicked) {
        Level level = player.level();
        BlockPos target = clicked.above();

        BlockState feet = level.getBlockState(target);
        BlockState head = level.getBlockState(target.above());
        if (!feet.getCollisionShape(level, target).isEmpty()
                || !head.getCollisionShape(level, target.above()).isEmpty()) {
            return;   // no room -- silently refuse rather than bury the player
        }

        ServerLevel serverLevel = player.serverLevel();
        double fromX = player.getX(), fromY = player.getY(), fromZ = player.getZ();

        player.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        player.fallDistance = 0.0F;

        // A trail at both ends, so an observer can read where somebody went.
        serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, fromX, fromY + 1.0, fromZ,
                18, 0.25, 0.5, 0.25, 0.02);
        serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.25, 0.5, 0.25, 0.02);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.6F, 1.7F);
    }
}

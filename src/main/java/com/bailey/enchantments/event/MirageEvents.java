package com.bailey.enchantments.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.network.DissonanceModePayload;
import com.bailey.enchantments.network.ModNetwork;
import com.bailey.enchantments.util.DeepDark;
import com.bailey.enchantments.util.DeepDarkBalance;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * <h2>⚠️ The mode needs ALL FOUR pieces, and it is a TOGGLE</h2>
 * "Corruption spreads" was built and scratched the same day. Four separate wins it is.
 *
 * <p><b>Sneak + right-click switches the mode on; sneak + right-click switches it off.</b> In
 * between you need hold nothing — walk, fight and flash freely. Bailey, 2026-09-22, after
 * playing it: <i>"make the dissonance so you just shift to toggle it on and off but you dont
 * have to stay shifted to use it."</i>
 *
 * <p>🔴 <b>It used to be HELD by sneaking, and that was unplayable.</b> Worth keeping written
 * down: the mode was entered with a sneak-CLICK, and a human releases shift a tick later, which
 * read as leaving voluntarily and charged the 30-second penalty. Switching it on switched it
 * off, silently, every time. Do not reintroduce a hold-to-keep state entered by a click.
 *
 * <p>The 30-second lockout survives, for the two ways of leaving that are still choices:
 * switching it off yourself, and walking out of the zone.
 *
 * <h2>🔴 The flash step is fenced, because it was a travel tool</h2>
 * <i>"I found out in less than a minute i can travel over 1000 blocks using the teleport so this
 * makes it more combat useful."</i> The zone is a 100x100 square <b>anchored where the mode was
 * entered</b>. It never re-centres -- a following zone would restore exactly the behaviour it
 * exists to prevent. Swapping weapons keeps the mode and the zone, and costs only the 1s
 * teleport lockout.
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

    /** Where each active mode was anchored. ⚠️ Set once on entry and never updated. */
    private static final Map<UUID, BlockPos> ZONE_ANCHOR = new HashMap<>();

    /** Game time each mode was entered, for the grace window below. */
    private static final Map<UUID, Long> MODE_SINCE = new HashMap<>();

    /**
     * The shortest the mode can be held before a sneak-click may switch it off.
     *
     * ⚠️ Half a second, so the shift still held from the click that turned it ON cannot turn
     * it straight back off. The toggle is driven by a click, and a click outlasts itself.
     */
    private static final long MIN_HOLD = 10L;

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

    private static int dissonancePieces(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.DISSONANCE.get(), entity.getItemBySlot(slot)) > 0) count++;
        }
        return count;
    }

    /**
     * Can this player use the Dissonance mode at all?
     *
     * ⚠️ ALL FOUR PIECES MUST CARRY DISSONANCE. "Corruption spreads" -- one winning piece
     * unlocking the whole set -- was built on 2026-09-22 and Bailey scratched it the same day:
     * "it didnt spread on piece to piece but I honestly think thats fine and instead just keep
     * it to needing to get a full set piece by piece."
     *
     * ⚠️ That restores the full cost: four separate wins, roughly 8 Ultimates and ~80 Wardens.
     * He has seen that number and chose it. Do not quietly discount it again.
     *
     * Below four pieces the Secret behaves exactly as Mirage, so no progress is ever wasted.
     */
    private static boolean canUseMode(Player player) {
        return dissonancePieces(player) == 4;
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

        enforceMode(player);
        trackHandSwap(player);
    }

    /**
     * The three ways the mode ends, checked every tick.
     *
     * ⚠️ Two of them carry a 30-second lockout, and that lockout is the real fence. A zone is
     * only a fence if leaving it is expensive; otherwise a player steps out, re-enters, and has
     * a fresh zone 100 blocks further on — which is exactly the 1000-blocks-a-minute behaviour
     * this whole mechanism exists to stop.
     */
    private static void enforceMode(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!MODE_ON.getOrDefault(player.getUUID(), false)) return;

        // Losing the set carries NO penalty -- the player did not choose it by moving, and
        // armour can break mid-fight.
        if (!canUseMode(player)) {
            endMode(serverPlayer, false);
            return;
        }

        // ✅ SNEAK NO LONGER HOLDS THE MODE. Bailey, 2026-09-22, after playing it: "make the
        // dissonance so you just shift to toggle it on and off but you dont have to stay
        // shifted to use it."
        //
        // 🔴 The old "held by sneaking" rule was unplayable for a reason worth keeping written
        // down: the mode was ENTERED with a sneak-CLICK, and a human releases shift a tick
        // later — which read as leaving voluntarily and charged the 30-second penalty. Switching
        // it on switched it off. A grace window patched that; making it a toggle removes the
        // whole class of problem instead.
        //
        // What still ends it: walking out of the zone, and losing the set. Both above.

        BlockPos anchor = ZONE_ANCHOR.get(player.getUUID());
        if (anchor != null && outsideZone(player, anchor)) {
            endMode(serverPlayer, true);
        }
    }


    /** Zone test against an arbitrary point, used for teleport targets as well as the player. */
    private static boolean outsideZone(BlockPos anchor, BlockPos point) {
        int half = DeepDarkBalance.DISSONANCE_ZONE_HALF;
        return Math.abs(point.getX() - anchor.getX()) > half
                || Math.abs(point.getZ() - anchor.getZ()) > half;
    }

    /** A flat refusal the player can see and hear, so a blocked flash is never mistaken for lag. */
    private static void denied(ServerPlayer player, BlockPos target) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CLICKING_STOP,
                SoundSource.PLAYERS, 0.6F, 1.9F);
        level.sendParticles(ParticleTypes.SMOKE,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                8, 0.2, 0.2, 0.2, 0.01);
    }
    private static boolean outsideZone(Player player, BlockPos anchor) {
        int half = DeepDarkBalance.DISSONANCE_ZONE_HALF;
        return Math.abs(player.getX() - anchor.getX()) > half
                || Math.abs(player.getZ() - anchor.getZ()) > half;
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
     * Sneak + right-click a block TOGGLES the mode, on or off. A plain right-click flash-steps.
     *
     * <p>✅ Bailey, 2026-09-22, after playing it: <i>"make the dissonance so you just shift to
     * toggle it on and off but you dont have to stay shifted to use it."</i> Sneak is now the
     * switch, not the grip.
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

        // 🐛 FOUND IN PLAY 2026-09-22. Bailey: "dissonances ability just was not working for
        // some reason im not sure if it was binded to something else." It was not a binding —
        // it was this gate, failing in total silence. Three pieces of Dissonance and an empty
        // hand looks exactly like a broken enchantment. Now it says which of the two is wrong.
        if (!canUseMode(player)) {
            if (player.isShiftKeyDown() && dissonancePieces(player) > 0) {
                player.displayClientMessage(Component.translatable(
                        "baileys_enchantments.dissonance.needs_set", dissonancePieces(player))
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }

        long now = player.level().getGameTime();

        // ✅ A TOGGLE. Sneak-click switches it on, sneak-click switches it off, and in between
        // you are free to stand up, walk, fight and flash without holding anything down.
        if (modeOn(player)) {
            if (player.isShiftKeyDown()) {
                // ⚠️ Not within MIN_HOLD of entering. A shift still held from the click that
                // turned it on would otherwise turn it straight back off — the same shape of
                // bug as the old "held by sneaking" design, and worth guarding explicitly.
                if (now - MODE_SINCE.getOrDefault(player.getUUID(), 0L) >= MIN_HOLD) {
                    endMode(player, true);
                }
                event.setCanceled(true);
                return;
            }

            if (now < TELEPORT_READY.getOrDefault(player.getUUID(), 0L)) return;

            BlockPos anchor = ZONE_ANCHOR.get(player.getUUID());
            BlockPos target = event.getPos().above();
            if (anchor != null && outsideZone(anchor, target)) {
                // Refused, not punished. Clicking the wall of your own zone is a misjudged aim,
                // not an escape attempt -- the 30s penalty is for walking out of it.
                denied(player, target);
                event.setCanceled(true);
                return;
            }

            flashStep(player, event.getPos());
            event.setCanceled(true);
            return;
        }

        if (!player.isShiftKeyDown()) return;

        // ⚠️ The lockout has to be visible. A silent refusal for thirty seconds is exactly what
        // made this read as broken — see the grace window in onTick for the rest of that story.
        long ready = TOGGLE_READY.getOrDefault(player.getUUID(), 0L);
        if (now < ready) {
            player.displayClientMessage(Component.translatable(
                    "baileys_enchantments.dissonance.spent", (ready - now) / 20 + 1)
                    .withStyle(ChatFormatting.GRAY), true);
            event.setCanceled(true);
            return;
        }

        beginMode(player);
        event.setCanceled(true);
    }

    /** Enter the mode, anchoring the zone where the player is standing. */
    private static void beginMode(ServerPlayer player) {
        BlockPos anchor = player.blockPosition();
        MODE_ON.put(player.getUUID(), true);
        ZONE_ANCHOR.put(player.getUUID(), anchor);
        MODE_SINCE.put(player.getUUID(), player.level().getGameTime());

        // ⚠️ Say so. The mode's only other tell is a particle burst, which is easy to miss and
        // was part of why this read as "not working" at all.
        player.displayClientMessage(Component.translatable("baileys_enchantments.dissonance.on")
                .withStyle(ChatFormatting.DARK_AQUA), true);

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.8F, 1.6F);
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.6, 0.4, 0.03);

        ModNetwork.sendToPlayer(
                new DissonanceModePayload(true, anchor.getX(), anchor.getZ()), player);
    }

    /**
     * Leave the mode.
     *
     * @param penalise true when the player chose to leave — released sneak, or walked out of
     *                 the zone. False when the armour did it for them, which must not be
     *                 punished.
     */
    private static void endMode(ServerPlayer player, boolean penalise) {
        MODE_ON.remove(player.getUUID());
        ZONE_ANCHOR.remove(player.getUUID());
        MODE_SINCE.remove(player.getUUID());

        if (penalise) {
            TOGGLE_READY.put(player.getUUID(),
                    player.level().getGameTime() + DeepDarkBalance.DISSONANCE_EXIT_COOLDOWN);
        }

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CLICKING_STOP,
                SoundSource.PLAYERS, 0.8F, 0.9F);
        level.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.4, 0.6, 0.4, 0.02);

        player.displayClientMessage(Component.translatable("baileys_enchantments.dissonance.off")
                .withStyle(ChatFormatting.GRAY), true);

        ModNetwork.sendToPlayer(new DissonanceModePayload(false, 0, 0), player);
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

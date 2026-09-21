package com.bailey.enchantments.event;

import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.util.SecretReveal;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The ceremony that plays when a Secret is gambled for, and the rules that keep the prize safe.
 *
 * <p>Designed by Bailey 2026-09-21 — see Brain/Decisions/The Secret ritual.md. The menu closes,
 * something happens over the anvil, and the outcome announces itself:
 *
 * <ul>
 *   <li><b>Failure</b> — a short, dry sound and "Failed..." fading on and off the centre of the
 *       screen. Deliberately anti-climactic.
 *   <li><b>Success</b> — a grand, un-cheery bloom of sculk and soul particles, and the prize
 *       drops onto the anvil as a real item for the player to collect.
 * </ul>
 *
 * <p>⚠️ <b>THE ROLL IS NOT HERE AND MUST NEVER MOVE HERE.</b> It happens once, server-side, in
 * {@code AncientAnvilMenu.onTake}. Everything in this class is the <i>presentation</i> of a
 * result already decided. Putting any part of the decision into the ceremony would reopen the
 * re-roll hole the whole design exists to close.
 *
 * <p>⚠️ <b>The entire ceremony is server-side</b>, which is why there is no packet for it.
 * Particles, sounds, the title and a real spinning {@link ItemEntity} are all things a server
 * can cause directly. Only the name-reveal flag needs syncing, because that is a rendering
 * decision — see {@link SecretReveal}.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class SecretRitualEvents {

    /** Marks an item as somebody's ritual prize. Stored on the entity, so it survives a reload. */
    private static final String OWNER_KEY = "baileys_ritual_owner";

    private SecretRitualEvents() {}

    // ---- the ceremony ----------------------------------------------------------------

    /**
     * Failure. Bailey asked for "an anti-climatic short sound at the end" and for "Failed..."
     * to "fade onto and off the centre of the screen".
     *
     * The title system already does fade-in / hold / fade-out, so the fade is vanilla's, not a
     * custom renderer. Timings are in ticks: 10 in, 30 held, 20 out.
     */
    public static void playFailure(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player,
                                   UUID ownerId, ItemStack gear) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 1.0, cz = pos.getZ() + 0.5;

        // A brief gutter of smoke that simply stops. No flourish -- that is the point.
        level.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 18, 0.22, 0.12, 0.22, 0.01);
        level.sendParticles(ParticleTypes.SCULK_SOUL, cx, cy, cz, 5, 0.18, 0.10, 0.18, 0.0);

        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.6F);

        // ⚠️ The player can log out during the 1.8s build-up. The ceremony still finishes and
        // the gear still lands on the anvil — only the title, which needs a live connection,
        // is skipped. Losing the item because somebody disconnected would be unforgivable.
        if (player != null) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 30, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("container." + BaileysEnchantments.MODID + ".secret.failed")));
        }

        // ⚠️ The gear still exists on a failure — it has merely lost its Ultimate. It is put on
        // the anvil under the same protections as a win, rather than left on the cursor: the
        // menu is about to be force-closed, and vanilla would fling a carried stack at the
        // player's feet. Same place, same rules, win or lose.
        dropPrize(level, pos, ownerId, gear);
    }

    /**
     * Success. "a dramatic particle celebration that shouldnt be cheery but is grand."
     *
     * ⚠️ No title on success, deliberately. Bailey: "that wont be necessary for the success if
     * the animation is grand anyways." Words would undercut it.
     */
    public static void playSuccess(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player,
                                   UUID ownerId, ItemStack prize) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 1.0, cz = pos.getZ() + 0.5;

        // ⚠️ SOUND FIRST, AND NOT THE SONIC BOOM.
        //
        // The first version led with WARDEN_SONIC_BOOM and Bailey reported "the sound effect
        // was really delayed" — correctly. That sample opens with the Warden's charge-up, so
        // the impact lands most of a second after the visuals. END_PORTAL_SPAWN hits
        // immediately and is grand without being cheerful, which is what was asked for.
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.9F, 1.6F);
        level.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, 0.5F);

        // A pillar of light, a shockwave ring, and a slow fall — three separate gestures rather
        // than one puff. Bailey: "could be significantly grander."
        level.sendParticles(ParticleTypes.FLASH, cx, cy + 0.6, cz, 2, 0, 0, 0, 0);

        // The column: tall, dense, rising.
        for (int i = 0; i < 26; i++) {
            double h = i * 0.13;
            level.sendParticles(ParticleTypes.SCULK_SOUL, cx, cy + h, cz, 6, 0.16, 0.05, 0.16, 0.015);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, cy + h, cz, 3, 0.12, 0.04, 0.12, 0.02);
        }

        // The ring: a shockwave thrown outward along the ground, drawn as a real circle so it
        // reads as a wave rather than as random scatter.
        for (int i = 0; i < 48; i++) {
            double a = (Math.PI * 2 / 48) * i;
            double dx = Math.cos(a), dz = Math.sin(a);
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                    cx + dx * 1.15, cy - 0.35, cz + dz * 1.15, 1, dx * 0.25, 0.04, dz * 0.25, 0.22);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.SOUL,
                        cx + dx * 0.7, cy - 0.2, cz + dz * 0.7, 1, dx * 0.12, 0.1, dz * 0.12, 0.1);
            }
        }

        // The settle: a wide, slow drift so the moment has a tail instead of stopping dead.
        level.sendParticles(ParticleTypes.SCULK_SOUL, cx, cy + 1.6, cz, 50, 0.9, 0.5, 0.9, 0.005);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy + 0.5, cz, 40, 0.6, 0.7, 0.6, 0.35);

        dropPrize(level, pos, ownerId, prize);
    }

    /**
     * The prize itself, as a real item hovering over the anvil for its owner to collect.
     *
     * <p>Bailey: "item is only yours and you have to pick it up off the table, make sure it is
     * immune to destruction of course." Three separate protections, because a real entity has
     * three separate ways to disappear:
     *
     * <ul>
     *   <li><b>Damage</b> — {@code setInvulnerable(true)}. Fire, lava, explosions and cactus all
     *       route through {@code isInvulnerableTo}, so one call covers them.
     *   <li><b>Despawn</b> — {@code setUnlimitedLifetime()}. An ordinary dropped item evaporates
     *       after five minutes; a Secret that cost an Ultimate must not.
     *   <li><b>Theft</b> — the owner's UUID is written onto the entity and enforced in
     *       {@link #onPickup}. Being first to the anvil must not beat being the one who paid.
     * </ul>
     *
     * <p>⚠️ Gravity is off so it hovers where it can be seen and reached. Vanilla already spins
     * a dropped item, so the "spins above the anvil" is free — no custom renderer.
     */
    private static void dropPrize(ServerLevel level, BlockPos pos, UUID ownerId, ItemStack prize) {
        ItemEntity item = new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, prize.copy());

        item.setNoGravity(true);
        item.setDeltaMovement(0, 0, 0);
        item.setInvulnerable(true);
        item.setUnlimitedLifetime();
        item.setPickUpDelay(20);          // a beat to watch it before it can be walked into
        item.setThrower(ownerId);
        item.getPersistentData().putUUID(OWNER_KEY, ownerId);

        level.addFreshEntity(item);
    }

    // ---- the rules that protect it ---------------------------------------------------

    /**
     * Only the player who paid may collect a ritual prize.
     *
     * ⚠️ Cancelling {@link EntityItemPickupEvent} is the only hook for this — vanilla's
     * {@code ItemEntity} has no owner concept, and {@code setThrower} governs where it drifts,
     * not who may take it.
     */
    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        ItemEntity item = event.getItem();
        if (!item.getPersistentData().hasUUID(OWNER_KEY)) return;

        UUID owner = item.getPersistentData().getUUID(OWNER_KEY);
        if (!owner.equals(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * Carry the Secret-name reveal across a respawn or a dimension change.
     *
     * ⚠️ Forge builds a fresh Player object for both, so without this the reveal is lost every
     * time the player dies — and the symptom (the name goes back to scrambled) points at the
     * scramble code rather than at respawn, which is what makes it worth a comment.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        SecretReveal.copyOnClone(event.getOriginal(), event.getEntity());
    }

    /**
     * Push the flag to the client on join.
     *
     * ⚠️ Not an optimisation — a correctness requirement. The client's copy is static and
     * survives leaving a world, so joining a second world without this would inherit the first
     * world's reveal, breaking the per-world rule outright.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SecretReveal.sync(player);
        }
    }

    /** Also on a dimension change and a respawn, since those re-send very little by default. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SecretReveal.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SecretReveal.sync(player);
        }
    }

    /** Convenience for the menu: is this the player who owns that prize? */
    public static boolean isOwner(ItemEntity item, Player player) {
        return item.getPersistentData().hasUUID(OWNER_KEY)
                && item.getPersistentData().getUUID(OWNER_KEY).equals(player.getUUID());
    }
}

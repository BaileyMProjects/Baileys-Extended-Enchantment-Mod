package com.bailey.enchantments.event;

import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.util.SecretReveal;

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
    public static void playFailure(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack gear) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 1.0, cz = pos.getZ() + 0.5;

        // A brief gutter of smoke that simply stops. No flourish -- that is the point.
        level.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 18, 0.22, 0.12, 0.22, 0.01);
        level.sendParticles(ParticleTypes.SCULK_SOUL, cx, cy, cz, 5, 0.18, 0.10, 0.18, 0.0);

        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.6F);

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 30, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("container." + BaileysEnchantments.MODID + ".secret.failed")));

        // ⚠️ The gear still exists on a failure — it has merely lost its Ultimate. It is put on
        // the anvil under the same protections as a win, rather than left on the cursor: the
        // menu is about to be force-closed, and vanilla would fling a carried stack at the
        // player's feet. Same place, same rules, win or lose.
        dropPrize(level, pos, player, gear);
    }

    /**
     * Success. "a dramatic particle celebration that shouldnt be cheery but is grand."
     *
     * ⚠️ No title on success, deliberately. Bailey: "that wont be necessary for the success if
     * the animation is grand anyways." Words would undercut it.
     */
    public static void playSuccess(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack prize) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 1.0, cz = pos.getZ() + 0.5;

        // A column and a burst, not a sparkle. Sculk and soul rather than happy colours.
        level.sendParticles(ParticleTypes.SCULK_SOUL, cx, cy + 0.3, cz, 60, 0.35, 0.55, 0.35, 0.02);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, 40, 0.30, 0.45, 0.30, 0.03);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, cx, cy + 0.6, cz, 30, 0.45, 0.30, 0.45, 0.05);
        level.sendParticles(ParticleTypes.FLASH, cx, cy + 0.5, cz, 1, 0, 0, 0, 0);

        level.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.playSound(null, pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 0.55F, 1.4F);

        dropPrize(level, pos, player, prize);
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
    private static void dropPrize(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack prize) {
        ItemEntity item = new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, prize.copy());

        item.setNoGravity(true);
        item.setDeltaMovement(0, 0, 0);
        item.setInvulnerable(true);
        item.setUnlimitedLifetime();
        item.setPickUpDelay(20);          // a beat to watch it before it can be walked into
        item.setThrower(player.getUUID());
        item.getPersistentData().putUUID(OWNER_KEY, player.getUUID());

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

package com.bailey.enchantments.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The build-up before a Secret's result is revealed.
 *
 * <p>Bailey, 2026-09-21: <i>"for both the failed and success it should have a build up for
 * roughly 1.5 to 2 seconds. Some sort of repeating sound that builds up and then shows the
 * animation for the result. same build up for both but then does its own animation."</i>
 *
 * <p>⚠️ <b>THE BUILD-UP IS IDENTICAL FOR BOTH OUTCOMES, AND THAT IS LOAD-BEARING.</b> It is the
 * same design rule as the preview: nothing may hint at the result before the result. If the
 * build-up differed even slightly — one more particle, a half-tone of pitch — a player would
 * learn to read it within a dozen attempts and the 50/50 would stop being a gamble. The roll is
 * already decided when this starts; this class must not know the difference until the end.
 *
 * <p>Driven from the server tick because the Ancient Anvil has no block entity to tick. The
 * queue is tiny and short-lived, so a list scanned each tick costs nothing.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class SecretCeremony {

    /** 36 ticks = 1.8s, inside Bailey's "roughly 1.5 to 2 seconds". */
    private static final int BUILDUP_TICKS = 36;

    /** Beats start slow and tighten. Dropping below 3 ticks stops reading as separate hits. */
    private static final int BEAT_INTERVAL_START = 9;
    private static final int BEAT_INTERVAL_MIN = 3;

    private static final List<Pending> ACTIVE = new ArrayList<>();

    private SecretCeremony() {}

    private static final class Pending {
        final ServerLevel level;
        final BlockPos pos;
        final UUID playerId;
        final ItemStack prize;
        final boolean success;
        int tick;
        int nextBeat;
        int interval = BEAT_INTERVAL_START;

        Pending(ServerLevel level, BlockPos pos, UUID playerId, ItemStack prize, boolean success) {
            this.level = level;
            this.pos = pos;
            this.playerId = playerId;
            this.prize = prize;
            this.success = success;
        }
    }

    /**
     * Start the ceremony. The outcome is already decided and is simply carried until the end.
     *
     * ⚠️ The prize is <b>not</b> handed over here. It appears with the result, after the
     * build-up — handing it over early would give the outcome away before the reveal.
     */
    public static void begin(ServerLevel level, BlockPos pos, ServerPlayer player,
                             ItemStack prize, boolean success) {
        ACTIVE.add(new Pending(level, pos, player.getUUID(), prize.copy(), success));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Pending> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (advance(p)) it.remove();
        }
    }

    /** @return true when this ceremony is finished and should be dropped from the queue. */
    private static boolean advance(Pending p) {
        // The player may have logged out mid-ceremony. That must not strand the prize, so the
        // ceremony runs to completion regardless and the item still appears on the anvil —
        // only the title, which needs a connection, is skipped.
        ServerPlayer player = (ServerPlayer) p.level.getPlayerByUUID(p.playerId);

        if (p.tick >= BUILDUP_TICKS) {
            if (p.success) {
                SecretRitualEvents.playSuccess(p.level, p.pos, player, p.playerId, p.prize);
            } else {
                SecretRitualEvents.playFailure(p.level, p.pos, player, p.playerId, p.prize);
            }
            return true;
        }

        buildUp(p);
        p.tick++;
        return false;
    }

    /**
     * The tension: an accelerating heartbeat and a ring of sculk drawing inward.
     *
     * <p>The Warden's heartbeat is the right sound for this mod — it is already the noise of
     * something terrible deciding whether to notice you — and speeding it up while raising the
     * pitch is the oldest trick there is for "something is about to happen."
     */
    private static void buildUp(Pending p) {
        double cx = p.pos.getX() + 0.5, cy = p.pos.getY() + 1.0, cz = p.pos.getZ() + 0.5;
        float progress = (float) p.tick / BUILDUP_TICKS;

        if (p.tick >= p.nextBeat) {
            // Pitch climbs with progress; the gap between beats shrinks. Both together read as
            // acceleration far more clearly than either alone.
            float pitch = 0.55F + progress * 1.05F;
            p.level.playSound(null, p.pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 1.1F, pitch);
            p.level.playSound(null, p.pos, SoundEvents.SCULK_CLICKING, SoundSource.BLOCKS, 0.5F, pitch);

            p.nextBeat = p.tick + p.interval;
            if (p.interval > BEAT_INTERVAL_MIN) p.interval--;
        }

        // A ring closing in on the anvil. Radius shrinks as the ceremony tightens, so the
        // gathering is visible and not just a haze.
        double radius = 2.4 - progress * 2.0;
        int points = 3 + (int) (progress * 5);
        double spin = p.tick * 0.35;
        for (int i = 0; i < points; i++) {
            double a = spin + (Math.PI * 2 / points) * i;
            double dx = Math.cos(a) * radius, dz = Math.sin(a) * radius;
            p.level.sendParticles(ParticleTypes.SCULK_SOUL,
                    cx + dx, cy - 0.1 + progress * 0.4, cz + dz, 1, 0, 0, 0, 0);
        }

        // The anvil itself starts to smoulder as it approaches the reveal.
        if (p.tick % 3 == 0) {
            p.level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, cx, cy, cz,
                    1 + (int) (progress * 3), 0.25, 0.15, 0.25, 0.01);
        }
    }
}

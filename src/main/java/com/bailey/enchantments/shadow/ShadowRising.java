package com.bailey.enchantments.shadow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The rise-from-the-ground animation: the body is added below the surface and lifted over
 * {@link ShadowBalance#RISE_TICKS}, with particles streaming off it, then let go.
 *
 * <p>While rising the mob has no gravity, no physics and no vulnerability — it is inside the
 * ground, and the ground must neither push it out nor suffocate it. All three are restored on
 * the last tick, and restored on removal too, so an interrupted rise never leaves a permanently
 * invulnerable, floating shadow behind.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class ShadowRising {
    private ShadowRising() {}

    private static final class Rise {
        final Mob mob;
        final double fromY, toY;
        int tick;

        Rise(Mob mob, double fromY, double toY) {
            this.mob = mob;
            this.fromY = fromY;
            this.toY = toY;
        }
    }

    private static final List<Rise> RISING = new ArrayList<>();

    /** Call after the mob has been added to the level at its final position. */
    public static void begin(ServerLevel level, Mob mob) {
        double toY = mob.getY();
        double fromY = toY - mob.getBbHeight() - 0.2;
        mob.setNoGravity(true);
        mob.noPhysics = true;
        mob.setInvulnerable(true);
        mob.setPos(mob.getX(), fromY, mob.getZ());
        RISING.add(new Rise(mob, fromY, toY));

        level.playSound(null, mob.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.8F, 0.7F);
        level.playSound(null, mob.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.2F, 0.5F);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RISING.isEmpty()) return;

        Iterator<Rise> it = RISING.iterator();
        while (it.hasNext()) {
            Rise r = it.next();
            Mob mob = r.mob;
            if (mob.isRemoved() || !mob.isAlive()) { release(mob); it.remove(); continue; }

            r.tick++;
            double t = Math.min(1.0, (double) r.tick / ShadowBalance.RISE_TICKS);
            double y = r.fromY + (r.toY - r.fromY) * t;
            mob.setPos(mob.getX(), y, mob.getZ());
            mob.setDeltaMovement(0, 0, 0);

            if (mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), r.toY + 0.1, mob.getZ(),
                        3, mob.getBbWidth() * 0.5, 0.05, mob.getBbWidth() * 0.5, 0.02);
                level.sendParticles(ParticleTypes.SMOKE, mob.getX(), r.toY + 0.1, mob.getZ(),
                        2, mob.getBbWidth() * 0.4, 0.05, mob.getBbWidth() * 0.4, 0.01);
            }

            if (t >= 1.0) { release(mob); it.remove(); }
        }
    }

    private static void release(Mob mob) {
        mob.setNoGravity(false);
        mob.noPhysics = false;
        mob.setInvulnerable(false);
    }
}

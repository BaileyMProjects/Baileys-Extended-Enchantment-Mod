package com.bailey.enchantments.client;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the Dissonance mode for the player who is in it — the aura on themselves, and the wall
 * of the flash-step zone.
 *
 * <p>Bailey, 2026-09-22: <i>"theres need to be a particle indicator around the player to show if
 * they are in the mode. I also think that a particle square should be drawn 100x100 for the
 * players perspective only using the soul particles."</i>
 *
 * <p>⚠️ <b>Client-side only, and that is what makes it private.</b> Particles spawned here with
 * {@code level.addParticle} exist on this client alone — nobody else sees the zone, which is
 * what was asked for. A server-spawned particle would be visible to everyone nearby and would
 * hand opponents the boundary for free.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, value = Dist.CLIENT)
public final class DissonanceClient {

    private static boolean active;
    private static int anchorX;
    private static int anchorZ;

    /**
     * ⚠️ How much of the wall is drawn at once.
     *
     * Minecraft culls particles well before 50 blocks, so drawing the whole 400-block perimeter
     * would waste almost all of it on particles nobody can see. Only the stretch of wall near
     * the player is emitted — which also means the boundary reveals itself as it is approached,
     * rather than being a permanent cage on screen.
     */
    private static final double DRAW_RANGE = 26.0;

    /** Spacing along the wall. Tighter reads as a solid sheet and costs more than it gains. */
    private static final double STEP = 1.5;

    private DissonanceClient() {}

    public static void set(boolean on, int x, int z) {
        active = on;
        anchorX = x;
        anchorZ = z;
    }

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;

        drawAura(mc.level, player);
        drawZoneWall(mc.level, player);
    }

    /** The tell that the mode is on at all — a low sculk shimmer around the player's feet. */
    private static void drawAura(Level level, LocalPlayer player) {
        long time = level.getGameTime();
        for (int i = 0; i < 3; i++) {
            double angle = time * 0.22 + (Math.PI * 2 / 3) * i;
            double radius = 0.55;
            level.addParticle(ParticleTypes.SCULK_SOUL,
                    player.getX() + Math.cos(angle) * radius,
                    player.getY() + 0.15 + Math.sin(time * 0.12 + i) * 0.25,
                    player.getZ() + Math.sin(angle) * radius,
                    0, 0.01, 0);
        }
    }

    /**
     * The zone wall: the four edges of the square the player may flash inside.
     *
     * ⚠️ Anchored, never re-centred. The whole reason the zone exists is that the ability was a
     * travel tool — Bailey covered 1000 blocks in under a minute with it. A wall that followed
     * the player would restore exactly that.
     */
    private static void drawZoneWall(Level level, LocalPlayer player) {
        int half = com.bailey.enchantments.util.DeepDarkBalance.DISSONANCE_ZONE_HALF;
        double minX = anchorX - half, maxX = anchorX + half;
        double minZ = anchorZ - half, maxZ = anchorZ + half;

        double px = player.getX(), pz = player.getZ();
        double baseY = player.getY();

        // Two edges run along X, two along Z. Each is only drawn where it is close enough to
        // be seen at all.
        if (Math.abs(pz - minZ) < DRAW_RANGE) wallAlongX(level, minX, maxX, minZ, px, baseY);
        if (Math.abs(pz - maxZ) < DRAW_RANGE) wallAlongX(level, minX, maxX, maxZ, px, baseY);
        if (Math.abs(px - minX) < DRAW_RANGE) wallAlongZ(level, minZ, maxZ, minX, pz, baseY);
        if (Math.abs(px - maxX) < DRAW_RANGE) wallAlongZ(level, minZ, maxZ, maxX, pz, baseY);
    }

    private static void wallAlongX(Level level, double minX, double maxX, double z,
                                   double playerX, double baseY) {
        double from = Math.max(minX, playerX - DRAW_RANGE);
        double to = Math.min(maxX, playerX + DRAW_RANGE);
        for (double x = from; x <= to; x += STEP) {
            emitColumn(level, x, z, baseY);
        }
    }

    private static void wallAlongZ(Level level, double minZ, double maxZ, double x,
                                   double playerZ, double baseY) {
        double from = Math.max(minZ, playerZ - DRAW_RANGE);
        double to = Math.min(maxZ, playerZ + DRAW_RANGE);
        for (double z = from; z <= to; z += STEP) {
            emitColumn(level, x, z, baseY);
        }
    }

    /** A short vertical column, so the boundary reads as a wall rather than a painted line. */
    private static void emitColumn(Level level, double x, double z, double baseY) {
        for (int step = 0; step < 3; step++) {
            level.addParticle(ParticleTypes.SCULK_SOUL,
                    x, baseY + step * 1.4, z, 0, 0.005, 0);
        }
    }
}

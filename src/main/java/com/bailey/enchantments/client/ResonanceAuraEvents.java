package com.bailey.enchantments.client;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The black ribbon that circles a weapon carrying Resonance.
 *
 * <p>Bailey, 2026-09-21: <i>"a black aura swirling around the sword like a ribbon ... it
 * circulates around the weapon in the hand of the user ... This should always apply no matter
 * what sword as long as it has Resonance on it."</i>
 *
 * <p>⚠️ <b>Done with particles rather than a custom item renderer, and that is what makes
 * "no matter what sword" true.</b> Forge attaches a custom renderer through
 * {@code Item#initializeClient}, which is a method on the Item class — so it can only ever be
 * given to items this mod registers. Resonance goes on <i>vanilla</i> swords, and on other
 * mods' swords. Particles are attached to the holder, not the item, so they work on every one
 * of them, in first person and third, and for everybody watching.
 *
 * <p>The shape is a three-strand helix travelling out along the blade, not a cloud: each strand
 * orbits the blade axis while sliding from hilt to tip and looping back, which is what reads as
 * a ribbon rather than as smoke.
 *
 * <p>⚠️ Client-only, and cosmetic. Nothing here may influence anything — the aura is drawn from
 * what the client already knows about the held stack, so a desync shows a wrong ribbon and
 * never a wrong outcome.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, value = Dist.CLIENT)
public final class ResonanceAuraEvents {

    /** Strands in the ribbon. Three reads as a braid; one reads as a stray particle. */
    private static final int STRANDS = 3;

    /** How far the ribbon stands off the blade. */
    private static final double RADIUS = 0.22;

    /** How far along the blade the helix travels before looping. */
    private static final double BLADE_LENGTH = 0.85;

    private static final double ORBIT_SPEED = 0.30;
    private static final double TRAVEL_SPEED = 0.055;

    private ResonanceAuraEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.isPaused()) return;

        // Every player in range, not just the local one: somebody else swinging a Resonance
        // sword should be visibly carrying something wrong.
        for (Player player : level.players()) {
            ItemStack held = player.getMainHandItem();
            if (!hasResonance(held)) continue;
            spawnRibbon(level, player);
        }
    }

    private static boolean hasResonance(ItemStack stack) {
        return !stack.isEmpty()
                && EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RESONANCE.get(), stack) > 0;
    }

    private static void spawnRibbon(Level level, Player player) {
        long time = level.getGameTime();

        // The hand, approximated from the body rather than from the render matrices. Exact
        // hand tracking would need to hook the renderer, which is a great deal of machinery
        // for an effect nobody measures with a ruler.
        Vec3 look = player.getLookAngle();
        double yawRad = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad));
        Vec3 up = new Vec3(0, 1, 0);

        Vec3 hand = player.position()
                .add(0, player.getEyeHeight() * 0.78, 0)
                .add(look.scale(0.42))
                .add(right.scale(0.34));

        for (int s = 0; s < STRANDS; s++) {
            double phase = (Math.PI * 2 / STRANDS) * s;
            double angle = time * ORBIT_SPEED + phase;

            // Slide along the blade and loop, so the ribbon flows instead of just spinning.
            double travel = ((time * TRAVEL_SPEED + s / (double) STRANDS) % 1.0) * BLADE_LENGTH;

            Vec3 offset = right.scale(Math.cos(angle) * RADIUS)
                    .add(up.scale(Math.sin(angle) * RADIUS))
                    .add(look.scale(travel));

            Vec3 p = hand.add(offset);

            // ⚠️ SQUID_INK is the blackest particle vanilla has, which is the whole reason it
            // is here rather than SMOKE — Bailey asked for black, and smoke reads grey.
            level.addParticle(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 0, 0, 0);

            // One strand in three carries a sculk mote, so the ribbon still belongs to this
            // mod rather than looking like a generic dark effect.
            if (s == 0 && time % 3 == 0) {
                level.addParticle(ParticleTypes.SCULK_SOUL, p.x, p.y, p.z, 0, 0.005, 0);
            }
        }
    }
}

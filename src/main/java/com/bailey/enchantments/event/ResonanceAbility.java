package com.bailey.enchantments.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Resonance's right-click: gather everything nearby, then scatter it at execute range.
 *
 * <p>Designed by Bailey 2026-09-21: <i>"the player becomes briefly immune, all mobs and players
 * within the chunk they are stood in get dragged towards him, with a limit of 10 blocks above
 * and below the player. All mobs then get flung out in random directions and get hit down to 20%
 * of their health and slowed."</i>
 *
 * <h2>Why 20% specifically</h2>
 * Execute's third perk is an instant kill at or below <b>20% health</b>, and Bailey wants Execute
 * to stay active on a Resonance sword — <i>"which would pair perfectly with this move as it makes
 * everyone vulnerable to the executes one shot perk."</i> So this move does not merely wound: it
 * puts every target <b>exactly on the execute threshold</b>. The number is not a coincidence and
 * must not be tuned without tuning {@code ModUltimateEvents}' execute cut-off with it.
 *
 * <h2>⚠️ This applies to PLAYERS as well as mobs</h2>
 * Confirmed by Bailey, asked explicitly because of what it means: one right-click drops every
 * player in your chunk to a health where a single hit kills them. That is intended.
 *
 * <h2>⚠️ Immunity is a TIMESTAMP, never a flag</h2>
 * {@code setInvulnerable(true)} would leave a permanently invincible player behind if anything
 * interrupted the sequence — a far worse bug than the ability failing. An expiry time cannot get
 * stuck: once the clock passes it, protection ends whether or not anything else ran.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class ResonanceAbility {

    /** Two minutes, Bailey's number. The move stuns a chunk and sets it to execute range. */
    private static final int COOLDOWN_TICKS = 2400;

    /** The gather. Long enough to watch, short enough not to be a cutscene. */
    private static final int GATHER_TICKS = 30;

    /** Vertical reach either side of the caster, from the brief. */
    private static final double VERTICAL_REACH = 10.0;

    /** Health left on every victim — Execute's own threshold. */
    private static final float EXECUTE_THRESHOLD = 0.2F;

    private static final int SLOW_TICKS = 100;
    private static final int SLOW_AMPLIFIER = 1;

    /**
     * Slowness alone does not hold someone still — they can still jump and drift. Jump Boost at
     * a huge amplifier zeroes jump height, and Slowness past about level 6 zeroes ground speed.
     * Together they are the closest vanilla has to a stun.
     */
    private static final int STUN_SLOW_AMPLIFIER = 250;
    private static final int STUN_JUMP_AMPLIFIER = 128;

    private static final List<Active> RUNNING = new ArrayList<>();
    private static final Map<UUID, Long> IMMUNE_UNTIL = new HashMap<>();

    private ResonanceAbility() {}

    private static final class Active {
        final ServerLevel level;
        final UUID casterId;
        int tick;

        Active(ServerLevel level, UUID casterId) {
            this.level = level;
            this.casterId = casterId;
        }
    }

    // ---- trigger ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer caster)) return;

        ItemStack held = event.getItemStack();
        if (!(held.getItem() instanceof SwordItem)) return;
        if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RESONANCE.get(), held) <= 0) return;
        if (player.getCooldowns().isOnCooldown(held.getItem())) return;

        player.getCooldowns().addCooldown(held.getItem(), COOLDOWN_TICKS);
        begin(caster);
        event.setCanceled(true);
    }

    private static void begin(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();

        // Immunity covers the gather plus a short tail, so the caster is never dropped out of
        // protection a tick before the scatter lands.
        IMMUNE_UNTIL.put(caster.getUUID(), level.getGameTime() + GATHER_TICKS + 10L);

        // The Warden's own charge-up. It is literally the sound of a sonic boom being wound
        // up, which is exactly what this is.
        level.playSound(null, caster.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.PLAYERS, 1.3F, 0.8F);
        level.playSound(null, caster.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.8F, 1.4F);

        RUNNING.add(new Active(level, caster.getUUID()));
    }

    // ---- the move --------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!IMMUNE_UNTIL.isEmpty()) {
            long now = System.currentTimeMillis();
            // Cheap guard against the map growing forever if a level is unloaded mid-move.
            if (RUNNING.isEmpty() && now % 1000 < 50) IMMUNE_UNTIL.clear();
        }
        if (RUNNING.isEmpty()) return;

        Iterator<Active> it = RUNNING.iterator();
        while (it.hasNext()) {
            if (advance(it.next())) it.remove();
        }
    }

    private static boolean advance(Active a) {
        Player caster = a.level.getPlayerByUUID(a.casterId);
        if (caster == null || caster.isRemoved()) return true;

        List<LivingEntity> victims = victims(a.level, caster);

        if (a.tick >= GATHER_TICKS) {
            scatter(a.level, caster, victims);
            return true;
        }

        gather(a.level, caster, victims, a.tick);
        a.tick++;
        return false;
    }

    /**
     * Everything living in the caster's own chunk, within the vertical reach.
     *
     * ⚠️ Chunk-bounded on purpose, not radius-bounded — Bailey asked for "the chunk they are
     * stood in". That makes the move's reach depend on WHERE in the chunk the caster stands,
     * which is unusual but is what was specified, and it gives the ability a readable footprint
     * on the F3 grid rather than an invisible sphere.
     */
    private static List<LivingEntity> victims(ServerLevel level, Player caster) {
        ChunkPos chunk = new ChunkPos(caster.blockPosition());
        AABB box = new AABB(
                chunk.getMinBlockX(), caster.getY() - VERTICAL_REACH, chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, caster.getY() + VERTICAL_REACH, chunk.getMaxBlockZ() + 1);

        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == caster || !e.isAlive()) continue;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            out.add(e);
        }
        return out;
    }

    private static void gather(ServerLevel level, Player caster, List<LivingEntity> victims, int tick) {
        Vec3 centre = caster.position().add(0, 0.6, 0);
        float progress = (float) tick / GATHER_TICKS;

        stun(caster);

        for (LivingEntity victim : victims) {
            stun(victim);

            // Pull, accelerating as the gather tightens.
            Vec3 toCentre = centre.subtract(victim.position());
            double distance = toCentre.length();
            if (distance > 0.6) {
                Vec3 pull = toCentre.normalize().scale(0.16 + progress * 0.30);
                victim.setDeltaMovement(victim.getDeltaMovement().scale(0.55).add(pull));
                victim.hurtMarked = true;   // ⚠️ without this the client never sees the pull
            }

            // A thread of sculk running from each victim to the caster, so the drag is visible
            // rather than merely felt.
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    2, 0.1, 0.1, 0.1, 0.02);
        }

        // The caster winds up.
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                centre.x, centre.y, centre.z, 3 + (int) (progress * 6), 0.5, 0.6, 0.5, 0.02);

        if (tick % 5 == 0) {
            level.playSound(null, caster.blockPosition(), SoundEvents.SCULK_CLICKING,
                    SoundSource.PLAYERS, 0.7F, 0.7F + progress * 1.1F);
        }
    }

    private static void scatter(ServerLevel level, Player caster, List<LivingEntity> victims) {
        Vec3 centre = caster.position().add(0, 0.6, 0);

        level.playSound(null, caster.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.4F, 1.0F);
        level.playSound(null, caster.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, caster.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 0.8F, 1.6F);

        level.sendParticles(ParticleTypes.SONIC_BOOM, centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, centre.x, centre.y, centre.z, 2, 0, 0, 0, 0);

        // The blast ring, drawn as a real circle so it reads as a wave.
        for (int i = 0; i < 60; i++) {
            double angle = (Math.PI * 2 / 60) * i;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                    centre.x + dx * 1.3, centre.y - 0.4, centre.z + dz * 1.3,
                    1, dx * 0.4, 0.05, dz * 0.4, 0.35);
        }

        for (LivingEntity victim : victims) {
            // ⚠️ REDUCE ONLY, NEVER RAISE. Setting health outright would HEAL anything already
            // below the threshold, which would make the move a rescue for a dying mob.
            float target = victim.getMaxHealth() * EXECUTE_THRESHOLD;
            if (victim.getHealth() > target) victim.setHealth(target);

            // Out in a random direction, which is what was asked for -- not away from the
            // caster. The scatter is meant to be disorienting, not a tidy shockwave.
            double angle = level.random.nextDouble() * Math.PI * 2;
            double force = 1.1 + level.random.nextDouble() * 0.6;
            victim.setDeltaMovement(Math.cos(angle) * force, 0.55 + level.random.nextDouble() * 0.35,
                    Math.sin(angle) * force);
            victim.hurtMarked = true;

            clearStun(victim);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    SLOW_TICKS, SLOW_AMPLIFIER, false, true));

            level.sendParticles(ParticleTypes.SOUL,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    12, 0.25, 0.3, 0.25, 0.12);
        }

        clearStun(caster);
    }

    // ---- stun and immunity -----------------------------------------------------------

    private static void stun(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                GATHER_TICKS + 2, STUN_SLOW_AMPLIFIER, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.JUMP,
                GATHER_TICKS + 2, STUN_JUMP_AMPLIFIER, false, false));
    }

    /** ⚠️ Both must go. Leaving Jump Boost 128 behind makes the victim unable to jump, quietly. */
    private static void clearStun(LivingEntity entity) {
        entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        entity.removeEffect(MobEffects.JUMP);
    }

    /**
     * The caster cannot be hurt while gathering.
     *
     * ⚠️ Checked against the world clock rather than a boolean, so protection expires on its own
     * even if the move is interrupted, the level unloads, or the server stops mid-gather.
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Long until = IMMUNE_UNTIL.get(player.getUUID());
        if (until == null) return;

        if (player.level().getGameTime() < until) {
            event.setCanceled(true);
        } else {
            IMMUNE_UNTIL.remove(player.getUUID());
        }
    }
}

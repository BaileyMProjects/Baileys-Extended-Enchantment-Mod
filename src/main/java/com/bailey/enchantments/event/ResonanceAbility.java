package com.bailey.enchantments.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.DeepDarkBalance;
import com.bailey.enchantments.util.ItemCooldown;

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

    /**
     * Two minutes, Bailey's number. The move stuns a chunk and sets it to execute range.
     *
     * <p>⚠️ Read from {@link DeepDarkBalance} rather than held here, because the cooldown
     * overlay needs the same number to work out how much of the sweep to draw.
     */
    private static final int COOLDOWN_TICKS = DeepDarkBalance.RESONANCE_COOLDOWN;

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

    /**
     * A running cast. It has two halves, and the difference between them matters:
     *
     * <ol>
     *   <li><b>The charge</b>, {@link DeepDarkBalance#RESONANCE_CHARGE_TICKS} long. The chunk is
     *       outlined for everyone, the caster is rooted, and — ✅ Bailey, 2026-09-22 — <b>the
     *       caster is vulnerable</b>. This is the window in which the move can be answered, and
     *       in which anyone standing in the marked chunk can simply walk out of it.
     *   <li><b>The gather and scatter</b>, as before, with the caster immune.
     * </ol>
     */
    private static final class Active {
        final ServerLevel level;
        final UUID casterId;
        final ChunkPos chunk;
        int charge;
        int tick;

        Active(ServerLevel level, UUID casterId, ChunkPos chunk) {
            this.level = level;
            this.casterId = casterId;
            this.chunk = chunk;
        }

        boolean charging() {
            return charge < DeepDarkBalance.RESONANCE_CHARGE_TICKS;
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
        // ⚠️ Per-item, not per-item-type. Using the sword's own NBT means this sword is on
        // cooldown and every other netherite sword is not. -> ItemCooldown
        if (ItemCooldown.isOnCooldown(held, ItemCooldown.Abilities.RESONANCE, player)) return;
        for (Active running : RUNNING) {
            if (running.casterId.equals(caster.getUUID())) return;
        }

        ItemCooldown.set(held, ItemCooldown.Abilities.RESONANCE, player, COOLDOWN_TICKS);
        begin(caster);
        event.setCanceled(true);
    }

    private static void begin(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();

        // ⚠️ NO IMMUNITY YET. It is granted when the charge completes — the whole point of the
        // build-up is that the caster can be interrupted and punished during it.

        // The Warden's own charge-up. It is literally the sound of a sonic boom being wound
        // up, which is exactly what this is.
        level.playSound(null, caster.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.PLAYERS, 1.3F, 0.8F);
        level.playSound(null, caster.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.8F, 1.4F);

        // ⚠️ The chunk is fixed AT THE START. The victim set is recomputed from the caster's
        // current chunk each tick, so without pinning it a caster could stroll to a chunk
        // border mid-charge and catch a different area than the one everyone was shown.
        RUNNING.add(new Active(level, caster.getUUID(), new ChunkPos(caster.blockPosition())));
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
        if (caster == null || caster.isRemoved() || !caster.isAlive()) return true;

        if (a.charging()) {
            charge(a, caster);
            a.charge++;
            // The moment the charge lands, the caster becomes untouchable for the rest of it.
            if (!a.charging()) {
                IMMUNE_UNTIL.put(a.casterId, a.level.getGameTime() + GATHER_TICKS + 10L);
                a.level.playSound(null, caster.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                        SoundSource.PLAYERS, 0.7F, 1.6F);
            }
            return false;
        }

        List<LivingEntity> victims = victims(a.level, a.chunk, caster);

        if (a.tick >= GATHER_TICKS) {
            scatter(a.level, caster, victims);
            return true;
        }

        gather(a.level, caster, victims, a.tick);
        a.tick++;
        return false;
    }

    /**
     * The build-up: the caster is rooted and the chunk is outlined for <b>everyone</b>.
     *
     * <p>⚠️ Server particles, not client ones. Dissonance's zone is drawn only for its owner
     * because hiding it is the point there; here the opposite is true — Bailey wanted the move
     * <i>"evadable as it is an extremely broken ability"</i>, and you cannot evade a boundary
     * you cannot see.
     */
    private static void charge(Active a, Player caster) {
        stun(caster);

        float progress = (float) a.charge / DeepDarkBalance.RESONANCE_CHARGE_TICKS;

        // The pillar on the caster, thickening as it winds up.
        a.level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                caster.getX(), caster.getY() + 1.0, caster.getZ(),
                1 + (int) (progress * 5), 0.4, 0.8, 0.4, 0.02);

        // The wall, every other tick. Drawn at the caster's own height so it reads as a volume
        // rather than a line on the floor, and sparse enough not to be a fog bank.
        if (a.charge % 2 == 0) {
            int minX = a.chunk.getMinBlockX(), maxX = a.chunk.getMaxBlockX() + 1;
            int minZ = a.chunk.getMinBlockZ(), maxZ = a.chunk.getMaxBlockZ() + 1;
            double y = caster.getY() + 0.2;
            for (int i = 0; i < 16; i += 2) {
                edge(a.level, minX + i, y, minZ, progress);
                edge(a.level, minX + i, y, maxZ, progress);
                edge(a.level, minX, y, minZ + i, progress);
                edge(a.level, maxX, y, minZ + i, progress);
            }
        }

        if (a.charge % 10 == 0) {
            a.level.playSound(null, caster.blockPosition(), SoundEvents.SCULK_CLICKING,
                    SoundSource.PLAYERS, 1.0F, 0.6F + progress * 0.8F);
        }
    }

    /** One post of the boundary wall, rising as the charge nears its end. */
    private static void edge(ServerLevel level, double x, double y, double z, float progress) {
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 1, 0.0, 0.6 + progress * 1.4, 0.0, 0.0);
    }

    /**
     * Everything living in the caster's own chunk, within the vertical reach.
     *
     * ⚠️ Chunk-bounded on purpose, not radius-bounded — Bailey asked for "the chunk they are
     * stood in". That makes the move's reach depend on WHERE in the chunk the caster stands,
     * which is unusual but is what was specified, and it gives the ability a readable footprint
     * on the F3 grid rather than an invisible sphere.
     */
    private static List<LivingEntity> victims(ServerLevel level, ChunkPos chunk, Player caster) {
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

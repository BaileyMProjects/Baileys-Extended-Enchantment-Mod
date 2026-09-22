package com.bailey.enchantments.shadow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import com.bailey.enchantments.network.ModNetwork;
import com.bailey.enchantments.network.ShadowMarkPayload;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The rules every shadow lives under, whatever body it wears.
 *
 * <ul>
 *   <li>Its AI is (re)installed whenever it enters a level — fresh spawn or world load.
 *   <li>It drops nothing and gives no experience, ever. A raised Warden must not be a loot
 *       source.
 *   <li>It cannot hurt its owner or a sibling, and they cannot hurt it — a sweep attack
 *       through your own army must be free.
 *   <li>It never converts (zombie → drowned, piglin → zombified, villager → zombie villager):
 *       conversion makes a <b>new</b> entity and the mark would not follow it.
 *   <li>Umbra clones dissolve on time.
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class ShadowEvents {
    private ShadowEvents() {}

    /** Live Umbra clones, pruned as they die. Server-side. */
    private static final List<Mob> TEMPORARY = new ArrayList<>();

    /**
     * Every live shadow, for the soul aura.
     *
     * <p>⚠️ Server-side, sent with {@code sendParticles} so <b>everyone</b> sees it. Bailey,
     * 2026-09-22: <i>"that soul particle effect we keep using should be on them aswell. These
     * are things that everyone should see."</i> It used to be drawn per viewer on the client,
     * so a client that missed the mark packet saw an ordinary mob with no aura at all.
     */
    private static final List<Mob> AURAS = new ArrayList<>();

    // ---- lifecycle -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob) || !ShadowData.isShadow(mob)) return;

        ShadowAi.install(mob);
        if (!AURAS.contains(mob)) AURAS.add(mob);
        if (ShadowData.isTemporary(mob) && !TEMPORARY.contains(mob)) TEMPORARY.add(mob);
    }

    /**
     * The drifting soul aura, every third tick, for every shadow. Scaled by hitbox, so a dragon
     * smokes and a chicken wisps.
     */
    private static void aura() {
        if (AURAS.isEmpty()) return;
        Iterator<Mob> it = AURAS.iterator();
        while (it.hasNext()) {
            Mob mob = it.next();
            if (mob.isRemoved() || !mob.isAlive() || !ShadowData.isShadow(mob)) { it.remove(); continue; }
            if (!(mob.level() instanceof ServerLevel level)) continue;
            if (level.getGameTime() % 3 != 0) continue;

            double w = mob.getBbWidth(), h = mob.getBbHeight();
            int count = Math.max(1, (int) Math.min(6, w * h));
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    mob.getX(), mob.getY() + h * 0.5, mob.getZ(),
                    count, w * 0.35, h * 0.4, w * 0.35, 0.005);
            if (level.getGameTime() % 12 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        mob.getX(), mob.getY() + h * 0.6, mob.getZ(),
                        1, w * 0.3, h * 0.3, w * 0.3, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        aura();
        if (TEMPORARY.isEmpty()) return;

        Iterator<Mob> it = TEMPORARY.iterator();
        while (it.hasNext()) {
            Mob mob = it.next();
            if (mob.isRemoved() || !mob.isAlive()) { it.remove(); continue; }
            if (!(mob.level() instanceof ServerLevel level)) continue;
            if (level.getGameTime() >= ShadowData.expiresAt(mob)) {
                ShadowSpawner.puff(level, mob, 12);
                ShadowTeam.leave(mob);
                mob.discard();
                it.remove();
            }
        }
    }

    /** How many Umbra clones this owner has alive right now. */
    public static int temporaryCount(UUID owner) {
        int n = 0;
        for (Mob mob : TEMPORARY) {
            if (mob.isAlive() && owner.equals(ShadowData.ownerId(mob))) n++;
        }
        return n;
    }

    static void trackTemporary(Mob mob) {
        if (!TEMPORARY.contains(mob)) TEMPORARY.add(mob);
    }

    // ---- the look ---------------------------------------------------------------------------

    /** The client cannot read the mark, so each viewer is told once as the shadow comes into view. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && ShadowData.isShadow(event.getTarget())) {
            ModNetwork.sendToPlayer(new ShadowMarkPayload(event.getTarget().getId()), viewer);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (ShadowData.isShadow(event.getEntity())) ShadowTeam.leave(event.getEntity());
    }

    // ---- no loot ---------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(LivingDropsEvent event) {
        if (ShadowData.isShadow(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onXp(LivingExperienceDropEvent event) {
        if (ShadowData.isShadow(event.getEntity())) event.setCanceled(true);
    }

    // ---- no friendly fire -------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) return;
        if (!ShadowData.isShadow(victim) && !ShadowData.isShadow(attacker)) return;
        if (ShadowData.areAllies(victim, attacker)) event.setCanceled(true);
    }

    /** Defence in depth: whatever AI survived the wipe may not aim a shadow at its own side. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        if (target == null || !ShadowData.isShadow(event.getEntity())) return;
        if (ShadowData.areAllies(event.getEntity(), target)) event.setCanceled(true);
    }

    // ---- no griefing ----------------------------------------------------------------------

    /** A shadow dragon would otherwise smash through the owner's base; a shadow enderman would loot it. */
    @SubscribeEvent
    public static void onGriefing(EntityMobGriefingEvent event) {
        if (ShadowData.isShadow(event.getEntity())) event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    // ---- no darkness from your own Warden ---------------------------------------------------

    /**
     * A Warden blinds every player within 20 blocks every six seconds, straight from
     * {@code customServerAiStep} -- not a behaviour, so the brain wipe does not stop it. A shadow
     * Warden would blind its own owner. The effect is refused while a shadow Warden is the one in
     * range; a real Warden further away still gets through.
     */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect() != MobEffects.DARKNESS) return;
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        AABB near = player.getBoundingBox().inflate(21.0);
        boolean shadowWarden = !player.level().getEntitiesOfClass(Warden.class, near, ShadowData::isShadow).isEmpty();
        boolean realWarden = !player.level().getEntitiesOfClass(Warden.class, near, w -> !ShadowData.isShadow(w)).isEmpty();
        if (shadowWarden && !realWarden) event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    // ---- no conversion -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Pre event) {
        if (ShadowData.isShadow(event.getEntity())) event.setCanceled(true);
    }
}

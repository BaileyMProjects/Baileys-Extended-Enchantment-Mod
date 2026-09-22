package com.bailey.enchantments.shadow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
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

    // ---- lifecycle -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob) || !ShadowData.isShadow(mob)) return;

        ShadowAi.install(mob);
        if (ShadowData.isTemporary(mob) && !TEMPORARY.contains(mob)) TEMPORARY.add(mob);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TEMPORARY.isEmpty()) return;

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

    // ---- no conversion -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Pre event) {
        if (ShadowData.isShadow(event.getEntity())) event.setCanceled(true);
    }
}

package com.bailey.enchantments.shadow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Watches what mobs do, so the classifier can price what it cannot read.
 *
 * <h2>⚠️ EVERY mob is watched, not only raised ones</h2>
 * That ordering is the entire point. Bailey asked whether observation would only apply to
 * shadows already captured — if it did, it would be useless: a four-slot Medusa is already bound
 * by the time she demonstrates anything. She petrifies you <b>while you fight her</b>, long
 * before you stand over her corpse, so by the moment of Arise the cost is already correct and
 * the mistake never happens.
 *
 * <h2>Recosting what is already owned</h2>
 * A soldier's price is re-checked once a minute against what has since been learned, and can
 * only go <b>up</b>. Nothing in the world is published, so correcting an existing roster is
 * free — and without it the first Medusa would stay four slots for ever, which is exactly the
 * complaint.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class ShadowObserver {
    private ShadowObserver() {}

    /** Rate limit on the "that one is worth more than we thought" notice, per player. */
    private static final Map<UUID, Long> TOLD = new HashMap<>();
    private static final long TELL_INTERVAL = 200L;

    // ---- watching ----------------------------------------------------------------------------

    /**
     * ⚠️ {@code Applicable}, not {@code Added}: an effect refused by an immunity — ours included
     * — still tells us the mob tried, and a mob that tries to blind you is dangerous whether or
     * not it succeeded this time.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffect(MobEffectEvent.Applicable event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        MinecraftServer server = victim.getServer();
        if (server == null) return;

        // Only effects a MOB inflicted on something count. A splash potion the player threw is
        // not evidence about anybody.
        LivingEntity source = victim.getLastHurtByMob();
        if (!(source instanceof Mob mob) || ShadowData.isShadow(mob)) return;
        if (victim.tickCount - victim.getLastHurtByMobTimestamp() > 40) return;

        ShadowObservations.get(server).observeEffect(mob.getType(), event.getEffectInstance().getEffect());
    }

    /** What a mob actually hits for, which may be nothing like what it declares. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        MinecraftServer server = victim.getServer();
        if (server == null) return;

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Mob mob) || ShadowData.isShadow(mob)) return;

        ShadowObservations.get(server).observeDamage(mob.getType(), event.getAmount());
    }

    // ---- recosting ----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 1200 != 0) return;
        recost(server);
    }

    /**
     * Re-price every soldier against what has been learned since it was raised.
     *
     * <p>⚠️ <b>Raise only.</b> A cost that could fall would let a player bind something at its
     * full price, wait for the number to drift down, and keep the discount. Going up is the
     * correction; going down would be an exploit.
     */
    private static void recost(MinecraftServer server) {
        ShadowArmies armies = ShadowArmies.get(server);
        boolean changed = false;

        for (Map.Entry<UUID, ShadowArmy> entry : armies.byId().entrySet()) {
            for (ShadowArmy.Soldier soldier : entry.getValue().soldiers()) {
                int now = ShadowClassifier.slotsForType(server, soldier.typeId);
                if (now <= soldier.slots) continue;

                int was = soldier.slots;
                soldier.slots = now;
                changed = true;
                tell(server, entry.getValue(), soldier, was, now);
            }
        }
        if (changed) armies.markDirty();
    }

    private static void tell(MinecraftServer server, ShadowArmy army,
                             ShadowArmy.Soldier soldier, int was, int now) {
        UUID holder = army.holder();
        if (holder == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(holder);
        if (player == null) return;

        long time = server.getTickCount();
        if (time - TOLD.getOrDefault(holder, -TELL_INTERVAL) < TELL_INTERVAL) return;
        TOLD.put(holder, time);

        player.displayClientMessage(Component.translatable(
                "baileys_enchantments.requiem.recost", soldier.name, was, now)
                .withStyle(ChatFormatting.DARK_RED), false);
    }
}

package com.bailey.enchantments.shadow;

import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The necromancer's ladder: earning it, and what it gives while the army is out.
 *
 * <p>Bailey, 2026-09-22: <i>"Rewards someone who actually uses it as it feels weird to be a
 * master of necromancy the moment you get the enchantment."</i>
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class NecromancerEvents {
    private NecromancerEvents() {}

    /** Buffs granted while at least one shadow is deployed, by the level that unlocks them. */
    private record Boon(int level, MobEffect effect, int amplifier) {}

    private static final Boon[] BOONS = {
            new Boon(5, MobEffects.MOVEMENT_SPEED, 0),
            new Boon(10, MobEffects.DAMAGE_BOOST, 0),
            new Boon(20, MobEffects.DAMAGE_RESISTANCE, 0),
            new Boon(30, MobEffects.MOVEMENT_SPEED, 1),
            new Boon(40, MobEffects.REGENERATION, 0),
            new Boon(50, MobEffects.DAMAGE_BOOST, 1),
    };

    /**
     * Long enough that the refresh every second never lets it lapse, short enough that recalling
     * the army takes it away almost at once.
     *
     * <p>⚠️ Not infinite, deliberately. An infinite effect outlives a crash, a dimension change
     * or a mod conflict that stops the refresh, and the player is left permanently buffed.
     */
    private static final int BOON_TICKS = 60;

    // ---- earning ---------------------------------------------------------------------------

    /**
     * A shadow killed something, or the player did.
     *
     * <p>⚠️ Runs at LOWEST so the roster bookkeeping in {@code RequiemEvents.onDeath} — which may
     * strike a dead shadow off — has already happened.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        Entity killer = event.getSource().getEntity();
        if (killer == null) return;

        MinecraftServer server = level.getServer();
        Necromancer necro = Necromancer.get(server);

        // The player's own kill reopens the gate. Bailey: "you have to still manually kill."
        if (killer instanceof ServerPlayer player) {
            necro.noteOwnKill(player.getUUID());
            return;
        }

        if (ShadowData.isShadow(victim)) return;              // no farming your own army

        // ✅ An ASSIST counts, not only the killing blow. Bailey, 2026-09-22: a raised Medusa
        // "turns everything to stone, which not only makes u unable to level up" — a shadow
        // whose contribution is disabling things earned nothing, because something else always
        // finished them. Now any shadow that damaged the victim recently pays its master.
        Entity earner = killer;
        UUID ownerId;
        if (ShadowData.isShadow(killer)) {
            ownerId = ShadowData.ownerId(killer);
        } else {
            ShadowAssists.Assist assist = ShadowAssists.claim(victim);
            if (assist == null) return;
            ownerId = assist.owner();
            // The shadow that did the work levels too, even though something else finished it.
            Entity shadow = ShadowArmyManager.findEntity(server, assist.shadow());
            if (shadow != null) earner = shadow;
        }
        if (ownerId == null) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) return;

        boolean allowed = necro.noteShadowKill(ownerId);
        if (!allowed) {
            // Told once, on the kill that shuts the gate, and not spammed after.
            if (necro.of(ownerId).killsSinceOwn == ShadowBalance.SHADOW_KILLS_PER_OWN_KILL) {
                owner.displayClientMessage(Component.translatable("baileys_enchantments.necro.gate")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }

        int xp = Math.max(1, ShadowClassifier.slotsFor(victim) * ShadowBalance.XP_PER_SLOT / ShadowBalance.XP_DIVISOR);
        int gained = necro.award(ownerId, xp);

        // The soldier's own ladder climbs from the same kill.
        SoldierLevels.award(server, earner, xp);

        if (gained > 0) {
            int now = necro.levelOf(ownerId);
            owner.displayClientMessage(Component.translatable("baileys_enchantments.necro.level",
                    now, Necromancer.slotsForLevel(now)).withStyle(ChatFormatting.DARK_AQUA), false);
            celebrate(owner);
        }
    }

    private static void celebrate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.6, 1.0, 0.6, 0.06);
    }

    // ---- mending ------------------------------------------------------------------------------

    /**
     * Downed and wounded shadows mend in storage, once a second.
     *
     * <p>⚠️ Every army is mended, not only those of players who are online. A roster is world
     * data; a soldier that healed only while you watched it would be a worse cooldown and a
     * confusing one.
     */
    private static void mend(MinecraftServer server) {
        ShadowArmies armies = ShadowArmies.get(server);
        boolean changed = false;
        for (ShadowArmy army : armies.all()) {
            for (ShadowArmy.Soldier s : army.soldiers()) {
                if (s.deployed || s.storedHealth < 0) continue;
                float max = s.maxHealthEstimate();
                if (s.storedHealth >= max) continue;
                s.storedHealth = Math.min(max, s.storedHealth + max * ShadowBalance.DOWNED_REGEN_PER_SECOND);
                changed = true;
            }
        }
        if (changed) armies.markDirty();
    }

    // ---- the boons ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) return;

        mend(server);

        ShadowArmies armies = ShadowArmies.get(server);
        Necromancer necro = Necromancer.get(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (armies.deployedBy(player.getUUID()).isEmpty()) continue;
            int level = necro.levelOf(player.getUUID());
            for (Boon boon : BOONS) {
                if (level < boon.level()) continue;
                player.addEffect(new MobEffectInstance(boon.effect(), BOON_TICKS, boon.amplifier(), true, false, true));
            }
        }
    }
}

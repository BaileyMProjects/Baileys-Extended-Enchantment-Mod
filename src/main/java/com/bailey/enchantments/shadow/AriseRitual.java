package com.bailey.enchantments.shadow;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <i>Arise.</i> Runts and Soldiers rise the moment they are asked. Elites and Monarchs demand a
 * rite: the player is held for the cutscene, then the dice decide.
 *
 * <p>Bailey, 2026-09-22: <i>"Igris in Solo Levelling needed to be arised multiple times and
 * some actually dont arise... give a chance for it to actually fail with 3 chances. Although
 * dont make that failure permanent as it would not be fair on servers."</i>
 *
 * <p>So: {@link ShadowBalance#RITUAL_ATTEMPTS} per <b>remnant</b>, never per player. The third
 * failure fades the remnant and the player goes and kills another one. Nobody is ever locked out
 * of a Warden; luck only decides how many they kill.
 *
 * <p>⚠️ The freeze is a timestamp and a pair of effects, never a flag — same rule as Resonance.
 * If anything interrupts the rite (the remnant unloads, the player logs out) the hold expires on
 * its own and the effects run out. Nothing can leave a player permanently rooted.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class AriseRitual {
    private AriseRitual() {}

    private static final int STUN_SLOW_AMPLIFIER = 250;
    private static final int STUN_JUMP_AMPLIFIER = 128;

    private static final class Rite {
        final ServerPlayer player;
        final ShadowRemnant remnant;
        final ShadowClassifier.Tier tier;
        int ticksLeft;

        Rite(ServerPlayer player, ShadowRemnant remnant, ShadowClassifier.Tier tier, int ticks) {
            this.player = player;
            this.remnant = remnant;
            this.tier = tier;
            this.ticksLeft = ticks;
        }
    }

    private static final Map<UUID, Rite> RUNNING = new HashMap<>();
    private static final Map<UUID, Long> HELD_UNTIL = new HashMap<>();

    /** Hook for the client cutscene, installed by the network layer. */
    public interface CutsceneSender {
        void begin(ServerPlayer player, ShadowRemnant remnant, ShadowClassifier.Tier tier, int ticks);
        void end(ServerPlayer player, boolean success);
    }

    private static CutsceneSender cutscene = new CutsceneSender() {
        @Override public void begin(ServerPlayer p, ShadowRemnant r, ShadowClassifier.Tier t, int ticks) {}
        @Override public void end(ServerPlayer p, boolean success) {}
    };

    public static void setCutsceneSender(CutsceneSender sender) {
        cutscene = sender;
    }

    // ---- entry -------------------------------------------------------------------------------

    public static void attempt(ServerPlayer player, ShadowRemnant remnant) {
        if (RUNNING.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.busy")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        ShadowArmy army = ShadowArmies.get(player.server).armyOf(player.getUUID());
        int slots = remnant.slots();
        if (army.freeSlots() < slots) {
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.no_room",
                    nameOf(remnant), slots, army.freeSlots()).withStyle(ChatFormatting.RED), true);
            return;
        }

        ShadowClassifier.Tier tier = remnant.tier();
        if (!tier.needsRitual()) {
            succeed(player, remnant, tier);
            return;
        }

        int ticks = tier.cutsceneTicks();
        remnant.setRitualRunning(true);
        hold(player, ticks);
        RUNNING.put(player.getUUID(), new Rite(player, remnant, tier, ticks));
        cutscene.begin(player, remnant, tier, ticks);

        ServerLevel level = player.serverLevel();
        level.playSound(null, remnant.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    // ---- ticking ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNNING.isEmpty()) return;

        Iterator<Rite> it = RUNNING.values().iterator();
        while (it.hasNext()) {
            Rite rite = it.next();
            ServerPlayer player = rite.player;
            ShadowRemnant remnant = rite.remnant;

            if (player.isRemoved() || !player.isAlive() || remnant.isRemoved()) {
                remnant.setRitualRunning(false);
                releaseHold(player);
                cutscene.end(player, false);
                it.remove();
                continue;
            }

            // The heartbeat quickens toward the reveal.
            int elapsed = rite.tier.cutsceneTicks() - rite.ticksLeft;
            int beat = rite.ticksLeft > 30 ? 20 : 10;
            if (elapsed % beat == 0) {
                player.serverLevel().playSound(null, remnant.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.HOSTILE, 1.5F, 0.6F + 0.4F * elapsed / Math.max(1, rite.tier.cutsceneTicks()));
            }

            if (--rite.ticksLeft > 0) continue;

            it.remove();
            remnant.setRitualRunning(false);
            releaseHold(player);
            boolean success = player.getRandom().nextFloat() < rite.tier.ariseChance();
            cutscene.end(player, success);
            if (success) succeed(player, remnant, rite.tier);
            else fail(player, remnant);
        }
    }

    // ---- outcomes --------------------------------------------------------------------------------

    private static void succeed(ServerPlayer player, ShadowRemnant remnant, ShadowClassifier.Tier tier) {
        ShadowArmy.Soldier soldier = ShadowArmyManager.arise(player, remnant.snapshot(), remnant.slots(),
                remnant.position(), tier != ShadowClassifier.Tier.RUNT);
        ServerLevel level = player.serverLevel();
        if (tier == ShadowClassifier.Tier.RUNT) {
            level.playSound(null, remnant.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.0F, 0.8F);
        }
        remnant.discard();

        ShadowArmy army = ShadowArmies.get(player.server).armyOf(player.getUUID());
        player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.arise",
                soldier.name, army.usedSlots(), ShadowBalance.ARMY_CAP).withStyle(ChatFormatting.DARK_AQUA), true);
    }

    private static void fail(ServerPlayer player, ShadowRemnant remnant) {
        boolean gone = remnant.fail();
        if (gone) {
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.refuse")
                    .withStyle(ChatFormatting.DARK_RED), true);
        } else {
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.resist",
                    remnant.attemptsLeft()).withStyle(ChatFormatting.RED), true);
        }
    }

    // ---- the hold --------------------------------------------------------------------------------

    private static void hold(ServerPlayer player, int ticks) {
        HELD_UNTIL.put(player.getUUID(), player.level().getGameTime() + ticks + 5L);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks + 2, STUN_SLOW_AMPLIFIER, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks + 2, STUN_JUMP_AMPLIFIER, false, false));
    }

    /** ⚠️ Both effects must go. Leaving Jump Boost 128 behind makes the player unable to jump, quietly. */
    private static void releaseHold(ServerPlayer player) {
        HELD_UNTIL.remove(player.getUUID());
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.JUMP);
    }

    public static boolean isHeld(Player player) {
        Long until = HELD_UNTIL.get(player.getUUID());
        if (until == null) return false;
        if (player.level().getGameTime() < until) return true;
        HELD_UNTIL.remove(player.getUUID());
        return false;
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && isHeld(player)) event.setCanceled(true);
    }

    private static String nameOf(ShadowRemnant remnant) {
        return net.minecraft.world.entity.EntityType.by(remnant.snapshot())
                .map(t -> t.getDescription().getString()).orElse("shadow");
    }
}

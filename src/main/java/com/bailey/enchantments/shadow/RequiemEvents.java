package com.bailey.enchantments.shadow;

import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.entity.ModEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Requiem's hooks into the world.
 *
 * <ul>
 *   <li>A kill with Requiem in hand leaves a {@link ShadowRemnant}. Ignoring it fills nothing —
 *       a mob grinder builds no army. Only chosen kills are kept.
 *   <li>Right-click with the blade deploys its army; sneak + right-click on nothing recalls it.
 *   <li><b>The army lives in the blade</b> ({@link RequiemBlade}). It stays out while the blade
 *       is anywhere in the holder's inventory and returns into it the moment it is not:
 *       dropped, chested, or lost on death. Logout and dimension change recall it too.
 *   <li>A shadow that dies in the field is struck from the roster.
 *   <li>A body that turns up with no matching roster entry is a stray, and is removed.
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class RequiemEvents {
    private RequiemEvents() {}

    private static final int CONTROL_COOLDOWN = 20;

    /** Kept for callers that only need the enchantment check. */
    public static boolean hasRequiem(ItemStack stack) {
        return RequiemBlade.isRequiem(stack);
    }

    // ---- kills ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;

        // A deployed shadow fell.
        if (ShadowData.isShadow(victim) && ShadowData.rosterId(victim) != null) {
            ShadowArmyManager.onShadowDied(victim);
            return;
        }

        // The holder fell: the blade drops, and everything goes back into it.
        if (victim instanceof ServerPlayer player) {
            ShadowArmyManager.recallAllHeldBy(player.server, player.getUUID());
        }

        // A kill worth remembering.
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (killer == victim) return;                             // no raising yourself
        // ⚠️ The blade must be theirs, not merely in their hand. A thief wielding someone else's
        // Requiem cannot Arise, so leaving remnants they can never use would only mislead them.
        ItemStack blade = RequiemBlade.held(killer);
        if (blade.isEmpty() || RequiemBlade.bind(blade, killer) == null) return;
        if (ShadowData.isShadow(victim)) return;
        if (!(victim instanceof Mob) && !(victim instanceof ServerPlayer)) return;
        if (!ShadowClassifier.isRaisable(victim)) return;

        ShadowRemnant remnant = ModEntities.SHADOW_REMNANT.get().create(level);
        if (remnant == null) return;
        int slots = ShadowClassifier.slotsFor(victim);
        remnant.bind(ShadowSpawner.snapshot(victim), killer.getUUID(), slots);
        remnant.moveTo(victim.getX(), victim.getY(), victim.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(remnant);

        killer.displayClientMessage(Component.translatable("baileys_enchantments.requiem.remnant",
                victim.getType().getDescription(), slots, remnant.tier().label()).withStyle(ChatFormatting.DARK_GRAY), true);
    }

    // ---- controls ----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player p = event.getEntity();
        if (p.level().isClientSide() || !(p instanceof ServerPlayer player)) return;
        ItemStack held = event.getItemStack();
        if (!RequiemBlade.isRequiem(held)) return;
        if (player.getCooldowns().isOnCooldown(held.getItem())) return;
        if (AriseRitual.isHeld(player)) return;

        player.getCooldowns().addCooldown(held.getItem(), CONTROL_COOLDOWN);
        event.setCanceled(true);

        UUID armyId = RequiemBlade.bind(held, player);
        if (armyId == null) {
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.not_your_blade")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ShadowArmy army = ShadowArmies.get(player.server).armyOf(armyId);
        if (army.soldiers().isEmpty()) {
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.none")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        if (player.isShiftKeyDown()) {
            int n = ShadowArmyManager.recallAll(player.server, armyId);
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.recalled", n)
                    .withStyle(ChatFormatting.DARK_AQUA), true);
        } else {
            // Someone else holding a copy of this blade's army out? It comes home to this blade first.
            UUID other = army.holder();
            if (other != null && !other.equals(player.getUUID())) ShadowArmyManager.recallAll(player.server, armyId);
            int n = ShadowArmyManager.deployAll(player, armyId);
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.deployed", n)
                    .withStyle(ChatFormatting.DARK_AQUA), true);
        }
    }

    // ---- the blade must stay with the holder ------------------------------------------------

    /**
     * Once a second: any army out under a player whose inventory no longer holds its blade goes
     * back into the blade. This is the rule that makes the army the sword's, not the player's.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (UUID armyId : ShadowArmies.get(server).deployedBy(player.getUUID())) {
                if (!RequiemBlade.carries(player, armyId)) {
                    ShadowArmyManager.recallAll(server, armyId);
                    player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.blade_lost")
                            .withStyle(ChatFormatting.DARK_AQUA), true);
                }
            }
        }
    }

    // ---- holder lifecycle -------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShadowArmyManager.recallAllHeldBy(player.server, player.getUUID());
            ShadowStances.forget(player.getUUID());
        }
    }

    /** Recall from the old dimension, redeploy in the new: the army was out, so it stays out. */
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;
        for (UUID armyId : ShadowArmies.get(server).deployedBy(player.getUUID())) {
            ShadowArmyManager.recallAll(server, armyId);
            if (RequiemBlade.carries(player, armyId)) ShadowArmyManager.deployAll(player, armyId);
        }
    }

    // ---- strays ---------------------------------------------------------------------------

    /**
     * A body in the world must match a deployed roster entry with its own UUID. Anything else is
     * a stray: recalled while its chunk was unloaded, released, or duplicated by a rollback.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Mob mob) || !ShadowData.isShadow(mob)) return;
        UUID rosterId = ShadowData.rosterId(mob);
        UUID armyId = ShadowData.armyId(mob);
        if (rosterId == null) return;

        ShadowArmies armies = ShadowArmies.get(level.getServer());
        ShadowArmy.Soldier s = armyId != null && armies.has(armyId) ? armies.armyOf(armyId).find(rosterId) : null;
        boolean legitimate = s != null && s.deployed && mob.getUUID().equals(s.entityId);
        if (!legitimate) {
            ShadowTeam.leave(mob);
            mob.discard();
            event.setCanceled(true);
        }
    }
}

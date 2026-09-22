package com.bailey.enchantments.shadow;

import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.entity.ModEntities;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Requiem's hooks into the world.
 *
 * <ul>
 *   <li>A kill with Requiem leaves a {@link ShadowRemnant}. Ignoring it fills nothing — a mob
 *       grinder builds no army. Only chosen kills are kept.
 *   <li>Right-click deploys the army; sneak + right-click on nothing recalls it.
 *   <li>Owner death, logout and dimension change recall it. ✅ Bailey: <i>"Owner dies or logs out
 *       the army is sent to storage."</i> A dimension change redeploys on the other side.
 *   <li>A shadow that dies in the field is struck from the roster.
 *   <li>A body that turns up with no matching roster entry is a stray, and is removed.
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class RequiemEvents {
    private RequiemEvents() {}

    private static final int CONTROL_COOLDOWN = 20;

    public static boolean hasRequiem(ItemStack stack) {
        return Ultimates.hasUltimate(stack, ModEnchantments.REQUIEM.get());
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

        // The owner fell: everything comes home.
        if (victim instanceof ServerPlayer player) {
            ShadowArmyManager.recallAll(player.server, player.getUUID());
            return;
        }

        // A kill worth remembering.
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (!hasRequiem(killer.getMainHandItem())) return;
        if (ShadowData.isShadow(victim)) return;
        if (!(victim instanceof Mob)) return;                    // players: Phase 6
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
        if (!hasRequiem(held)) return;
        if (player.getCooldowns().isOnCooldown(held.getItem())) return;
        if (AriseRitual.isHeld(player)) return;

        player.getCooldowns().addCooldown(held.getItem(), CONTROL_COOLDOWN);
        event.setCanceled(true);

        ShadowArmy army = ShadowArmies.get(player.server).armyOf(player.getUUID());
        if (army.soldiers().isEmpty()) {
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.none")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        if (player.isShiftKeyDown()) {
            int n = ShadowArmyManager.recallAll(player.server, player.getUUID());
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.recalled", n)
                    .withStyle(ChatFormatting.DARK_AQUA), true);
        } else {
            int n = ShadowArmyManager.deployAll(player);
            player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.deployed", n)
                    .withStyle(ChatFormatting.DARK_AQUA), true);
        }
    }

    // ---- owner lifecycle -------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShadowArmyManager.recallAll(player.server, player.getUUID());
            ShadowStances.forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ShadowArmies armies = ShadowArmies.get(player.server);
        if (armies.has(player.getUUID())) {
            ShadowStances.set(player.getUUID(), armies.armyOf(player.getUUID()).stance());
        }
    }

    /** Recall from the old dimension, redeploy in the new: the army was out, so it stays out. */
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;
        ShadowArmies armies = ShadowArmies.get(server);
        if (!armies.has(player.getUUID())) return;
        boolean wasOut = armies.armyOf(player.getUUID()).anyDeployed();
        ShadowArmyManager.recallAll(server, player.getUUID());
        if (wasOut) ShadowArmyManager.deployAll(player);
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
        UUID ownerId = ShadowData.ownerId(mob);
        if (rosterId == null || ownerId == null) return;

        ShadowArmies armies = ShadowArmies.get(level.getServer());
        ShadowArmy.Soldier s = armies.has(ownerId) ? armies.armyOf(ownerId).find(rosterId) : null;
        boolean legitimate = s != null && s.deployed && mob.getUUID().equals(s.entityId);
        if (!legitimate) {
            ShadowTeam.leave(mob);
            mob.discard();
            event.setCanceled(true);
        }
    }
}

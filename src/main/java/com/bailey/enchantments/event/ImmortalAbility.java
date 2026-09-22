package com.bailey.enchantments.event;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.DeepDarkBalance;
import com.bailey.enchantments.util.ItemCooldown;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>Immortal</b> — Aegis's Secret. Designed by Bailey, 2026-09-22.
 *
 * <p>A <b>full set</b> enchantment, like Dissonance: three pieces do nothing at all.
 *
 * <h2>What the set gives, permanently</h2>
 * <ul>
 *   <li><b>Immunity to every debuff.</b> Refused outright, so there is no icon and no swirl.
 *   <li><b>Aegis's protection, without Aegis's slowness.</b> Bailey: <i>"the removal of all
 *       speed debuffs from the ultimate with the same protection benefit."</i>
 * </ul>
 *
 * <h2>The ward</h2>
 * A keybind — not a right-click, so it can never fight with Resonance for the same input.
 * For {@link DeepDarkBalance#IMMORTAL_DURATION} the wearer takes <b>no damage at all</b>, and
 * pays for it: half movement speed, half damage dealt.
 *
 * <h2>⚠️ Total invulnerability is a timestamp, never a flag</h2>
 * Same rule as Resonance. {@code setInvulnerable(true)} that leaks leaves an unkillable player
 * behind — far worse than the ability failing. A deadline expires on its own whatever happens.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class ImmortalAbility {
    private ImmortalAbility() {}

    private static final UUID SLOW_ID = UUID.fromString("2e6b8c31-9a74-4f52-bd08-3c17a9e45d26");
    private static final UUID WEAKEN_ID = UUID.fromString("b58f0d47-6c23-41ae-97d1-0e84b2f36a95");
    private static final String NAME = "Immortal ward";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Who is warded, and until when (game time). */
    private static final Map<UUID, Long> WARDED = new HashMap<>();

    // ---- the set ---------------------------------------------------------------------------

    /** Does this wearer have Immortal on all four pieces? Nothing works below that. */
    public static boolean hasFullSet(LivingEntity wearer) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.IMMORTAL.get(), wearer.getItemBySlot(slot)) <= 0) {
                return false;
            }
        }
        return true;
    }

    /** The first Immortal piece worn, which is where the ability's cooldown is kept. */
    private static ItemStack keyPiece(LivingEntity wearer) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = wearer.getItemBySlot(slot);
            if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.IMMORTAL.get(), stack) > 0) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- permanent: no debuffs ----------------------------------------------------------------

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        if (event.getEffectInstance().getEffect().getCategory() != MobEffectCategory.HARMFUL) return;
        if (hasFullSet(player)) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    // ---- the ward ------------------------------------------------------------------------------

    /** Called from the network layer when the keybind is pressed. */
    public static void activate(ServerPlayer player) {
        if (!hasFullSet(player)) {
            player.displayClientMessage(Component.translatable("baileys_enchantments.immortal.no_set")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (isWarded(player)) return;

        ItemStack piece = keyPiece(player);
        if (ItemCooldown.isOnCooldown(piece, ItemCooldown.Abilities.IMMORTAL, player)) return;

        ItemCooldown.set(piece, ItemCooldown.Abilities.IMMORTAL, player, DeepDarkBalance.IMMORTAL_COOLDOWN);
        WARDED.put(player.getUUID(), player.level().getGameTime() + DeepDarkBalance.IMMORTAL_DURATION);
        applyToll(player, true);

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.5F);
        player.displayClientMessage(Component.translatable("baileys_enchantments.immortal.on")
                .withStyle(ChatFormatting.AQUA), true);
    }

    public static boolean isWarded(Player player) {
        Long until = WARDED.get(player.getUUID());
        if (until == null) return false;
        if (player.level().getGameTime() < until) return true;
        WARDED.remove(player.getUUID());
        return false;
    }

    /**
     * ⚠️ {@code LivingAttackEvent}, not {@code LivingHurtEvent}. "No damage at all" has to mean
     * the hit never happens: at hurt-time the knockback, the red flash and the armour damage
     * have already been decided. The same reasoning as Mirage's evade.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isWarded(player)) return;

        // The void is never dodged. Evading /kill or a fall out of the world strands a player —
        // the same exception Dissonance's evade makes.
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        event.setCanceled(true);
        if (player.level() instanceof ServerLevel level && player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.6, 0.4, 0.05);
        }
    }

    /** Half damage dealt, the other half of the price. */
    @SubscribeEvent
    public static void onDealDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!isWarded(player)) return;
        event.setAmount(event.getAmount() * DeepDarkBalance.IMMORTAL_DAMAGE_DEALT);
    }

    // ---- upkeep -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || WARDED.isEmpty()) return;

        Iterator<Map.Entry<UUID, Long>> it = WARDED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() == null
                    ? null
                    : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                            .getPlayerList().getPlayer(entry.getKey());
            if (player == null) { it.remove(); continue; }

            if (player.level().getGameTime() >= entry.getValue()) {
                applyToll(player, false);
                player.displayClientMessage(Component.translatable("baileys_enchantments.immortal.off")
                        .withStyle(ChatFormatting.GRAY), true);
                it.remove();
                continue;
            }

            if (player.tickCount % 5 == 0 && player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.5, 0.8, 0.5, 0.01);
            }
        }
    }

    /** The price of the ward: half speed while it runs, restored when it ends. */
    private static void applyToll(Player player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SLOW_ID);
        if (on) {
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, NAME,
                    -DeepDarkBalance.IMMORTAL_SLOW, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /**
     * ⚠️ A safety net, not decoration. If the ward's end is ever missed — a crash, a dimension
     * change, the player logging out mid-ward — this catches the leftover modifier the next
     * time they are seen without a ward running.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 40 != 0) return;
        if (isWarded(player)) return;

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SLOW_ID) != null) speed.removeModifier(SLOW_ID);
    }

    /** Unused hook kept for symmetry with the speed modifier, should damage ever need one. */
    static UUID weakenId() {
        return WEAKEN_ID;
    }
}

package com.bailey.enchantments.event;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.DeepDarkBalance;
import com.bailey.enchantments.util.ItemCooldown;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>Shortbow</b> — the bow fires the instant you click. No draw, no charge.
 *
 * <p>Bailey, 2026-09-22: <i>"a new ultimate enchantment called shortbow, which allows you to
 * just simply click to show a bow with no draw. A feature already existing in things like
 * hypixel skyblock."</i>
 *
 * <p>Every arrow leaves at <b>full power</b> — a drawn bow's maximum — so the trade is not
 * damage, it is that the bow never stops to aim. {@link DeepDarkBalance#SHORTBOW_COOLDOWN} keeps
 * that from being a machine gun, and it is a <b>per-item</b> cooldown so the rest of your
 * quiverful of bows is unaffected.
 *
 * <h2>⚠️ Why the vanilla draw has to be cancelled, not shortened</h2>
 * A bow's power comes from how long {@code useOn} ran. Leaving the draw in and firing early
 * would fire a weak arrow as well as ours. So the use is cancelled outright and the arrow is
 * built here, mirroring {@code BowItem.releaseUsing} at full charge — which is also why Power,
 * Punch, Flame and Infinity all have to be applied by hand: nothing else is going to.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class ShortbowAbility {
    private ShortbowAbility() {}

    public static boolean isShortbow(ItemStack stack) {
        return stack.getItem() instanceof BowItem
                && Ultimates.hasUltimate(stack, ModEnchantments.SHORTBOW.get());
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack bow = event.getItemStack();
        if (!isShortbow(bow)) return;

        // ⚠️ Cancelled on BOTH sides. On the client this stops the draw animation starting; on
        // the server it stops the item being "in use" at all.
        event.setCanceled(true);
        if (player.level().isClientSide()) return;
        if (ItemCooldown.isOnCooldown(bow, ItemCooldown.Abilities.SHORTBOW, player)) return;

        ItemStack ammo = player.getProjectile(bow);
        boolean free = player.getAbilities().instabuild
                || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, bow) > 0;
        if (ammo.isEmpty()) {
            if (!free) return;
            ammo = new ItemStack(Items.ARROW);
        }

        shoot(player, bow, ammo, free);
        ItemCooldown.set(bow, ItemCooldown.Abilities.SHORTBOW, player, DeepDarkBalance.SHORTBOW_COOLDOWN);
    }

    private static void shoot(Player player, ItemStack bow, ItemStack ammo, boolean free) {
        if (!(player.level() instanceof ServerLevel level)) return;

        ArrowItem arrowItem = ammo.getItem() instanceof ArrowItem item ? item : (ArrowItem) Items.ARROW;
        AbstractArrow arrow = arrowItem.createArrow(level, ammo, player);
        // 3.0 is what a fully drawn vanilla bow uses (power 1.0 × 3.0 in BowItem.releaseUsing).
        // Every Shortbow shot is a full draw, so the speed is constant.
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.0F, 1.0F);
        arrow.setCritArrow(true);      // a full draw always crits, and this is always a full draw

        int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (power > 0) arrow.setBaseDamage(arrow.getBaseDamage() + power * 0.5 + 0.5);

        int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (punch > 0) arrow.setKnockback(punch);

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) {
            arrow.setSecondsOnFire(100);
        }

        // ⚠️ The pickup rule has to match how the arrow was paid for, or an infinity bow
        // produces collectable arrows out of nothing.
        boolean spectral = ammo.is(Items.SPECTRAL_ARROW);
        if (free && (ammo.is(Items.ARROW) || spectral)) {
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }

        bow.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));
        if (!free && !player.getAbilities().instabuild) {
            ammo.shrink(1);
            if (ammo.isEmpty()) player.getInventory().removeItem(ammo);
        }

        level.addFreshEntity(arrow);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.random.nextFloat() * 0.4F + 1.2F) + 0.5F);
        player.awardStat(Stats.ITEM_USED.get(bow.getItem()));
    }

    /**
     * Belt and braces: if anything else ever does start a draw on a Shortbow, releasing it
     * fires nothing. Without this a mod that re-triggers the use would get a second arrow free.
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (isShortbow(event.getBow())) event.setCanceled(true);
    }
}

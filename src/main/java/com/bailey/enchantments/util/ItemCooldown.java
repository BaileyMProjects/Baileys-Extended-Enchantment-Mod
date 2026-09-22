package com.bailey.enchantments.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A cooldown that belongs to <b>one item</b>, and to one ability on it.
 *
 * <h2>🔴 Why not {@code Player.getCooldowns()}</h2>
 * Vanilla's cooldown map is keyed by {@code Item}, not by stack. Bailey, 2026-09-22: <i>"make
 * sure that the cooldown visual only applies to the singular item. With resonance it shows it on
 * all swords which it should only be on that one item and cooldowns between secret enchantments
 * shouldnt carry over."</i> Both halves of that are the same bug: using Resonance greyed out
 * every netherite sword in the inventory, and any other ability on a netherite sword shared the
 * timer.
 *
 * <p>So the expiry lives in the stack's own NBT, under a key per ability. Two different swords
 * have two different cooldowns; Resonance and a future weapon ability on the same sword have
 * two more.
 *
 * <p>⚠️ Stored as a <b>game-time deadline</b>, not a countdown. A countdown needs something to
 * tick it, and an item in a chest ticks for nobody — it would freeze and come back still on
 * cooldown weeks later. A deadline simply passes.
 */
public final class ItemCooldown {
    private ItemCooldown() {}

    private static final String ROOT = "BaileysCooldowns";

    public static boolean isOnCooldown(ItemStack stack, String ability, Player player) {
        return remaining(stack, ability, player) > 0;
    }

    /** Ticks left, or 0. */
    public static long remaining(ItemStack stack, String ability, Player player) {
        if (stack.isEmpty()) return 0;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ROOT)) return 0;
        long until = tag.getCompound(ROOT).getLong(ability);
        long now = player.level().getGameTime();
        return Math.max(0, until - now);
    }

    /** The fraction of the cooldown still to run, 0..1, for the overlay. */
    public static float fraction(ItemStack stack, String ability, Player player, int total) {
        if (total <= 0) return 0.0F;
        return Math.min(1.0F, remaining(stack, ability, player) / (float) total);
    }

    public static void set(ItemStack stack, String ability, Player player, int ticks) {
        if (stack.isEmpty()) return;
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag cooldowns = root.contains(ROOT) ? root.getCompound(ROOT) : new CompoundTag();
        cooldowns.putLong(ability, player.level().getGameTime() + ticks);
        root.put(ROOT, cooldowns);
    }

    /** The longest cooldown running on this stack, and how long it was, for the overlay. */
    public static float overlayFraction(ItemStack stack, Player player) {
        if (stack.isEmpty()) return 0.0F;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ROOT)) return 0.0F;

        CompoundTag cooldowns = tag.getCompound(ROOT);
        long now = player.level().getGameTime();
        float best = 0.0F;
        for (String ability : cooldowns.getAllKeys()) {
            long until = cooldowns.getLong(ability);
            if (until <= now) continue;
            int total = Abilities.durationOf(ability);
            if (total <= 0) continue;
            best = Math.max(best, Math.min(1.0F, (until - now) / (float) total));
        }
        return best;
    }

    /** The ability keys, and how long each one's cooldown runs, so the overlay can scale it. */
    public static final class Abilities {
        private Abilities() {}

        public static final String RESONANCE = "Resonance";
        public static final String IMMORTAL = "Immortal";
        public static final String SHORTBOW = "Shortbow";

        public static int durationOf(String ability) {
            return switch (ability) {
                case RESONANCE -> com.bailey.enchantments.util.DeepDarkBalance.RESONANCE_COOLDOWN;
                case IMMORTAL -> com.bailey.enchantments.util.DeepDarkBalance.IMMORTAL_COOLDOWN;
                case SHORTBOW -> com.bailey.enchantments.util.DeepDarkBalance.SHORTBOW_COOLDOWN;
                default -> 0;
            };
        }
    }
}

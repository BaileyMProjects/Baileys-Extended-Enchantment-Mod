package com.bailey.enchantments.event;

import java.util.HashMap;
import java.util.Map;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.menu.AncientAnvilMenu;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What a VANILLA anvil is allowed to do with this mod's gear. The Ancient Anvil is exempt —
 * it has its own menu and its own rules.
 *
 * <p>Decided 2026-09-21 (Brain/Decisions/Enchantment sourcing.md). Two rules:
 *
 * <ol>
 *   <li><b>Ultimates and Secrets are refused outright</b>, on either side. The Ancient Anvil's
 *       ritual is their only route and that is not negotiable.
 *   <li><b>Over-cap levels are preserved but never created.</b> A normal anvil may never raise
 *       an enchantment above the higher of (its native max, the level you already had).
 * </ol>
 *
 * <p>⚠️ This class used to simply CANCEL the anvil whenever an over-cap enchantment was present
 * on either side. That was far wider than the bug it guarded: it meant a Sharpness VI sword
 * could not even be <em>renamed</em>. The actual danger is narrow — vanilla clamps merges to
 * {@link Enchantment#getMaxLevel()}, which {@code OverCaps} never touches, so a VI is knocked
 * back to V <em>only when the same enchantment appears on both sides</em>.
 *
 * <p>⚠️ Because {@link AnvilUpdateEvent} fires <em>before</em> vanilla computes anything, there
 * is no way to post-process vanilla's result. The over-cap case therefore has to build its own
 * output. {@link #buildOverCapResult} mirrors {@code AnvilMenu.createResult} step for step and
 * differs in exactly one line — the ceiling. Keep it that way: if vanilla's anvil maths ever
 * changes, this is the method that has to follow it.
 *
 * <p>Everything that does not involve an over-cap level is left entirely to vanilla, including
 * applying this mod's own enchantments from a book — their {@code getMaxLevel()} is their real
 * max, so vanilla's clamp is already correct for them.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public class ModAnvilEvents {

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (event.getPlayer().level().isClientSide()) return;
        if (event.getPlayer().containerMenu instanceof AncientAnvilMenu) return;

        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // Rule 1: Ultimates and Secrets never touch a normal anvil.
        if (Ultimates.hasAnyUltimate(left) || Ultimates.hasAnyUltimate(right)) {
            block(event);
            return;
        }

        // Rule 2 only has anything to say when an over-cap level is actually in play.
        // Otherwise vanilla is already correct, and doing nothing is the safest thing we can do.
        if (!hasOverCapEnchantment(left) && !hasOverCapEnchantment(right)) return;

        buildOverCapResult(event, left, right);
    }

    /**
     * Refuse the operation. Per {@link AnvilUpdateEvent}'s own contract, cancelling skips
     * vanilla AND sets the output to {@link ItemStack#EMPTY} — so this cannot leave a stale
     * result sitting in the slot.
     */
    private static void block(AnvilUpdateEvent event) {
        event.setCanceled(true);
    }

    private static boolean hasOverCapEnchantment(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            if (entry.getValue() > entry.getKey().getMaxLevel()) return true;
        }
        return false;
    }

    /**
     * THE RULE. A normal anvil may never raise an enchantment above the higher of its native
     * max and the level the player already had.
     *
     * <p>Sharpness VI + VI -&gt; VI (merged, not downgraded). Sharpness V + V -&gt; V (no
     * over-cap created). Vampiric I + I -&gt; II (native max is 2, so a normal upgrade).
     */
    private static int ceilingFor(Enchantment ench, int leftLevel, int rightLevel) {
        return Math.max(ench.getMaxLevel(), Math.max(leftLevel, rightLevel));
    }

    private static void buildOverCapResult(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        Player player = event.getPlayer();
        ItemStack result = left.copy();
        Map<Enchantment, Integer> enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(result));

        int costAccrued = 0;
        int renameCost = 0;
        int materialCost = 0;
        int repairCostCarry = left.getBaseRepairCost() + (right.isEmpty() ? 0 : right.getBaseRepairCost());

        if (!right.isEmpty()) {
            boolean fromBook = right.is(Items.ENCHANTED_BOOK)
                    && !EnchantedBookItem.getEnchantments(right).isEmpty();

            if (result.isDamageableItem() && result.getItem().isValidRepairItem(left, right)) {
                // Repairing with raw material (iron ingots, diamonds...). No enchantment maths
                // happens on this branch at all, so the over-cap simply rides along untouched —
                // which is the whole point: this used to be blocked.
                int perUnit = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                if (perUnit <= 0) {
                    block(event);
                    return;
                }
                int unitsUsed;
                for (unitsUsed = 0; perUnit > 0 && unitsUsed < right.getCount(); unitsUsed++) {
                    result.setDamageValue(result.getDamageValue() - perUnit);
                    costAccrued++;
                    perUnit = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                }
                materialCost = unitsUsed;
            } else {
                if (!fromBook && (!result.is(right.getItem()) || !result.isDamageableItem())) {
                    block(event);
                    return;
                }

                if (result.isDamageableItem() && !fromBook) {
                    int leftRemaining = left.getMaxDamage() - left.getDamageValue();
                    int rightRemaining = right.getMaxDamage() - right.getDamageValue();
                    int bonus = rightRemaining + result.getMaxDamage() * 12 / 100;
                    int newDamage = Math.max(0, result.getMaxDamage() - (leftRemaining + bonus));
                    if (newDamage < result.getDamageValue()) {
                        result.setDamageValue(newDamage);
                        costAccrued += 2;
                    }
                }

                Map<Enchantment, Integer> toMerge = EnchantmentHelper.getEnchantments(right);
                boolean anyApplied = false;
                boolean anyRejected = false;

                for (Map.Entry<Enchantment, Integer> entry : toMerge.entrySet()) {
                    Enchantment ench = entry.getKey();
                    if (ench == null) continue;

                    int leftLevel = enchantments.getOrDefault(ench, 0);
                    int rightLevel = entry.getValue();
                    int merged = leftLevel == rightLevel ? rightLevel + 1 : Math.max(rightLevel, leftLevel);

                    boolean compatible = ench.canEnchant(left);
                    if (player.getAbilities().instabuild || left.is(Items.ENCHANTED_BOOK)) compatible = true;
                    for (Enchantment existing : enchantments.keySet()) {
                        if (existing != ench && !ench.isCompatibleWith(existing)) {
                            compatible = false;
                            costAccrued++;
                        }
                    }

                    if (!compatible) {
                        anyRejected = true;
                        continue;
                    }

                    anyApplied = true;

                    // ⚠️ THE ONE LINE THAT DIFFERS FROM VANILLA. Vanilla clamps to
                    // ench.getMaxLevel() here, which is what silently knocks Sharpness VI to V.
                    int ceiling = ceilingFor(ench, leftLevel, rightLevel);
                    if (merged > ceiling) merged = ceiling;
                    enchantments.put(ench, merged);

                    int perLevel = switch (ench.getRarity()) {
                        case COMMON -> 1;
                        case UNCOMMON -> 2;
                        case RARE -> 4;
                        case VERY_RARE -> 8;
                    };
                    if (fromBook) perLevel = Math.max(1, perLevel / 2);
                    costAccrued += perLevel * merged;
                    if (left.getCount() > 1) costAccrued = 40;
                }

                if (anyRejected && !anyApplied) {
                    block(event);
                    return;
                }
            }
        }

        String name = event.getName();
        if (name != null && !name.isBlank()) {
            if (!name.equals(left.getHoverName().getString())) {
                renameCost = 1;
                costAccrued += renameCost;
                result.setHoverName(Component.literal(name));
            }
        } else if (left.hasCustomHoverName()) {
            renameCost = 1;
            costAccrued += renameCost;
            result.resetHoverName();
        }

        // Nothing actually happened — no repair, no merge, no rename.
        if (costAccrued <= 0) {
            block(event);
            return;
        }

        int total = repairCostCarry + costAccrued;
        // Vanilla's rename discount: a pure rename is never "too expensive".
        if (renameCost == costAccrued && renameCost > 0 && total >= 40) total = 39;
        if (total >= 40 && !player.getAbilities().instabuild) {
            block(event);
            return;
        }

        int newRepairCost = result.getBaseRepairCost();
        if (!right.isEmpty() && newRepairCost < right.getBaseRepairCost()) {
            newRepairCost = right.getBaseRepairCost();
        }
        if (renameCost != costAccrued || renameCost == 0) {
            newRepairCost = AnvilMenu.calculateIncreasedRepairCost(newRepairCost);
        }
        result.setRepairCost(newRepairCost);

        EnchantmentHelper.setEnchantments(enchantments, result);
        event.setOutput(result);
        event.setCost(total);
        event.setMaterialCost(materialCost);
    }
}

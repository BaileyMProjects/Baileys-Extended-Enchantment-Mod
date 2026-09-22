package com.bailey.enchantments.shadow;

import java.util.UUID;

import javax.annotation.Nullable;

import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The army lives in the sword.
 *
 * <p>Bailey, 2026-09-22, after the first playtest: <i>"I do not know if it should be linked to the
 * user or the sword itself. So if the sword isnt held it cant be used and if the sword is dropped
 * and picked up by another player it gives u that same army."</i> Sword-bound is the answer, and
 * it is the mod's existing rule: Ultimates and Secrets are bound to the item, never the player.
 *
 * <p>A Requiem blade carries an <b>army id</b> in its NBT, assigned the first time it is used.
 * The roster is stored under that id. Whoever holds the blade commands the roster; the shadows
 * stay out while the blade is anywhere in the holder's inventory and return into it the moment
 * it is not — dropped, chested, or lost on death.
 *
 * <p>⚠️ <b>Migration is one line.</b> Before this, armies were keyed by player UUID. The first
 * blade a player uses with no army id of its own takes <b>the player's UUID</b> as its id, so a
 * roster built the night before continues under the same key with no copying at all.
 */
public final class RequiemBlade {
    private RequiemBlade() {}

    private static final String ARMY_KEY = "BaileysArmy";
    private static final String FOUNDER_KEY = "BaileysArmyFounder";

    public static boolean isRequiem(ItemStack stack) {
        return !stack.isEmpty() && Ultimates.hasUltimate(stack, ModEnchantments.REQUIEM.get());
    }

    /** The Requiem blade in either hand, main hand first, or empty. */
    public static ItemStack held(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isRequiem(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isRequiem(off)) return off;
        return ItemStack.EMPTY;
    }

    @Nullable
    public static InteractionHand heldHand(Player player) {
        if (isRequiem(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isRequiem(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    /** The blade's army id, or null if it has never commanded anything. */
    @Nullable
    public static UUID armyId(ItemStack blade) {
        if (!isRequiem(blade)) return null;
        CompoundTag tag = blade.getTag();
        return tag != null && tag.hasUUID(ARMY_KEY) ? tag.getUUID(ARMY_KEY) : null;
    }

    /** Who raised this blade's army. Only they may command it. */
    @Nullable
    public static UUID founder(ItemStack blade) {
        if (!isRequiem(blade)) return null;
        CompoundTag tag = blade.getTag();
        return tag != null && tag.hasUUID(FOUNDER_KEY) ? tag.getUUID(FOUNDER_KEY) : null;
    }

    /**
     * The army this player commands with this blade, assigning one if the blade is fresh — or
     * <b>null</b> if the blade belongs to somebody else.
     *
     * <p>✅ <b>An army is bound to the blade AND to the player who raised it.</b> Bailey,
     * 2026-09-22: <i>"The army never sticks for another player but the army always sticks with
     * the intended player even if they drop it. It is only until they are using a different
     * weapon or that weapon is burned that they no longer keep that army and its progress."</i>
     *
     * <p>So the three cases are:
     * <ul>
     *   <li><b>Your own blade</b> — yours, dropped and picked up a hundred times.
     *   <li><b>A different blade</b> — a different army, starting empty. Your necromancer level
     *       is untouched: it lives on you. This is what stops a level 1 player being handed four
     *       Wardens on a sword.
     *   <li><b>Somebody else's blade</b> — nothing. Not their army, and not a fresh one either,
     *       because overwriting the id would destroy the owner's roster the moment a thief
     *       right-clicked.
     * </ul>
     *
     * <p>⚠️ A blade from before this rule has an id but no founder. It is adopted by whoever
     * holds it first, which is the player whose UUID that id already was.
     */
    @Nullable
    public static UUID bind(ItemStack blade, Player holder) {
        if (!isRequiem(blade)) return null;
        UUID existing = armyId(blade);
        UUID founder = founder(blade);

        if (existing != null) {
            if (founder == null) {
                blade.getOrCreateTag().putUUID(FOUNDER_KEY, holder.getUUID());
                return existing;
            }
            return founder.equals(holder.getUUID()) ? existing : null;
        }

        UUID id = UUID.randomUUID();
        CompoundTag tag = blade.getOrCreateTag();
        tag.putUUID(ARMY_KEY, id);
        tag.putUUID(FOUNDER_KEY, holder.getUUID());
        return id;
    }

    /** Is a blade with this army id anywhere in the player's inventory? */
    public static boolean carries(Player player, UUID armyId) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (armyId.equals(armyId(inv.getItem(i)))) return true;
        }
        return false;
    }
}

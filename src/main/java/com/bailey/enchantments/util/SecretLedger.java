package com.bailey.enchantments.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * One weapon Secret and one armour Secret per player, at a time.
 *
 * <p>Bailey, 2026-09-22: <i>"there should be a counter for how many weapon secrets and armor
 * secrets you have at once. Once you unlock dissonance you shouldnt be able to get another until
 * you wipe that enchantment. Same with weapons but you should be able to have 1 armour and 1
 * weapon at a time."</i>
 *
 * <p>The ledger records, per player, which Secret they currently hold in each category. It is
 * written when a gamble is <b>won</b> and cleared when the Secret is <b>wiped</b> at the Ancient
 * Anvil's Remove tab and no item in their inventory still carries it. Winning the same Secret
 * again (a second Dissonance piece) is always allowed; a <i>different</i> Secret in the same
 * category is refused at the anvil until the old one is wiped.
 *
 * <p>World saved data rather than player NBT, for the same reason as the shadow armies: one
 * copy, keyed by UUID, that survives death without being copied by hand.
 */
public final class SecretLedger extends SavedData {
    private static final String NAME = "baileys_secret_ledger";

    public enum Category {
        WEAPON, ARMOR;

        public static Category of(Enchantment secret) {
            return secret.category == EnchantmentCategory.WEAPON ? WEAPON : ARMOR;
        }

        public String key() { return name().toLowerCase(); }
    }

    /** Per player: category → Secret registry id. */
    private final Map<UUID, Map<Category, ResourceLocation>> held = new HashMap<>();

    public static SecretLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SecretLedger::load, SecretLedger::new, NAME);
    }

    // ---- queries ----------------------------------------------------------------------------

    /** The Secret this player holds in the category, or null. */
    @Nullable
    public Enchantment holding(UUID player, Category category) {
        Map<Category, ResourceLocation> mine = held.get(player);
        if (mine == null) return null;
        ResourceLocation id = mine.get(category);
        return id == null ? null : ForgeRegistries.ENCHANTMENTS.getValue(id);
    }

    /**
     * The Secret standing in the way of winning {@code wanted}, or null if the way is clear. The
     * same Secret again is never in the way.
     */
    @Nullable
    public Enchantment blocking(UUID player, Enchantment wanted) {
        Enchantment current = holding(player, Category.of(wanted));
        return current != null && current != wanted ? current : null;
    }

    // ---- mutations ----------------------------------------------------------------------------

    public void record(UUID player, Enchantment won) {
        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(won);
        if (id == null) return;
        held.computeIfAbsent(player, p -> new HashMap<>()).put(Category.of(won), id);
        setDirty();
    }

    /**
     * A Secret was stripped from an item. The ledger clears only if nothing else in the player's
     * inventory still carries it — a wiped chestplate with three Dissonance pieces still worn
     * has not wiped Dissonance.
     */
    public void onWiped(Player player, Enchantment secret) {
        if (stillCarries(player, secret)) return;
        Map<Category, ResourceLocation> mine = held.get(player.getUUID());
        if (mine == null) return;
        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(secret);
        if (id != null && id.equals(mine.get(Category.of(secret)))) {
            mine.remove(Category.of(secret));
            setDirty();
        }
    }

    private static boolean stillCarries(Player player, Enchantment secret) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(secret, stack) > 0) return true;
        }
        return false;
    }

    // ---- persistence ----------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, Map<Category, ResourceLocation>> e : held.entrySet()) {
            CompoundTag mine = new CompoundTag();
            for (Map.Entry<Category, ResourceLocation> c : e.getValue().entrySet()) {
                mine.putString(c.getKey().key(), c.getValue().toString());
            }
            all.put(e.getKey().toString(), mine);
        }
        tag.put("Held", all);
        return tag;
    }

    private static SecretLedger load(CompoundTag tag) {
        SecretLedger data = new SecretLedger();
        CompoundTag all = tag.getCompound("Held");
        for (String key : all.getAllKeys()) {
            try {
                UUID player = UUID.fromString(key);
                CompoundTag mine = all.getCompound(key);
                Map<Category, ResourceLocation> map = new HashMap<>();
                for (Category c : Category.values()) {
                    if (mine.contains(c.key())) {
                        ResourceLocation id = ResourceLocation.tryParse(mine.getString(c.key()));
                        if (id != null) map.put(c, id);
                    }
                }
                data.held.put(player, map);
            } catch (IllegalArgumentException ignored) {
                // not a UUID key; skip rather than refuse to load the world
            }
        }
        return data;
    }
}

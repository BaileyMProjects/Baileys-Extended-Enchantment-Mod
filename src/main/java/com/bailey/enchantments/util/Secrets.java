package com.bailey.enchantments.util;

import java.util.HashMap;
import java.util.Map;

import com.bailey.enchantments.enchantment.ModEnchantments;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

// The Secret tier: an ULTIMATE → the Secret gambled for at the Ancient Anvil.
//
// ⚠️ THE INPUT IS AN ULTIMATE, NOT A MAXED BASE ENCHANTMENT (redesigned 2026-09-21).
// This map used to run Sharpness → Resonance. Bailey moved the Secret tier ABOVE the Ultimate
// tier rather than beside it: "the odds should be a lot higher like 50/50 but you have to get
// the ultimate to make it a secret". So the chain is now three rungs, not two:
//
//     maxed Sharpness --(Ultimate ritual)--> Execute --(50% gamble)--> Resonance
//
// A failed roll destroys the Ultimate outright, so a player must re-max a base enchantment and
// re-run the Ultimate ritual before they can try again. At 50% that is ~2 Ultimates spent per
// Secret won. -> Brain/Decisions/Secret enchantments.md
//
// Deliberately still a MIRROR of Ultimates rather than an extension of it. A Secret occupies
// the same one-per-item slot an Ultimate does and is bound to the item the same way, but the
// two maps stay separate so the anvil can ask "what would the Ultimate be" and "what would the
// Secret be" independently — which is exactly what the toggle switches between.
//
// ⚠️ A SECRET IS AN ULTIMATE AS FAR AS EXCLUSION IS CONCERNED. Ultimates.isUltimate() returns
// true for SecretEnchantment (it is an UltimateEnchantment subclass), so the Remove tab's
// filter, removeAllUltimates and the one-Ultimate-per-item rule all cover Secrets for free.
// That inheritance is load-bearing — see Brain/Decisions/Secret enchantments.md.
//
// ⚠️ A CONSEQUENCE OF ONE-ULTIMATE-PER-ITEM: because an item can carry at most one Ultimate,
// the anvil's Secret-mode option list can only ever hold 0 or 1 entry. The selector arrows on
// that tab are therefore vestigial. Known and accepted, not a bug.
public final class Secrets {

    private static Map<Enchantment, Enchantment> ultimateToSecret = null;
    private static Map<Enchantment, Enchantment> secretToUltimate = null;

    private Secrets() {}

    private static void ensureBuilt() {
        if (ultimateToSecret != null) return;
        ultimateToSecret = new HashMap<>();
        secretToUltimate = new HashMap<>();

        // Resonance is gambled for with EXECUTE, which is Sharpness's Ultimate — so the
        // lineage Bailey wanted is unchanged (Resonance stays reachable from the general
        // weapon ladder, not from the Deep Dark one), while the old tension becomes literal:
        // you must EARN Execute and then destroy it to try for Resonance.
        //
        // This closes Open decisions #21. The base was an inference of mine when the map ran
        // off Sharpness; the 2026-09-21 redesign settled it by moving the whole tier up a rung.
        add(ModEnchantments.EXECUTE.get(), ModEnchantments.RESONANCE.get());

        // The armour evasion line. Mirage is gambled away for Dissonance exactly as Execute is
        // for Resonance, and Dissonance keeps Mirage's passives for the same reason: winning
        // must never be a downgrade.
        add(ModEnchantments.MIRAGE.get(), ModEnchantments.DISSONANCE.get());

        // The Shadow Monarch. Umbra is gambled away for Requiem, which keeps Umbra's damage.
        add(ModEnchantments.UMBRA.get(), ModEnchantments.REQUIEM.get());

        // Immortal, from Aegis — the second armour Secret, and Dissonance's rival for the one
        // armour slot a player may hold. -> SecretLedger
        add(ModEnchantments.AEGIS.get(), ModEnchantments.IMMORTAL.get());
    }

    private static void add(Enchantment ultimate, Enchantment secret) {
        ultimateToSecret.put(ultimate, secret);
        secretToUltimate.put(secret, ultimate);
    }

    public static boolean isSecret(Enchantment ench) {
        ensureBuilt();
        return secretToUltimate.containsKey(ench) || ench instanceof ModEnchantments.SecretEnchantment;
    }

    // The Secret an Ultimate can be gambled for, or null if that Ultimate has no Secret.
    public static Enchantment getSecretForUltimate(Enchantment ultimate) {
        ensureBuilt();
        return ultimateToSecret.get(ultimate);
    }

    // The Ultimate a Secret was gambled from.
    public static Enchantment getUltimateForSecret(Enchantment secret) {
        ensureBuilt();
        return secretToUltimate.get(secret);
    }

    // Does this item carry any Secret? Used by the tooltip and by the anvil's gate on
    // re-gambling an item that already won.
    public static boolean hasAnySecret(ItemStack stack) {
        ensureBuilt();
        for (Enchantment ench : EnchantmentHelper.getEnchantments(stack).keySet()) {
            if (isSecret(ench)) return true;
        }
        return false;
    }
}

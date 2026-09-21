package com.bailey.enchantments.util;

import com.bailey.enchantments.network.ModNetwork;
import com.bailey.enchantments.network.SecretRevealPayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Whether a player has yet seen what a Secret enchantment is called.
 *
 * Until they have, the word is drawn with vanilla's obfuscated style — a rapid scramble — so
 * the tier reads as genuinely unknown rather than as a label they have simply not unlocked.
 *
 * <p><b>The rule, decided by Bailey 2026-09-21 (Open decisions #29):</b>
 * <blockquote>"attempting counts as a use. per player first time on every world. So even if u
 * used it on a different world or server it will still be scrambled first time everytime you
 * create a new world."</blockquote>
 *
 * <ul>
 *   <li><b>Attempting</b> reveals it — win or lose. Losing already costs an Ultimate and a
 *       crystal, which is price enough for a name.
 *   <li><b>Per player, per world.</b> This is why the flag lives in the player's persisted NBT
 *       and NOT in a client config: a config would leak the reveal across every world and
 *       server on that PC, which is exactly what was ruled out.
 * </ul>
 *
 * <p>⚠️ Two things silently break this if forgotten, and both are handled in
 * {@code SecretRitualEvents}:
 * <ol>
 *   <li><b>Death and dimension change rebuild the player object.</b> The persisted tag must be
 *       copied in {@code PlayerEvent.Clone} or the reveal is lost every time you die.
 *   <li><b>The client cannot read server NBT.</b> Drawing obfuscated-or-not is a client
 *       decision, so the flag is synced on login and again the moment it flips.
 * </ol>
 */
public final class SecretReveal {

    /** Lives inside Forge's PERSISTED_NBT_TAG, which is the part of player data meant to survive. */
    private static final String KEY = "baileys_secret_revealed";

    private SecretReveal() {}

    // ---- server side -----------------------------------------------------------------

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean isRevealed(Player player) {
        return persisted(player).getBoolean(KEY);
    }

    /**
     * Mark the name as known and tell the client. Safe to call repeatedly — it only sends a
     * packet on the transition, so hammering the anvil does not spam the network.
     */
    public static void reveal(ServerPlayer player) {
        if (isRevealed(player)) return;
        persisted(player).putBoolean(KEY, true);
        sync(player);
    }

    /** Push the current value to one player's client. Called on login and on the transition. */
    public static void sync(ServerPlayer player) {
        ModNetwork.sendToPlayer(new SecretRevealPayload(isRevealed(player)), player);
    }

    /**
     * Carry the flag across a respawn or a dimension change.
     *
     * ⚠️ Forge hands us a brand new Player object in both cases. Without this the reveal is
     * silently lost on every death — and because the failure is invisible (the text just goes
     * back to scrambled) it would be blamed on the scramble logic, not on respawn.
     */
    public static void copyOnClone(Player original, Player replacement) {
        if (isRevealed(original)) {
            persisted(replacement).putBoolean(KEY, true);
        }
    }

    // ---- client side -----------------------------------------------------------------

    /**
     * The client's copy. ⚠️ Static, and therefore shared across worlds within one game session,
     * which is why {@link #sync} is sent on every login — joining a new world must overwrite
     * the value left behind by the last one, not inherit it.
     */
    private static boolean clientRevealed;

    public static void setClientRevealed(boolean revealed) {
        clientRevealed = revealed;
    }

    public static boolean isRevealedClient() {
        return clientRevealed;
    }
}

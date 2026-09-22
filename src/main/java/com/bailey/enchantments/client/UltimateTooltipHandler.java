package com.bailey.enchantments.client;

import java.util.List;
import java.util.Map;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.util.SecretReveal;
import com.bailey.enchantments.util.Secrets;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

// Replaces the standard gray enchantment name line for any ultimate enchantment
// with a bold pink version so they stand out clearly from normal enchantments,
// and inserts a gray lore line beneath it describing what the ultimate does.
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, value = Dist.CLIENT)
public class UltimateTooltipHandler {
    private static final Style ULTIMATE_STYLE = Style.EMPTY
            .withColor(TextColor.fromRgb(0xFF85C2))
            .withBold(true);
    // Secrets are red rather than pink, and carry an animated per-character shimmer — the
    // "cool text effect" Bailey asked for on 2026-08-09, and what makes the tier read as
    // secret at a glance rather than as just another colour of Ultimate.
    //
    // ⚠️ SHIMMER once the name is KNOWN; obfuscated while it is not. Updated 2026-09-21.
    //
    // This used to read "shimmer, NOT obfuscation", because obfuscation scrambles forever and
    // a player could never read which Secret they had won. Bailey then added the missing half:
    // the scramble lifts permanently once you have ATTEMPTED a Secret in that world. So the
    // name is unreadable only while genuinely unknown, and the old objection no longer holds.
    // See secretName() below and Brain/Decisions/The Secret ritual.md.
    private static final int SECRET_RED = 0xE0293A;
    private static final int SECRET_RED_BRIGHT = 0xFF8080;

    private static final Style DESC_STYLE = Style.EMPTY
            .withColor(TextColor.fromRgb(0xAAAAAA))
            .withItalic(true);

    /** Dimmer than the descriptions, so the hint reads as chrome rather than content. */
    private static final Style HINT_STYLE = Style.EMPTY
            .withColor(TextColor.fromRgb(0x6A6A78))
            .withItalic(true);

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(event.getItemStack());
        if (enchantments.isEmpty()) return;

        List<Component> tooltip = event.getToolTip();
        boolean details = detailsShown();
        boolean anyOurs = false;

        for (Enchantment ench : enchantments.keySet()) {
            ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(ench);
            // ✅ EVERY enchantment of ours gets a description, not only the Ultimates — Bailey,
            // 2026-09-22: "lets make all of the descriptions more detailed for everything."
            // Venom, Vein Miner and the rest had descriptions written and never shown, because
            // this loop skipped anything that was not an Ultimate.
            if (key == null || !key.getNamespace().equals(BaileysEnchantments.MODID)) continue;
            anyOurs = true;
            boolean ultimate = Ultimates.isUltimate(ench);
            String translationKey = "enchantment." + key.getNamespace() + "." + key.getPath();

            String plainName = ench.getFullname(enchantments.get(ench)).getString();
            for (int i = 0; i < tooltip.size(); i++) {
                if (tooltip.get(i).getString().equals(plainName)) {
                    // Only the top tiers are recoloured. An ordinary mod enchantment keeps
                    // vanilla's grey name and simply gains a line under it.
                    if (ultimate) {
                        tooltip.set(i, Secrets.isSecret(ench)
                                ? secretName(translationKey)
                                : Component.translatable(translationKey).withStyle(ULTIMATE_STYLE));
                    }
                    if (details) tooltip.add(i + 1, description(ench, translationKey));
                    break;
                }
            }
        }

        // One hint for the whole item, not one per enchantment.
        if (anyOurs && !details) {
            tooltip.add(Component.translatable("baileys_enchantments.tooltip.hold_shift")
                    .withStyle(HINT_STYLE));
        }
    }

    /**
     * The description line under an enchantment's name.
     *
     * <p>✅ <b>An armour Secret's description is scrambled until the full set is worn</b> —
     * Bailey, 2026-09-22: <i>"make armour secret enchantments have the scribbled animation on
     * the writing until the user gets the full set and have the description tell you to get the
     * full set to unlock."</i>
     *
     * <p>So the line reads as an unreadable smear with one legible instruction after it. That is
     * the honest signal: an armour Secret genuinely does <b>nothing at all</b> until all four
     * pieces carry it, and a player wearing one piece needs to be told that rather than left to
     * conclude the enchantment is broken.
     *
     * <p>⚠️ Counted on the CLIENT from what is worn, which is all the tooltip has. A player
     * reading the tooltip of a piece in a chest sees the scramble until they have the set on —
     * correct, since that is exactly when it starts working.
     */
    private static Component description(Enchantment ench, String translationKey) {
        Component real = Component.translatable(translationKey + ".desc");
        if (!isSetLocked(ench)) return real.copy().withStyle(DESC_STYLE);

        return Component.empty()
                .append(real.copy().withStyle(s -> s.withColor(SECRET_RED).withObfuscated(true)))
                .append(Component.literal("  "))
                .append(Component.translatable("baileys_enchantments.secret.needs_set")
                        .withStyle(s -> s.withColor(SECRET_RED).withItalic(true)));
    }

    /**
     * ✅ <b>Descriptions are behind Shift</b> — Bailey, 2026-09-22: <i>"the tooltips I think
     * should be toggle since they are really big descriptions but useful."</i>
     *
     * <p>They are long on purpose, and a sword with four of our enchantments on it was burying
     * the item's own stats under a wall of text. Held Shift shows them all; otherwise one grey
     * hint line, once, however many enchantments are on the item.
     *
     * <p>⚠️ The <b>name</b> is never hidden — only the description. A Secret's colour and
     * scramble are how the tier reads at a glance, and that has to survive a closed tooltip.
     */
    private static boolean detailsShown() {
        return Screen.hasShiftDown();
    }

    /** An armour Secret the viewer is not yet wearing a full set of. */
    private static boolean isSetLocked(Enchantment ench) {
        if (!Secrets.isSecret(ench)) return false;
        if (ench.category != EnchantmentCategory.ARMOR) return false;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return true;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (EnchantmentHelper.getItemEnchantmentLevel(ench, player.getItemBySlot(slot)) <= 0) {
                return true;
            }
        }
        return false;
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /**
     * A Secret's name on an item tooltip.
     *
     * <p>⚠️ <b>Scrambled until this player has attempted a Secret in this world</b>, then the
     * real name from then on — Bailey, 2026-09-21: <i>"that scramble should be used on the
     * preview for the item. So it shows the red enchantment but the text is scrambled."</i>
     *
     * <p>⚠️ This reinstates something that was deliberately REMOVED on 2026-09-19, and the
     * reversal is sound. Obfuscation was taken out then because it scrambles forever, so a
     * player could never read which Secret they had won. The reveal-on-attempt rule answers
     * exactly that: it is unreadable only while genuinely unknown. Do not "restore" the old
     * behaviour on the strength of that older comment.
     *
     * <p>The colour is red either way. Only legibility changes, never identity.
     */
    private static Component secretName(String translationKey) {
        if (!SecretReveal.isRevealedClient()) {
            return Component.translatable(translationKey)
                    .withStyle(s -> s.withColor(SECRET_RED).withObfuscated(true));
        }
        return shimmer(Component.translatable(translationKey).getString());
    }

    // Build the name one character at a time, each with its own colour, so a bright band
    // travels along the word.
    //
    // ⚠️ Driven by wall-clock time rather than a tick counter on purpose: this runs inside a
    // tooltip build, which has no tick context and happens once per frame while the stack is
    // hovered. System time is the only clock available here that advances smoothly.
    //
    // Resolved to a literal string first: a translatable Component cannot be styled per
    // character, and the shimmer has to survive translation into any language.
    private static Component shimmer(String name) {
        MutableComponent out = Component.empty();
        long now = System.currentTimeMillis();
        for (int i = 0; i < name.length(); i++) {
            // 0..1 wave, offset per character so the band moves rather than the whole word
            // pulsing in unison. 400ms per character keeps it slow enough not to be noisy.
            double phase = (now / 400.0D) - i * 0.45D;
            float t = (float) ((Math.sin(phase) + 1.0D) / 2.0D);
            // Bias toward the base red so the bright end is a highlight, not half the word.
            t = t * t * t;
            out = out.append(Component.literal(String.valueOf(name.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(lerpColor(SECRET_RED, SECRET_RED_BRIGHT, t)))
                            .withBold(true)));
        }
        return out;
    }

    private static int lerpColor(int from, int to, float t) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }
}

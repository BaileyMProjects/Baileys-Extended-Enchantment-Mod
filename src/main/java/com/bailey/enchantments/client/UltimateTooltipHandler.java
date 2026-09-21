package com.bailey.enchantments.client;

import java.util.List;
import java.util.Map;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.util.SecretReveal;
import com.bailey.enchantments.util.Secrets;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(event.getItemStack());
        if (enchantments.isEmpty()) return;

        List<Component> tooltip = event.getToolTip();
        for (Enchantment ench : enchantments.keySet()) {
            if (!Ultimates.isUltimate(ench)) continue;

            ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(ench);
            if (key == null) continue;
            String translationKey = "enchantment." + key.getNamespace() + "." + key.getPath();

            String plainName = ench.getFullname(enchantments.get(ench)).getString();
            for (int i = 0; i < tooltip.size(); i++) {
                if (tooltip.get(i).getString().equals(plainName)) {
                    tooltip.set(i, Secrets.isSecret(ench)
                            ? secretName(translationKey)
                            : Component.translatable(translationKey).withStyle(ULTIMATE_STYLE));
                    tooltip.add(i + 1, Component.translatable(translationKey + ".desc").withStyle(DESC_STYLE));
                    break;
                }
            }
        }
    }

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

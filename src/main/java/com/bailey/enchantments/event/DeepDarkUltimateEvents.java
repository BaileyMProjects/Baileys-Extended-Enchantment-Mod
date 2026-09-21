package com.bailey.enchantments.event;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.DeepDark;
import com.bailey.enchantments.util.DeepDarkBalance;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// The deep Ultimate ladder: Shatter (Sculkbane's ceiling) and Silence (Deepward's).
//
// ⚠️ Separate from both ModUltimateEvents and DeepDarkCombatEvents on purpose. The file names
// map to tiers: ModUltimateEvents is the vanilla-based ladder, DeepDarkCombatEvents is the
// Deep Dark base tier, this is the Deep Dark Ultimate tier. Keeping expansion code out of
// ModUltimateEvents is the standing rule — that class is ~1000 lines and the rework it sits
// in has still never been launched.
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public class DeepDarkUltimateEvents {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // -------------------------------------------------------------------------
    // Shatter: Sculkbane's bonus, much larger, under exactly the same rule.
    //
    // Same shape as Sculkbane deliberately — flat, conditional, and worth NOTHING outside the
    // Deep Dark rule. An Ultimate that quietly became unconditional would undo the trade the
    // whole tier is built on.
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingHurtShatter(LivingHurtEvent event) {
        Entity cause = event.getSource().getDirectEntity();
        if (!(cause instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide()) return;

        if (!Ultimates.hasUltimate(attacker.getMainHandItem(), ModEnchantments.SHATTER.get())) return;
        if (!DeepDark.bonusApplies(attacker, event.getEntity())) return;

        event.setAmount(event.getAmount() + DeepDarkBalance.SHATTER_DAMAGE_BONUS);
    }

    // -------------------------------------------------------------------------
    // Silence: the wearer stops emitting vibrations entirely.
    //
    // ⚠️ THIS MECHANIC WAS RECORDED AS PROBABLY-UNBUILDABLE. It is not. Forge's
    // VanillaGameEvent fires from Level.gameEvent BEFORE the event reaches any listener, and
    // it is cancelable and carries getCause() — the entity that emitted it. Cancelling by
    // cause suppresses the wearer at the source, so there is nothing to hook per-listener and
    // no need to touch shriekers, sensors or the Warden's brain at all.
    //
    // Verified against forge-1.20.1-47.4.20: getCause() exists and returns Entity. This mod
    // already cancels this event elsewhere (SculkMuting, for the Ancient Table's sensors), so
    // the hook is proven in this codebase rather than assumed from docs.
    //
    // ⚠️ WHAT THIS ACTUALLY DOES, because it is broader than "shriekers escalate more slowly":
    // vibrations are how the Deep Dark perceives anything at all, so a silenced player is
    // invisible to sculk sensors, to shriekers (warning level never rises, so the Warden is
    // never summoned by them) AND to an already-spawned Warden, which is blind and hunts by
    // vibration. It can still find the wearer by SMELL, which is what stops this being true
    // invisibility. That is a big effect — but Zephyr already grants flight and Eternal makes
    // an item unbreakable, so it is not out of scale for this tier.
    //
    // ⚠️ Deliberately NOT filtered by GameEvent type. "Silence" that leaks whenever you place
    // a block would be a worse enchantment and a confusing one. Killing a mob still spreads
    // sculk from a catalyst — that vibration's cause is the mob that died, not the wearer.
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onVanillaGameEventSilence(VanillaGameEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getCause() instanceof LivingEntity wearer)) return;
        if (!hasSilence(wearer)) return;

        event.setCanceled(true);
    }

    // Any single piece, not the full set — Ultimates are per-item everywhere else in this mod.
    private static boolean hasSilence(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (Ultimates.hasUltimate(entity.getItemBySlot(slot), ModEnchantments.SILENCE.get())) return true;
        }
        return false;
    }
}

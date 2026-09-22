package com.bailey.enchantments.event;

import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.DeepDarkBalance;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Deepward's armour toughness — the "better armour buffs" Bailey asked for on 2026-09-22.
 *
 * <p>Toughness rather than armour points, because the damage formula stops counting armour past
 * 20 and a netherite set is already there. Toughness is what still does something at that point:
 * it reduces how fast heavy hits cut through the armour rating, which is exactly the Warden's
 * problem.
 *
 * <p>⚠️ Applied as a live modifier rather than baked into the item, because the enchantment can
 * be on any armour and the total must follow what is currently worn. Reapplied whenever the sum
 * changes and removed when it reaches zero, so nothing lingers on a player who took the set off.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class DeepwardToughness {
    private DeepwardToughness() {}

    private static final UUID TOUGHNESS_ID = UUID.fromString("7f3a4c15-2b68-4d09-91e7-5c8a6b2d3f40");
    private static final String NAME = "Deepward";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        // Once a second is enough: the only thing that changes it is swapping armour.
        if (event.player.tickCount % 20 != 0) return;
        apply(event.player);
    }

    private static void apply(LivingEntity wearer) {
        AttributeInstance toughness = wearer.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness == null) return;

        int levels = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            levels += EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.DEEPWARD.get(), wearer.getItemBySlot(slot));
        }

        double wanted = levels * DeepDarkBalance.DEEPWARD_TOUGHNESS_PER_LEVEL;
        AttributeModifier current = toughness.getModifier(TOUGHNESS_ID);

        if (wanted <= 0.0) {
            if (current != null) toughness.removeModifier(TOUGHNESS_ID);
            return;
        }
        if (current != null) {
            if (current.getAmount() == wanted) return;
            toughness.removeModifier(TOUGHNESS_ID);
        }
        toughness.addTransientModifier(new AttributeModifier(
                TOUGHNESS_ID, NAME, wanted, AttributeModifier.Operation.ADDITION));
    }
}

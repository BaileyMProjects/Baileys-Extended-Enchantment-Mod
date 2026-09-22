package com.bailey.enchantments.client;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.network.ModNetwork;
import com.bailey.enchantments.network.SecretAbilityPayload;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sneak + right-click <b>air</b>, for Immortal's ward.
 *
 * <p>⚠️ This exists only because {@code RightClickEmpty} is <b>client-only</b>. The block case is
 * handled server-side in {@code ImmortalAbility}; clicking at open sky never reaches the server
 * at all, so the client has to ask. Same limitation Dissonance's toggle has, answered here
 * rather than left as "it works unless you are looking at the horizon".
 *
 * <p>The client checks what it is wearing before sending, so this is not a packet fired on every
 * sneak-click. The server re-checks everything regardless.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, value = Dist.CLIENT)
public final class SecretAbilityInput {
    private SecretAbilityInput() {}

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || player != event.getEntity()) return;
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.IMMORTAL.get(), player.getItemBySlot(slot)) <= 0) {
                return;
            }
        }
        ModNetwork.sendToServer(new SecretAbilityPayload(SecretAbilityPayload.Ability.IMMORTAL));
    }
}

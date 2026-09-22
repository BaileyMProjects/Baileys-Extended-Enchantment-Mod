package com.bailey.enchantments.entity;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.shadow.ShadowPlayer;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes {
    private ModEntityAttributes() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SHADOW_PLAYER.get(), ShadowPlayer.createAttributes().build());
    }
}

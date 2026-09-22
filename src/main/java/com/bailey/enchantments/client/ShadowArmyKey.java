package com.bailey.enchantments.client;

import org.lwjgl.glfw.GLFW;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.network.ArmyActionPayload;
import com.bailey.enchantments.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The key that opens the Shadow Army screen. Rebindable; G by default. The press only asks the
 * server — it decides whether this player may see a roster, and sends it if so.
 */
public final class ShadowArmyKey {
    private ShadowArmyKey() {}

    public static final KeyMapping OPEN = new KeyMapping("key.baileys_enchantments.shadow_army",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.baileys_enchantments");

    @Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }
    }

    @Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, value = Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            while (OPEN.consumeClick()) {
                ModNetwork.sendToServer(ArmyActionPayload.of(ArmyActionPayload.Action.OPEN));
            }
        }
    }
}

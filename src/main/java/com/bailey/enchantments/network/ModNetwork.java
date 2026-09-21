package com.bailey.enchantments.network;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.menu.AncientAnvilMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private ModNetwork() {}

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BaileysEnchantments.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register(FMLCommonSetupEvent event) {
        int id = 0;
        CHANNEL.registerMessage(id++,
                RenameAnvilPayload.class,
                RenameAnvilPayload::encode,
                RenameAnvilPayload::decode,
                RenameAnvilPayload::handle);

        CHANNEL.registerMessage(id++,
                SecretRevealPayload.class,
                SecretRevealPayload::encode,
                SecretRevealPayload::decode,
                SecretRevealPayload::handle);
    }

    /** Server to one client. Used for the Secret-name reveal flag. */
    public static void sendToPlayer(Object msg, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void handleRename(RenameAnvilPayload payload, ServerPlayer player) {
        if (player != null && player.containerMenu instanceof AncientAnvilMenu menu) {
            menu.setItemName(payload.name());
        }
    }
}

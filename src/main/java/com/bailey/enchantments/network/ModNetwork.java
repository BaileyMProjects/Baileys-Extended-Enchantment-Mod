package com.bailey.enchantments.network;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.menu.AncientAnvilMenu;
import com.bailey.enchantments.shadow.AriseRitual;
import com.bailey.enchantments.shadow.ShadowClassifier;
import com.bailey.enchantments.shadow.ShadowRemnant;

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

        CHANNEL.registerMessage(id++,
                DissonanceModePayload.class,
                DissonanceModePayload::encode,
                DissonanceModePayload::decode,
                DissonanceModePayload::handle);

        CHANNEL.registerMessage(id++,
                AriseCutscenePayload.class,
                AriseCutscenePayload::encode,
                AriseCutscenePayload::decode,
                AriseCutscenePayload::handle);

        CHANNEL.registerMessage(id++,
                ShadowMarkPayload.class,
                ShadowMarkPayload::encode,
                ShadowMarkPayload::decode,
                ShadowMarkPayload::handle);

        CHANNEL.registerMessage(id++,
                ArmyActionPayload.class,
                ArmyActionPayload::encode,
                ArmyActionPayload::decode,
                ArmyActionPayload::handle);

        CHANNEL.registerMessage(id++,
                ArmyRosterPayload.class,
                ArmyRosterPayload::encode,
                ArmyRosterPayload::decode,
                ArmyRosterPayload::handle);

        // The rite tells its one viewer when to start and how it went.
        AriseRitual.setCutsceneSender(new AriseRitual.CutsceneSender() {
            @Override
            public void begin(ServerPlayer player, ShadowRemnant remnant, ShadowClassifier.Tier tier, int ticks) {
                sendToPlayer(AriseCutscenePayload.begin(remnant.getId(), tier.ordinal(), ticks), player);
            }

            @Override
            public void end(ServerPlayer player, boolean success) {
                sendToPlayer(AriseCutscenePayload.end(success), player);
            }
        });
    }

    /** Client to server. */
    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
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

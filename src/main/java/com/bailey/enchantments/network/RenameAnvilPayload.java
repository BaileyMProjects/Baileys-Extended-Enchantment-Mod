package com.bailey.enchantments.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

// Sent from the Ancient Anvil's rename text box to the server, since our custom
// menu can't piggyback on vanilla's anvil rename packet (which checks for AnvilMenu).
public class RenameAnvilPayload {
    private final String name;

    public RenameAnvilPayload(String name) {
        this.name = name;
    }

    public String name() {
        return this.name;
    }

    public static void encode(RenameAnvilPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name);
    }

    public static RenameAnvilPayload decode(FriendlyByteBuf buf) {
        return new RenameAnvilPayload(buf.readUtf());
    }

    public static void handle(RenameAnvilPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModNetwork.handleRename(msg, ctx.get().getSender()));
        ctx.get().setPacketHandled(true);
    }
}

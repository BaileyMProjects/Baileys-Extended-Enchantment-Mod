package com.bailey.enchantments.network;

import java.util.function.Supplier;

import com.bailey.enchantments.client.DissonanceClient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: the Dissonance mode's state and the anchor of its flash-step zone.
 *
 * <p>⚠️ <b>A packet became unavoidable here.</b> Everything about the mode had been server-side
 * until Bailey asked for the zone to be drawn — <i>"a particle square should be drawn 100x100
 * for the players perspective only"</i>. Only the client can render for one viewer, and it
 * cannot know either the mode state or where the zone was anchored without being told.
 *
 * <p>The anchor is sent as the block position the mode was entered at. It is sent once, on the
 * transition, rather than streamed — the zone never moves, which is the entire point of it.
 */
public class DissonanceModePayload {
    private final boolean active;
    private final int anchorX;
    private final int anchorZ;

    public DissonanceModePayload(boolean active, int anchorX, int anchorZ) {
        this.active = active;
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
    }

    public static void encode(DissonanceModePayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeInt(msg.anchorX);
        buf.writeInt(msg.anchorZ);
    }

    public static DissonanceModePayload decode(FriendlyByteBuf buf) {
        return new DissonanceModePayload(buf.readBoolean(), buf.readInt(), buf.readInt());
    }

    public static void handle(DissonanceModePayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DissonanceClient.set(msg.active, msg.anchorX, msg.anchorZ));
        ctx.get().setPacketHandled(true);
    }
}

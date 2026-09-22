package com.bailey.enchantments.network;

import java.util.function.Supplier;

import com.bailey.enchantments.client.ShadowRenderClient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: "this entity is a shadow". Sent when a player starts tracking one.
 *
 * <p>The mark lives in server-only persistent data, so the client has no other way to know
 * which zombie is a shadow. One varint per shadow per viewer, once; the renderer does the rest.
 */
public class ShadowMarkPayload {
    private final int entityId;

    public ShadowMarkPayload(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(ShadowMarkPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
    }

    public static ShadowMarkPayload decode(FriendlyByteBuf buf) {
        return new ShadowMarkPayload(buf.readVarInt());
    }

    public static void handle(ShadowMarkPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShadowRenderClient.mark(msg.entityId)));
        ctx.get().setPacketHandled(true);
    }
}

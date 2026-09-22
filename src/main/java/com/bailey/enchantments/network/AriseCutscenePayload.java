package com.bailey.enchantments.network;

import java.util.function.Supplier;

import com.bailey.enchantments.client.AriseCutsceneClient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: start or end the Arise cutscene for the one player performing the rite.
 *
 * <p>Only the owner sees the cutscene; other players see the particles and the mob rising. A
 * begin carries the remnant's entity id so the client can lock its camera on it, the tier for
 * the length of the build-up, and the tick count so the client's clock matches the server's roll.
 */
public class AriseCutscenePayload {
    private final boolean begin;
    private final int remnantId;
    private final int tier;
    private final int ticks;
    private final boolean success;

    private AriseCutscenePayload(boolean begin, int remnantId, int tier, int ticks, boolean success) {
        this.begin = begin;
        this.remnantId = remnantId;
        this.tier = tier;
        this.ticks = ticks;
        this.success = success;
    }

    public static AriseCutscenePayload begin(int remnantId, int tier, int ticks) {
        return new AriseCutscenePayload(true, remnantId, tier, ticks, false);
    }

    public static AriseCutscenePayload end(boolean success) {
        return new AriseCutscenePayload(false, 0, 0, 0, success);
    }

    public static void encode(AriseCutscenePayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.begin);
        buf.writeVarInt(msg.remnantId);
        buf.writeByte(msg.tier);
        buf.writeVarInt(msg.ticks);
        buf.writeBoolean(msg.success);
    }

    public static AriseCutscenePayload decode(FriendlyByteBuf buf) {
        return new AriseCutscenePayload(buf.readBoolean(), buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(AriseCutscenePayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (msg.begin) AriseCutsceneClient.begin(msg.remnantId, msg.tier, msg.ticks);
            else AriseCutsceneClient.end(msg.success);
        }));
        ctx.get().setPacketHandled(true);
    }
}

package com.bailey.enchantments.network;

import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.bailey.enchantments.shadow.ShadowArmyMenuServer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → server: one button press on the Shadow Army screen. The server validates everything
 * — that the player has an army, that the soldier is theirs, that the cap holds — and answers
 * with a fresh {@link ArmyRosterPayload}. The client never mutates the roster itself.
 */
public class ArmyActionPayload {
    public enum Action { OPEN, DEPLOY, RECALL, RELEASE, DEPLOY_ALL, RECALL_ALL, STANCE, RENAME }

    private final Action action;
    @Nullable private final UUID soldier;
    private final String text;

    public ArmyActionPayload(Action action, @Nullable UUID soldier, String text) {
        this.action = action;
        this.soldier = soldier;
        this.text = text;
    }

    public static ArmyActionPayload of(Action action) {
        return new ArmyActionPayload(action, null, "");
    }

    public static ArmyActionPayload of(Action action, UUID soldier) {
        return new ArmyActionPayload(action, soldier, "");
    }

    public static void encode(ArmyActionPayload msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeBoolean(msg.soldier != null);
        if (msg.soldier != null) buf.writeUUID(msg.soldier);
        buf.writeUtf(msg.text, 64);
    }

    public static ArmyActionPayload decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID soldier = buf.readBoolean() ? buf.readUUID() : null;
        return new ArmyActionPayload(action, soldier, buf.readUtf(64));
    }

    public static void handle(ArmyActionPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) ShadowArmyMenuServer.handle(player, msg.action, msg.soldier, msg.text);
        });
        ctx.get().setPacketHandled(true);
    }
}

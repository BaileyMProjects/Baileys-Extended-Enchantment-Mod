package com.bailey.enchantments.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.bailey.enchantments.client.ShadowArmyScreen;
import com.bailey.enchantments.shadow.ShadowStance;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: the whole roster, as the screen shows it. Sent on open and after every
 * change. Small enough to resend whole rather than diff — an army is at most a dozen rows.
 */
public class ArmyRosterPayload {
    public record Row(UUID id, String typeId, String name, int slots, int tier,
                      boolean deployed, float health, float maxHealth) {}

    public final boolean open;
    public final int used;
    public final int cap;
    public final ShadowStance stance;
    public final List<Row> rows;

    public ArmyRosterPayload(boolean open, int used, int cap, ShadowStance stance, List<Row> rows) {
        this.open = open;
        this.used = used;
        this.cap = cap;
        this.stance = stance;
        this.rows = rows;
    }

    public static void encode(ArmyRosterPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.open);
        buf.writeVarInt(msg.used);
        buf.writeVarInt(msg.cap);
        buf.writeEnum(msg.stance);
        buf.writeVarInt(msg.rows.size());
        for (Row r : msg.rows) {
            buf.writeUUID(r.id);
            buf.writeUtf(r.typeId);
            buf.writeUtf(r.name, 64);
            buf.writeVarInt(r.slots);
            buf.writeByte(r.tier);
            buf.writeBoolean(r.deployed);
            buf.writeFloat(r.health);
            buf.writeFloat(r.maxHealth);
        }
    }

    public static ArmyRosterPayload decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int used = buf.readVarInt();
        int cap = buf.readVarInt();
        ShadowStance stance = buf.readEnum(ShadowStance.class);
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUUID(), buf.readUtf(), buf.readUtf(64), buf.readVarInt(), buf.readByte(),
                    buf.readBoolean(), buf.readFloat(), buf.readFloat()));
        }
        return new ArmyRosterPayload(open, used, cap, stance, rows);
    }

    public static void handle(ArmyRosterPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ShadowArmyScreen.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}

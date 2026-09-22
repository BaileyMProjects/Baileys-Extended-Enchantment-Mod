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
                      boolean deployed, float health, float maxHealth,
                      int level, int xp, int xpToNext, int intelligence, boolean ready) {}

    public final boolean open;
    public final int used;
    public final int cap;
    public final ShadowStance stance;
    public final List<Row> rows;
    /** The holder's own ladder, which is separate from the blade's roster. */
    public final int level;
    public final int xp;
    public final int xpToNext;
    public final int killsSinceOwn;
    public final int killGate;

    public ArmyRosterPayload(boolean open, int used, int cap, ShadowStance stance, List<Row> rows,
                             int level, int xp, int xpToNext, int killsSinceOwn, int killGate) {
        this.open = open;
        this.used = used;
        this.cap = cap;
        this.stance = stance;
        this.rows = rows;
        this.level = level;
        this.xp = xp;
        this.xpToNext = xpToNext;
        this.killsSinceOwn = killsSinceOwn;
        this.killGate = killGate;
    }

    public static void encode(ArmyRosterPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.open);
        buf.writeVarInt(msg.used);
        buf.writeVarInt(msg.cap);
        buf.writeEnum(msg.stance);
        buf.writeVarInt(msg.level);
        buf.writeVarInt(msg.xp);
        buf.writeVarInt(msg.xpToNext == Integer.MAX_VALUE ? 0 : msg.xpToNext);
        buf.writeVarInt(msg.killsSinceOwn);
        buf.writeVarInt(msg.killGate);
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
            buf.writeVarInt(r.level);
            buf.writeVarInt(r.xp);
            buf.writeVarInt(r.xpToNext == Integer.MAX_VALUE ? 0 : r.xpToNext);
            buf.writeByte(r.intelligence);
            buf.writeBoolean(r.ready);
        }
    }

    public static ArmyRosterPayload decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int used = buf.readVarInt();
        int cap = buf.readVarInt();
        ShadowStance stance = buf.readEnum(ShadowStance.class);
        int level = buf.readVarInt();
        int xp = buf.readVarInt();
        int xpToNext = buf.readVarInt();
        int killsSinceOwn = buf.readVarInt();
        int killGate = buf.readVarInt();
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUUID(), buf.readUtf(), buf.readUtf(64), buf.readVarInt(), buf.readByte(),
                    buf.readBoolean(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readBoolean()));
        }
        return new ArmyRosterPayload(open, used, cap, stance, rows, level, xp, xpToNext, killsSinceOwn, killGate);
    }

    public static void handle(ArmyRosterPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ShadowArmyScreen.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}

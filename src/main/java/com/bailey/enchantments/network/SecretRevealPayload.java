package com.bailey.enchantments.network;

import java.util.function.Supplier;

import com.bailey.enchantments.util.SecretReveal;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: has this player seen what a Secret is called?
 *
 * The truth lives in the player's persisted NBT on the server, but whether to draw the name
 * scrambled is a rendering decision the client has to make every frame. So it is mirrored.
 *
 * ⚠️ Sent on login as well as on the transition. The client's copy is static, so a player who
 * revealed it in one world and then joined another would otherwise carry the reveal with them —
 * which is precisely the per-world rule this whole mechanism exists to enforce.
 */
public class SecretRevealPayload {
    private final boolean revealed;

    public SecretRevealPayload(boolean revealed) {
        this.revealed = revealed;
    }

    public boolean revealed() {
        return this.revealed;
    }

    public static void encode(SecretRevealPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.revealed);
    }

    public static SecretRevealPayload decode(FriendlyByteBuf buf) {
        return new SecretRevealPayload(buf.readBoolean());
    }

    public static void handle(SecretRevealPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> SecretReveal.setClientRevealed(msg.revealed()));
        ctx.get().setPacketHandled(true);
    }
}

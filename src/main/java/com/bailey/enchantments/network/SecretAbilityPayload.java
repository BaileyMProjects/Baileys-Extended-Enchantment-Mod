package com.bailey.enchantments.network;

import java.util.function.Supplier;

import com.bailey.enchantments.event.ImmortalAbility;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → server: a Secret ability fired from its own keybind.
 *
 * <p>⚠️ A keybind, not a right-click, because Bailey asked for Immortal to be <i>"activated like
 * resonance with a different keybind to resonance so it does not overlap"</i>. Armour has no
 * use action of its own, so there is nothing to hang a right-click on anyway.
 *
 * <p>Carries which ability, so a second armour Secret later needs no second packet.
 */
public class SecretAbilityPayload {
    public enum Ability { IMMORTAL }

    private final Ability ability;

    public SecretAbilityPayload(Ability ability) {
        this.ability = ability;
    }

    public static void encode(SecretAbilityPayload msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.ability);
    }

    public static SecretAbilityPayload decode(FriendlyByteBuf buf) {
        return new SecretAbilityPayload(buf.readEnum(Ability.class));
    }

    public static void handle(SecretAbilityPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            switch (msg.ability) {
                case IMMORTAL -> ImmortalAbility.activate(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

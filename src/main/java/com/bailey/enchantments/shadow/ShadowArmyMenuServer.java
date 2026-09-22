package com.bailey.enchantments.shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.bailey.enchantments.network.ArmyActionPayload;
import com.bailey.enchantments.network.ArmyRosterPayload;
import com.bailey.enchantments.network.ModNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The server half of the Shadow Army screen. Every button is a request; this is where it is
 * checked and applied, and the roster sent back so the screen shows what is true rather than
 * what it hoped.
 */
public final class ShadowArmyMenuServer {
    private ShadowArmyMenuServer() {}

    private static final int MAX_NAME = 24;

    public static void handle(ServerPlayer player, ArmyActionPayload.Action action, @Nullable UUID soldierId, String text) {
        ShadowArmies armies = ShadowArmies.get(player.server);

        if (action == ArmyActionPayload.Action.OPEN) {
            boolean allowed = RequiemEvents.hasRequiem(player.getMainHandItem())
                    || RequiemEvents.hasRequiem(player.getOffhandItem())
                    || armies.has(player.getUUID());
            if (!allowed) {
                player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.no_weapon")
                        .withStyle(ChatFormatting.GRAY), true);
                return;
            }
            send(player, true);
            return;
        }

        ShadowArmy army = armies.armyOf(player.getUUID());
        ShadowArmy.Soldier soldier = soldierId != null ? army.find(soldierId) : null;

        switch (action) {
            case DEPLOY -> {
                if (soldier != null && !AriseRitual.isHeld(player)) ShadowArmyManager.deploy(player, army, soldier, null, false);
            }
            case RECALL -> {
                if (soldier != null) ShadowArmyManager.recall(player.server, army, soldier);
            }
            case RELEASE -> {
                if (soldier != null) ShadowArmyManager.release(player.server, player.getUUID(), soldier.id);
            }
            case DEPLOY_ALL -> {
                if (!AriseRitual.isHeld(player)) ShadowArmyManager.deployAll(player);
            }
            case RECALL_ALL -> ShadowArmyManager.recallAll(player.server, player.getUUID());
            case STANCE -> {
                ShadowStance next = army.stance().next();
                army.setStance(next);
                ShadowStances.set(player.getUUID(), next);
                armies.markDirty();
            }
            case RENAME -> {
                if (soldier == null) break;
                String name = text.strip();
                if (name.isEmpty() || name.length() > MAX_NAME) break;
                soldier.name = name;
                armies.markDirty();
                if (ShadowArmyManager.findEntity(player.server, soldier.entityId) instanceof net.minecraft.world.entity.Mob mob) {
                    ShadowArmyManager.applyName(mob, soldier);
                }
            }
            default -> {}
        }
        send(player, false);
    }

    /** The roster as it stands. Health comes from the live body when there is one. */
    public static void send(ServerPlayer player, boolean open) {
        ShadowArmies armies = ShadowArmies.get(player.server);
        ShadowArmy army = armies.armyOf(player.getUUID());
        List<ArmyRosterPayload.Row> rows = new ArrayList<>();
        for (ShadowArmy.Soldier s : army.soldiers()) {
            float health = 0, max = 0;
            if (s.deployed) {
                Entity body = ShadowArmyManager.findEntity(player.server, s.entityId);
                if (body instanceof LivingEntity living) {
                    health = living.getHealth();
                    max = living.getMaxHealth();
                }
            }
            rows.add(new ArmyRosterPayload.Row(s.id, s.typeId, s.name, s.slots, s.tier().ordinal(), s.deployed, health, max));
        }
        ModNetwork.sendToPlayer(new ArmyRosterPayload(open, army.usedSlots(), ShadowBalance.ARMY_CAP, army.stance(), rows), player);
    }
}

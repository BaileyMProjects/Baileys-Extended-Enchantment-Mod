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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * The server half of the Shadow Army screen. Every button is a request; this is where it is
 * checked and applied, and the roster sent back so the screen shows what is true rather than
 * what it hoped.
 *
 * <p>The screen shows <b>the held blade's</b> army. No blade in hand, no screen.
 */
public final class ShadowArmyMenuServer {
    private ShadowArmyMenuServer() {}

    private static final int MAX_NAME = 24;

    public static void handle(ServerPlayer player, ArmyActionPayload.Action action, @Nullable UUID soldierId, String text) {
        ItemStack blade = RequiemBlade.held(player);
        if (blade.isEmpty()) {
            if (action == ArmyActionPayload.Action.OPEN) {
                player.displayClientMessage(Component.translatable("baileys_enchantments.requiem.no_weapon")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        UUID armyId = RequiemBlade.bind(blade, player);
        ShadowArmies armies = ShadowArmies.get(player.server);
        ShadowArmy army = armies.armyOf(armyId);

        if (action == ArmyActionPayload.Action.OPEN) {
            send(player, armyId, true);
            return;
        }

        ShadowArmy.Soldier soldier = soldierId != null ? army.find(soldierId) : null;
        boolean held = AriseRitual.isHeld(player);

        switch (action) {
            case DEPLOY -> {
                if (soldier != null && !held) ShadowArmyManager.deploy(player, armyId, army, soldier, null, false);
            }
            case RECALL -> {
                if (soldier != null) ShadowArmyManager.recall(player.server, army, soldier);
            }
            case RELEASE -> {
                if (soldier != null) ShadowArmyManager.release(player.server, armyId, soldier.id);
            }
            case DEPLOY_ALL -> {
                if (!held) ShadowArmyManager.deployAll(player, armyId);
            }
            case RECALL_ALL -> ShadowArmyManager.recallAll(player.server, armyId);
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
                if (ShadowArmyManager.findEntity(player.server, soldier.entityId) instanceof Mob mob) {
                    ShadowArmyManager.applyName(mob, soldier);
                }
            }
            default -> {}
        }
        send(player, armyId, false);
    }

    /** The roster as it stands. Health comes from the live body when there is one. */
    public static void send(ServerPlayer player, UUID armyId, boolean open) {
        ShadowArmy army = ShadowArmies.get(player.server).armyOf(armyId);
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

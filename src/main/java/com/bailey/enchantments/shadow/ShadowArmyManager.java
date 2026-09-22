package com.bailey.enchantments.shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.bailey.enchantments.shadow.ai.ShadowMovement;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Everything that changes an army: arise, deploy, recall, release, death. The one place the
 * roster and the world are kept in step.
 *
 * <p>⚠️ Every path that touches a soldier's {@code deployed} flag also touches the world, and
 * vice versa. A soldier marked deployed with no body, or a body with no roster entry, is a
 * stray — and {@link RequiemEvents#onJoin} exists to sweep exactly those up on load, because
 * an unloaded chunk can keep a body the recall could not reach.
 */
public final class ShadowArmyManager {
    private ShadowArmyManager() {}

    // ---- arise ----------------------------------------------------------------------------

    /**
     * Bind a snapshot into the roster and put its body in the world at {@code at}. The caller
     * has already checked the fit. Returns the soldier.
     */
    public static ShadowArmy.Soldier arise(ServerPlayer owner, CompoundTag snapshot, int slots,
                                           Vec3 at, boolean rise) {
        MinecraftServer server = owner.server;
        ShadowArmies armies = ShadowArmies.get(server);
        ShadowArmy army = armies.armyOf(owner.getUUID());

        String typeId = snapshot.getString("id");
        String name = EntityType.by(snapshot).map(t -> t.getDescription().getString()).orElse(typeId);
        ShadowArmy.Soldier soldier = new ShadowArmy.Soldier(UUID.randomUUID(), typeId, name, slots, snapshot);
        army.add(soldier);
        armies.markDirty();

        deploy(owner, army, soldier, at, rise);
        return soldier;
    }

    // ---- deploy ----------------------------------------------------------------------------

    /** Put one soldier's body in the world. No-op if it is already out and alive. */
    public static boolean deploy(ServerPlayer owner, ShadowArmy army, ShadowArmy.Soldier soldier,
                                 @Nullable Vec3 at, boolean rise) {
        if (soldier.deployed && findEntity(owner.server, soldier.entityId) != null) return false;

        ServerLevel level = owner.serverLevel();
        Vec3 pos = at != null ? at : owner.position();
        Mob mob = ShadowSpawner.materialise(level, soldier.snapshot, pos);
        if (mob == null) return false;

        ShadowData.markArmy(mob, owner.getUUID(), soldier.id, soldier.slots);
        applyName(mob, soldier);
        ShadowAi.install(mob);

        // ⚠️ Roster BEFORE world. RequiemEvents.onJoin sweeps any body whose roster entry does
        // not already say "deployed, this UUID" -- so if these two lines came after
        // addFreshEntity, every deploy would be discarded as a stray on arrival.
        soldier.deployed = true;
        soldier.entityId = mob.getUUID();
        ShadowArmies.get(owner.server).markDirty();

        level.addFreshEntity(mob);
        if (at == null) ShadowMovement.teleportNear(mob, owner);

        if (rise) ShadowRising.begin(level, mob);
        else ShadowSpawner.puff(level, mob, 10);
        return true;
    }

    public static int deployAll(ServerPlayer owner) {
        ShadowArmy army = ShadowArmies.get(owner.server).armyOf(owner.getUUID());
        int n = 0;
        for (ShadowArmy.Soldier s : new ArrayList<>(army.soldiers())) {
            if (deploy(owner, army, s, null, false)) n++;
        }
        return n;
    }

    // ---- recall ----------------------------------------------------------------------------

    /** Pull one soldier back into storage, unharmed. Safe if the body is already gone. */
    public static void recall(MinecraftServer server, ShadowArmy army, ShadowArmy.Soldier soldier) {
        Entity body = findEntity(server, soldier.entityId);
        if (body != null && body.level() instanceof ServerLevel level) {
            ShadowSpawner.puff(level, body, 10);
            ShadowTeam.leave(body);
            body.discard();
        }
        soldier.deployed = false;
        soldier.entityId = null;
        ShadowArmies.get(server).markDirty();
    }

    public static int recallAll(MinecraftServer server, UUID ownerId) {
        ShadowArmies armies = ShadowArmies.get(server);
        if (!armies.has(ownerId)) return 0;
        ShadowArmy army = armies.armyOf(ownerId);
        int n = 0;
        for (ShadowArmy.Soldier s : army.soldiers()) {
            if (s.deployed) { recall(server, army, s); n++; }
        }
        return n;
    }

    // ---- release and death -----------------------------------------------------------------

    /** Remove a soldier from the roster for good, recalling its body first. */
    public static void release(MinecraftServer server, UUID ownerId, UUID soldierId) {
        ShadowArmies armies = ShadowArmies.get(server);
        ShadowArmy army = armies.armyOf(ownerId);
        ShadowArmy.Soldier s = army.find(soldierId);
        if (s == null) return;
        recall(server, army, s);
        army.remove(soldierId);
        armies.markDirty();
    }

    /** A deployed shadow died in the field. It is gone from the roster; its owner is told. */
    public static void onShadowDied(LivingEntity shadow) {
        UUID rosterId = ShadowData.rosterId(shadow);
        UUID ownerId = ShadowData.ownerId(shadow);
        MinecraftServer server = shadow.getServer();
        if (rosterId == null || ownerId == null || server == null) return;

        ShadowArmies armies = ShadowArmies.get(server);
        if (!armies.has(ownerId)) return;
        ShadowArmy army = armies.armyOf(ownerId);
        ShadowArmy.Soldier s = army.find(rosterId);
        if (s == null) return;
        army.remove(rosterId);
        armies.markDirty();

        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("baileys_enchantments.requiem.fallen", s.name)
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    @Nullable
    public static Entity findEntity(MinecraftServer server, @Nullable UUID id) {
        if (id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    static void applyName(Mob mob, ShadowArmy.Soldier soldier) {
        String base = EntityType.by(soldier.snapshot).map(t -> t.getDescription().getString()).orElse(soldier.typeId);
        if (!soldier.name.equals(base)) {
            mob.setCustomName(Component.literal(soldier.name));
            mob.setCustomNameVisible(false);
        }
    }

    /** Every deployed body of this owner that is loaded right now. */
    public static List<Mob> deployedBodies(MinecraftServer server, UUID ownerId) {
        List<Mob> out = new ArrayList<>();
        ShadowArmies armies = ShadowArmies.get(server);
        if (!armies.has(ownerId)) return out;
        for (ShadowArmy.Soldier s : armies.armyOf(ownerId).soldiers()) {
            if (s.deployed && findEntity(server, s.entityId) instanceof Mob mob) out.add(mob);
        }
        return out;
    }
}

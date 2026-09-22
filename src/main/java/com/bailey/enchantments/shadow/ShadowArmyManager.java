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
 * <p>Armies are addressed by <b>army id</b> — the id in a Requiem blade's NBT
 * ({@link RequiemBlade}). The holder is whoever deployed it; the shadows answer to them until
 * the blade leaves their inventory.
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
     * Bind a snapshot into the blade's roster and put its body in the world at {@code at}. The
     * caller has already checked the fit. Returns the soldier.
     */
    public static ShadowArmy.Soldier arise(ServerPlayer holder, UUID armyId, CompoundTag snapshot, int slots,
                                           Vec3 at, boolean rise) {
        ShadowArmies armies = ShadowArmies.get(holder.server);
        ShadowArmy army = armies.armyOf(armyId);

        String typeId = snapshot.getString("id");
        String name = snapshot.contains("ProfileName") ? snapshot.getString("ProfileName")
                : EntityType.by(snapshot).map(t -> t.getDescription().getString()).orElse(typeId);
        ShadowArmy.Soldier soldier = new ShadowArmy.Soldier(UUID.randomUUID(), typeId, name, slots, snapshot);
        army.add(soldier);
        armies.markDirty();

        deploy(holder, armyId, army, soldier, at, rise);
        return soldier;
    }

    // ---- deploy ----------------------------------------------------------------------------

    /** Put one soldier's body in the world under this holder. No-op if it is already out and alive. */
    public static boolean deploy(ServerPlayer holder, UUID armyId, ShadowArmy army, ShadowArmy.Soldier soldier,
                                 @Nullable Vec3 at, boolean rise) {
        if (soldier.deployed && findEntity(holder.server, soldier.entityId) != null) return false;
        if (!readyToDeploy(soldier)) return false;

        ServerLevel level = holder.serverLevel();
        Vec3 pos = at != null ? at : holder.position();
        Mob mob = ShadowSpawner.materialise(level, soldier.snapshot, pos);
        if (mob == null) return false;

        ShadowData.markArmy(mob, holder.getUUID(), armyId, soldier.id, soldier.slots);
        applyName(mob, soldier);

        // ⚠️ Level BEFORE health: the modifier raises max health, and setting current health
        // first would leave the body looking wounded the moment it appears.
        SoldierLevels.apply(mob, soldier.level);
        if (soldier.storedHealth >= 0) {
            mob.setHealth(Math.min(mob.getMaxHealth(), Math.max(1.0F, soldier.storedHealth)));
        }

        ShadowAi.install(mob);

        // ⚠️ Roster BEFORE world. RequiemEvents.onJoin sweeps any body whose roster entry does
        // not already say "deployed, this UUID" -- so if these lines came after addFreshEntity,
        // every deploy would be discarded as a stray on arrival.
        soldier.deployed = true;
        soldier.entityId = mob.getUUID();
        army.setHolder(holder.getUUID());
        ShadowStances.set(holder.getUUID(), army.stance());
        ShadowArmies.get(holder.server).markDirty();

        level.addFreshEntity(mob);
        if (at == null) ShadowMovement.teleportNear(mob, holder);

        if (rise) ShadowRising.begin(level, mob);
        else ShadowSpawner.puff(level, mob, 10);
        return true;
    }

    /**
     * Field as much of the roster as this holder can carry.
     *
     * <p>⚠️ Two separate limits, and they are not the same thing. <b>Slots</b> belong to the
     * holder's necromancer level, so a novice fielding a veteran's blade gets only part of it.
     * <b>Bodies</b> are a flat ceiling, so forty slots of runts cannot become forty live mobs
     * with AI on a server. Strongest first, so what does fit is the best of it.
     */
    public static int deployAll(ServerPlayer holder, UUID armyId) {
        ShadowArmy army = ShadowArmies.get(holder.server).armyOf(armyId);
        int cap = Necromancer.get(holder.server).slotsOf(holder.getUUID());

        List<ShadowArmy.Soldier> order = new ArrayList<>(army.soldiers());
        order.sort((a, b) -> Integer.compare(b.slots, a.slots));

        int used = 0;
        int bodies = 0;
        int n = 0;
        for (ShadowArmy.Soldier s : order) {
            if (bodies >= ShadowBalance.MAX_DEPLOYED_BODIES) break;
            if (used + s.slots > cap) continue;
            if (deploy(holder, armyId, army, s, null, false)) {
                used += s.slots;
                bodies++;
                n++;
            }
        }
        return n;
    }

    // ---- recall ----------------------------------------------------------------------------

    /** Pull one soldier back into the blade, keeping the wounds it took. */
    public static void recall(MinecraftServer server, ShadowArmy army, ShadowArmy.Soldier soldier) {
        Entity body = findEntity(server, soldier.entityId);
        if (body != null && body.level() instanceof ServerLevel level) {
            // ⚠️ Health is carried, not reset. Recalling a wounded shadow and redeploying it
            // would otherwise be a free full heal, and the downed cooldown would mean nothing.
            if (body instanceof LivingEntity living) {
                soldier.storedHealth = living.getHealth();
                soldier.maxHealth = living.getMaxHealth();
            }
            ShadowSpawner.puff(level, body, 10);
            ShadowTeam.leave(body);
            body.discard();
        }
        soldier.deployed = false;
        soldier.entityId = null;
        if (!army.anyDeployed()) army.setHolder(null);
        ShadowArmies.get(server).markDirty();
    }

    public static int recallAll(MinecraftServer server, UUID armyId) {
        ShadowArmies armies = ShadowArmies.get(server);
        if (!armies.has(armyId)) return 0;
        ShadowArmy army = armies.armyOf(armyId);
        int n = 0;
        for (ShadowArmy.Soldier s : army.soldiers()) {
            if (s.deployed) { recall(server, army, s); n++; }
        }
        army.setHolder(null);
        armies.markDirty();
        return n;
    }

    /** Every army this player has out, back into its blade. Death, logout, dimension change. */
    public static int recallAllHeldBy(MinecraftServer server, UUID player) {
        int n = 0;
        for (UUID armyId : ShadowArmies.get(server).deployedBy(player)) n += recallAll(server, armyId);
        return n;
    }

    // ---- release and death -----------------------------------------------------------------

    /** Remove a soldier from the roster for good, recalling its body first. */
    public static void release(MinecraftServer server, UUID armyId, UUID soldierId) {
        ShadowArmies armies = ShadowArmies.get(server);
        ShadowArmy army = armies.armyOf(armyId);
        ShadowArmy.Soldier s = army.find(soldierId);
        if (s == null) return;
        recall(server, army, s);
        army.remove(soldierId);
        armies.markDirty();
    }

    /**
     * A deployed shadow fell. ✅ <b>It is DOWNED, not destroyed</b> — Bailey, 2026-09-22: <i>"when
     * your soldier dies it just goes back into your army and doesnt dissapear but they just sit
     * at 0 health and slowly regenerate."</i> It keeps its level; only the body needs mending.
     */
    public static void onShadowDied(LivingEntity shadow) {
        UUID rosterId = ShadowData.rosterId(shadow);
        UUID armyId = ShadowData.armyId(shadow);
        UUID ownerId = ShadowData.ownerId(shadow);
        MinecraftServer server = shadow.getServer();
        if (rosterId == null || armyId == null || server == null) return;

        ShadowArmies armies = ShadowArmies.get(server);
        if (!armies.has(armyId)) return;
        ShadowArmy army = armies.armyOf(armyId);
        ShadowArmy.Soldier s = army.find(rosterId);
        if (s == null) return;
        s.deployed = false;
        s.entityId = null;
        s.storedHealth = 0.0F;
        s.maxHealth = shadow.getMaxHealth();
        if (!army.anyDeployed()) army.setHolder(null);
        armies.markDirty();

        ServerPlayer owner = ownerId != null ? server.getPlayerList().getPlayer(ownerId) : null;
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("baileys_enchantments.requiem.fallen", s.name)
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    /**
     * Can this soldier take the field? A downed one must mend first.
     *
     * <p>⚠️ Compared against the body's max health at its CURRENT level, which the roster does
     * not know — so the fraction is judged against the stored value alone, treating -1 (never
     * fought) as full. Good enough: what matters is that 0 blocks and a healed soldier does not.
     */
    public static boolean readyToDeploy(ShadowArmy.Soldier soldier) {
        return soldier.storedHealth < 0 || soldier.storedHealth >= soldier.maxHealthEstimate() * ShadowBalance.DEPLOYABLE_AT;
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

    /** Unused now that armies are blade-bound; kept for the report and debugging. */
    public static List<Mob> deployedBodies(MinecraftServer server, UUID armyId) {
        List<Mob> out = new ArrayList<>();
        ShadowArmies armies = ShadowArmies.get(server);
        if (!armies.has(armyId)) return out;
        for (ShadowArmy.Soldier s : armies.armyOf(armyId).soldiers()) {
            if (s.deployed && findEntity(server, s.entityId) instanceof Mob mob) out.add(mob);
        }
        return out;
    }
}

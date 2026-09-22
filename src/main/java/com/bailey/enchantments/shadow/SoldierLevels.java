package com.bailey.enchantments.shadow;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/**
 * Each shadow's own ladder, and the intelligence that comes with it.
 *
 * <p>Bailey, 2026-09-22: <i>"When you first obtain a shadow its lvl 1 and has no sort of
 * intelligence and just blindly kills but as it gains xp and levels you up as a necromancer it
 * also levels itself up and gains more intelligence with how it fights and its stats."</i>
 *
 * <p>A soldier climbs the same curve as its master, from its <b>own</b> kills, and keeps its
 * level through being downed — the body mends, the soldier is not re-earned.
 */
public final class SoldierLevels {
    private SoldierLevels() {}

    private static final UUID HEALTH_ID = UUID.fromString("1d9f3c2e-58a7-4b16-9c04-7e2f5a8d3b61");
    private static final UUID DAMAGE_ID = UUID.fromString("4a71e6b8-2c39-4d05-8f6a-19b3e7d24c85");

    // ---- earning ----------------------------------------------------------------------------

    /** Award a deployed shadow XP for its own kill, and announce a level. */
    public static void award(MinecraftServer server, Entity body, int amount) {
        UUID armyId = ShadowData.armyId(body);
        UUID rosterId = ShadowData.rosterId(body);
        if (armyId == null || rosterId == null) return;                 // an Umbra clone

        ShadowArmies armies = ShadowArmies.get(server);
        if (!armies.has(armyId)) return;
        ShadowArmy army = armies.armyOf(armyId);
        ShadowArmy.Soldier soldier = army.find(rosterId);
        if (soldier == null || soldier.level >= ShadowBalance.MAX_NECROMANCER_LEVEL) return;

        soldier.xp += amount;
        boolean levelled = false;
        while (soldier.level < ShadowBalance.MAX_NECROMANCER_LEVEL) {
            int need = Necromancer.xpToNext(soldier.level);
            if (soldier.xp < need) break;
            soldier.xp -= need;
            soldier.level++;
            levelled = true;
        }
        armies.markDirty();

        if (!levelled) return;
        if (body instanceof Mob mob) apply(mob, soldier.level);

        UUID ownerId = ShadowData.ownerId(body);
        ServerPlayer owner = ownerId != null ? server.getPlayerList().getPlayer(ownerId) : null;
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("baileys_enchantments.necro.soldier_level",
                    soldier.name, soldier.level, intelligenceOf(body, soldier.level))
                    .withStyle(ChatFormatting.DARK_AQUA), true);
            if (body.level() instanceof ServerLevel level) {
                level.playSound(null, body.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.HOSTILE, 1.0F, 1.6F);
            }
        }
    }

    // ---- stats ------------------------------------------------------------------------------

    /**
     * Put a soldier's level onto its body. Idempotent: the old modifiers go first, so this can
     * run on every deploy and on every level without stacking.
     *
     * <p>⚠️ Health is raised <b>before</b> anything sets the body's current health, or the mob
     * spawns at its old maximum and looks wounded.
     */
    public static void apply(Mob mob, int level) {
        float bonus = Math.max(0, level - 1);

        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(HEALTH_ID);
            if (bonus > 0) {
                health.addPermanentModifier(new AttributeModifier(HEALTH_ID, "Shadow level",
                        bonus * ShadowBalance.SOLDIER_HEALTH_PER_LEVEL, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }

        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(DAMAGE_ID);
            if (bonus > 0) {
                damage.addPermanentModifier(new AttributeModifier(DAMAGE_ID, "Shadow level",
                        bonus * ShadowBalance.SOLDIER_DAMAGE_PER_LEVEL, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
    }

    // ---- intelligence -------------------------------------------------------------------------

    /**
     * How well this shadow fights, 0..{@link ShadowBalance#MAX_INTELLIGENCE}.
     *
     * <p>Bailey: <i>"some sort of intelligence based on what the mob is and then that scales."</i>
     * So it is <b>species plus level</b>: a raised player starts clever and ends cleverer than a
     * raised cow ever will, and the species part costs nothing to work out — it falls out of the
     * entity's class, exactly like the classifier's role does.
     */
    public static int intelligenceOf(Entity body, int level) {
        int base = baseIntelligence(body);
        int fromLevel = level * ShadowBalance.MAX_INTELLIGENCE / ShadowBalance.MAX_NECROMANCER_LEVEL;
        return Math.min(ShadowBalance.MAX_INTELLIGENCE, base + fromLevel);
    }

    /** Intelligence at level 1, by what the thing is. */
    public static int baseIntelligence(Entity body) {
        if (body instanceof ShadowPlayer) return 2;
        if (body instanceof AbstractIllager || body instanceof AbstractPiglin) return 1;
        if (body instanceof Monster) return 0;
        if (body instanceof Animal) return 0;
        return 0;
    }

    /** The live intelligence of a deployed shadow, read by the AI. */
    public static int intelligenceOf(Mob mob) {
        MinecraftServer server = mob.getServer();
        if (server == null) return baseIntelligence(mob);
        return intelligenceOf(mob, levelOf(server, mob));
    }

    /** The roster level of a deployed shadow, or 1 for an Umbra clone. */
    public static int levelOf(MinecraftServer server, Entity body) {
        ShadowArmy.Soldier soldier = soldierOf(server, body);
        return soldier == null ? 1 : soldier.level;
    }

    @Nullable
    public static ShadowArmy.Soldier soldierOf(MinecraftServer server, Entity body) {
        UUID armyId = ShadowData.armyId(body);
        UUID rosterId = ShadowData.rosterId(body);
        if (armyId == null || rosterId == null) return null;
        ShadowArmies armies = ShadowArmies.get(server);
        return armies.has(armyId) ? armies.armyOf(armyId).find(rosterId) : null;
    }

    /** Convenience for goals, which hold a living entity rather than a server. */
    public static int intelligenceOf(LivingEntity entity) {
        return entity instanceof Mob mob ? intelligenceOf(mob) : 0;
    }
}

package com.bailey.enchantments.shadow.ai;

import java.util.EnumSet;

import com.bailey.enchantments.shadow.ShadowAi;
import com.bailey.enchantments.shadow.ShadowData;
import com.bailey.enchantments.shadow.SoldierLevels;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Applies {@link ShadowTargeting} every ten ticks. The rules live there so bodies whose goals
 * never tick can share them; this is only the goal-selector wrapper.
 */
public class ShadowTargetGoal extends Goal {
    /**
     * ⚠️ Reaction time IS intelligence, rung 1. A dull shadow looks at the battlefield every ten
     * ticks and is half a second behind everything; a clever one is nearly instant.
     */
    private static int intervalFor(int intelligence) {
        return Math.max(2, 10 - intelligence * 2);
    }

    private final Mob mob;
    private int timer;

    public ShadowTargetGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return ShadowData.owner(mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        int intelligence = SoldierLevels.intelligenceOf(mob);
        if (++timer < intervalFor(intelligence)) return;
        timer = 0;

        Player owner = ShadowData.owner(mob);
        if (owner == null) return;

        LivingEntity current = mob.getTarget();
        LivingEntity next = ShadowTargeting.choose(mob, owner, current, intelligence);
        if (next != current) ShadowAi.setTarget(mob, next);
    }
}

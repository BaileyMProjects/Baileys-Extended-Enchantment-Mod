package com.bailey.enchantments.shadow;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Umbra's proc: a kill has a chance to raise a fleeting shadow of the victim.
 *
 * <p>Bailey, 2026-09-22: <i>"the umbra should just have a chance to clone a mob temporarily
 * and thats all."</i> No storage, no controls, no ritual. It happens or it does not.
 *
 * <p>⚠️ Requiem keeps the proc, on the same principle as Resonance keeping Execute's perks:
 * it is gambled from Umbra and destroys it, so winning must never lose a perk. A clone is not
 * stored and costs no slots, so it does not interact with the army.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class UmbraEvents {
    private UmbraEvents() {}

    public static boolean hasUmbraPerk(ItemStack weapon) {
        return Ultimates.hasUltimate(weapon, ModEnchantments.UMBRA.get())
                || Ultimates.hasUltimate(weapon, ModEnchantments.REQUIEM.get());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (!hasUmbraPerk(killer.getMainHandItem())) return;

        if (ShadowData.isShadow(victim)) return;                 // a shadow leaves no shadow
        if (!(victim instanceof Mob)) return;                     // players: Phase 6
        if (!ShadowClassifier.isRaisable(victim)) return;
        if (ShadowEvents.temporaryCount(killer.getUUID()) >= ShadowBalance.UMBRA_MAX_CLONES) return;
        if (level.random.nextFloat() >= ShadowBalance.UMBRA_CLONE_CHANCE) return;

        CompoundTag snapshot = ShadowSpawner.snapshot(victim);
        Mob clone = ShadowSpawner.materialise(level, snapshot, victim.position());
        if (clone == null) return;

        ShadowData.markTemporary(clone, killer.getUUID(), level.getGameTime() + ShadowBalance.UMBRA_CLONE_TICKS);
        ShadowAi.install(clone);
        level.addFreshEntity(clone);
        ShadowEvents.trackTemporary(clone);
        ShadowSpawner.puff(level, clone, 16);
    }
}

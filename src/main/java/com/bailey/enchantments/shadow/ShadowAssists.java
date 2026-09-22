package com.bailey.enchantments.shadow;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Who a shadow has hurt lately, so an army is paid for the fights it actually won.
 *
 * <h2>🔴 Why this exists</h2>
 * Bailey, 2026-09-22: a raised Medusa <i>"turns everything to stone, which not only makes u
 * unable to level up"</i>. The army only earned when a shadow struck the <b>killing blow</b>, so
 * a shadow whose whole contribution is disabling things — or whose victim dies to the fall, the
 * fire, the poison it applied, or another shadow — earned nothing at all.
 *
 * <p>An assist window fixes the general case, not just Medusa's: if a shadow of yours damaged
 * the victim in the last few seconds, that kill feeds the ladder even if something else landed
 * the last hit.
 *
 * <p>⚠️ The entry is keyed by the <b>victim</b> and holds the <b>owner</b>, not the shadow. The
 * shadow may well be dead by the time the victim falls, and the credit belongs to the
 * necromancer either way.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class ShadowAssists {
    private ShadowAssists() {}

    /** How long after a shadow's hit a kill still counts as theirs. */
    private static final long WINDOW = 200L;

    /** The owner owed the credit, and the shadow that earned it — both level from the kill. */
    public record Assist(UUID owner, UUID shadow, long at) {}

    private static final Map<UUID, Assist> RECENT = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        Entity attacker = event.getSource().getEntity();
        if (attacker == null || !ShadowData.isShadow(attacker)) return;
        UUID owner = ShadowData.ownerId(attacker);
        if (owner == null) return;

        RECENT.put(victim.getUUID(), new Assist(owner, attacker.getUUID(), victim.level().getGameTime()));
    }

    /** The credit owed for this kill, if a shadow softened it up. Claimed once, then gone. */
    @Nullable
    public static Assist claim(LivingEntity victim) {
        Assist assist = RECENT.remove(victim.getUUID());
        if (assist == null) return null;
        return victim.level().getGameTime() - assist.at() <= WINDOW ? assist : null;
    }

    /**
     * ⚠️ Swept, because most entries are never claimed: a shadow hits a dozen things it does not
     * kill, and nothing would ever remove those. Every ten seconds, over a map that is small by
     * construction.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RECENT.isEmpty()) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 200 != 0) return;

        long now = server.overworld().getGameTime();
        Iterator<Assist> it = RECENT.values().iterator();
        while (it.hasNext()) {
            if (now - it.next().at() > WINDOW) it.remove();
        }
    }
}

package com.bailey.enchantments.client;

import java.util.HashSet;
import java.util.Set;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws shadows black and blue.
 *
 * <p>Bailey, 2026-09-22: <i>"they all turn black and blue in the show... Just switch the colours
 * when they are summoned as a shadow."</i>
 *
 * <p>How: the normal render is cancelled and the mob's <b>own</b> renderer is run again through
 * a {@link ShadowTintBuffer}. Because it is the mob's own renderer, it works for anything —
 * modded, GeckoLib, the dragon, the player shadow — with no per-mob art. The blue rim comes from
 * the vanilla glow outline, which the server gives shadows via a scoreboard team colour, and the
 * drift of soul particles is added here per viewer.
 *
 * <p>⚠️ <b>The recursion trap.</b> Re-running the renderer fires this same event again. A guard
 * flag lets the nested call through untouched. A mod that also cancels this event could collide;
 * that is on the playtest list, not solved here.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, value = Dist.CLIENT)
public final class ShadowRenderClient {
    private ShadowRenderClient() {}

    private static final Set<Integer> SHADOWS = new HashSet<>();
    private static boolean rendering;

    /** Set by the army screen while it draws a roster preview, so the preview is tinted too. */
    public static boolean previewTint;

    public static void mark(int entityId) {
        SHADOWS.add(entityId);
    }

    public static boolean isShadow(Entity entity) {
        return SHADOWS.contains(entity.getId());
    }

    // ---- render ------------------------------------------------------------------------------

    @SubscribeEvent
    public static <T extends LivingEntity, M extends EntityModel<T>> void onRenderPre(RenderLivingEvent.Pre<T, M> event) {
        if (rendering) return;
        LivingEntity entity = event.getEntity();
        if (!previewTint && !SHADOWS.contains(entity.getId())) return;

        LivingEntityRenderer<T, M> renderer = event.getRenderer();
        @SuppressWarnings("unchecked")
        T typed = (T) entity;
        float yaw = Mth.lerp(event.getPartialTick(), entity.yRotO, entity.getYRot());

        event.setCanceled(true);
        rendering = true;
        try {
            renderer.render(typed, yaw, event.getPartialTick(), event.getPoseStack(),
                    new ShadowTintBuffer(event.getMultiBufferSource()), event.getPackedLight());
        } finally {
            rendering = false;
        }
    }

    // ---- particles and housekeeping ------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SHADOWS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;

        if (mc.level.getGameTime() % 3 != 0) return;
        for (int id : SHADOWS) {
            Entity e = mc.level.getEntity(id);
            if (e == null || !e.isAlive()) continue;
            if (mc.level.random.nextInt(3) != 0) continue;
            double x = e.getX() + (mc.level.random.nextDouble() - 0.5) * e.getBbWidth();
            double y = e.getY() + mc.level.random.nextDouble() * e.getBbHeight();
            double z = e.getZ() + (mc.level.random.nextDouble() - 0.5) * e.getBbWidth();
            mc.level.addParticle(ParticleTypes.SCULK_SOUL, x, y, z, 0, 0.01, 0);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) SHADOWS.remove(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) SHADOWS.clear();
    }
}

package com.bailey.enchantments.client;

import java.util.HashSet;
import java.util.Set;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.client.model.EntityModel;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
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
 * <p>The normal render is cancelled and the mob's <b>own</b> renderer is run twice:
 * <ol>
 *   <li>through a {@link ShadowTintBuffer} — a near-black navy body with a rim light computed
 *       from the surface normal;
 *   <li>through a {@link ShadowShellBuffer} at {@link #SHELL_SCALE} — a solid light-blue halo,
 *       emissive and blended, whose brightness owes nothing to the texture.
 * </ol>
 * Because it is the mob's own renderer both times, it works on any body with no per-mob art.
 * The soul aura around them is sent by the server, so every player sees it.
 *
 * <p>🔶 <b>GeckoLib is unverified.</b> This hooks {@code RenderLivingEvent}, which only
 * {@code LivingEntityRenderer} fires. GeckoLib 4's {@code GeoEntityRenderer} extends
 * {@code EntityRenderer} directly, so a GeckoLib mob may never reach this code and would render
 * untouched. Needs a pack with one in it to find out.
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

    /** How much larger the halo is than the body. Small: it is a glow, not a second mob. */
    private static final float SHELL_SCALE = 1.06F;

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

        PoseStack pose = event.getPoseStack();
        event.setCanceled(true);
        rendering = true;
        try {
            renderer.render(typed, yaw, event.getPartialTick(), pose,
                    new ShadowTintBuffer(event.getMultiBufferSource()), event.getPackedLight());
            shell(renderer, typed, yaw, event.getPartialTick(), pose, event.getMultiBufferSource());
        } finally {
            rendering = false;
        }
    }

    /**
     * The halo: the same model again, a little larger, in solid light blue.
     *
     * <p>⚠️ Scaled about the entity's <b>middle</b>, not its origin. The origin is at the feet,
     * so a plain scale would leave the halo standing in the ground and floating above the head.
     *
     * <p>Skipped for the preview in the army screen — a halo drawn inside a 36-pixel box is a
     * blue smear — and for an invisible entity, which should stay invisible.
     */
    private static <T extends LivingEntity, M extends EntityModel<T>> void shell(
            LivingEntityRenderer<T, M> renderer, T entity, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers) {
        if (previewTint || entity.isInvisible()) return;
        ResourceLocation texture = renderer.getTextureLocation(entity);
        if (texture == null) return;

        float mid = entity.getBbHeight() * 0.5F;
        pose.pushPose();
        pose.translate(0.0F, mid, 0.0F);
        pose.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        pose.translate(0.0F, -mid, 0.0F);
        try {
            renderer.render(entity, yaw, partialTick, pose,
                    new ShadowShellBuffer(buffers, texture), LightTexture.FULL_BRIGHT);
        } catch (RuntimeException e) {
            // A renderer that cannot survive being run twice loses only its halo, not its body.
            BaileysEnchantments.LOGGER.warn("Shadow halo failed for {}: {}", entity.getType(), e.toString());
        } finally {
            pose.popPose();
        }
    }

    // ---- housekeeping ----------------------------------------------------------------------
    //
    // ⚠️ The soul particles used to be spawned here, per viewer. They are now sent by the
    // SERVER (ShadowEvents.onServerTick) so everybody sees them, not only clients that have
    // received the mark packet. Bailey, 2026-09-22: "These are things that everyone should see."


    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) SHADOWS.remove(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) SHADOWS.clear();
    }
}

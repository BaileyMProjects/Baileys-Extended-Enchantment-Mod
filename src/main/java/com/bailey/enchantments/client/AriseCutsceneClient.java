package com.bailey.enchantments.client;

import com.bailey.enchantments.BaileysEnchantments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Arise cutscene, for the one player performing the rite.
 *
 * <p>What was promised, and no more: letterbox bars, a dimmed world, the camera turned onto the
 * remnant, the word ARISE fading in, a shake on the heartbeat, and a flash on the outcome. The
 * vanilla camera can be locked and shaken; it cannot be dollied, and this does not pretend to.
 *
 * <p>The server owns the clock and the dice. This only mirrors the countdown so the visuals
 * land on the same tick as the roll, and it ends when told — a late or lost packet can leave it
 * running at most until its own countdown plus a short grace expires, never forever.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID, value = Dist.CLIENT)
public final class AriseCutsceneClient {
    private AriseCutsceneClient() {}

    private static final int SHADOW_BLUE = 0x6A9CFF;
    private static final int SHADOW_DEEP = 0x101A33;
    private static final int FAIL_RED = 0xA0202A;
    private static final int RESULT_TICKS = 30;
    private static final int GRACE_TICKS = 40;

    private enum Phase { OFF, RUNNING, RESULT }

    private static Phase phase = Phase.OFF;
    private static int remnantId;
    private static int total;
    private static int elapsed;
    private static int resultTick;
    private static boolean success;

    public static void begin(int entityId, int tier, int ticks) {
        remnantId = entityId;
        total = Math.max(1, ticks);
        elapsed = 0;
        phase = Phase.RUNNING;
    }

    public static void end(boolean won) {
        if (phase == Phase.OFF) return;
        success = won;
        resultTick = 0;
        phase = Phase.RESULT;
    }

    public static boolean isActive() {
        return phase != Phase.OFF;
    }

    // ---- clock and camera ---------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == Phase.OFF) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) { phase = Phase.OFF; return; }

        if (phase == Phase.RUNNING) {
            elapsed++;
            if (elapsed > total + GRACE_TICKS) phase = Phase.OFF;   // the server never answered
            turnToward(player, mc.level.getEntity(remnantId));
        } else {
            resultTick++;
            if (resultTick > RESULT_TICKS) phase = Phase.OFF;
        }
    }

    /**
     * Turn the player, not just the camera, so the view does not snap when the scene ends. The
     * turn eases in over the first second rather than jumping.
     */
    private static void turnToward(LocalPlayer player, Entity remnant) {
        if (remnant == null) return;
        Vec3 eye = player.getEyePosition();
        Vec3 to = remnant.position().add(0, 0.6, 0).subtract(eye);
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        float wantYaw = (float) (Mth.atan2(to.z, to.x) * (180.0 / Math.PI)) - 90.0F;
        float wantPitch = (float) -(Mth.atan2(to.y, flat) * (180.0 / Math.PI));

        float ease = Math.min(1.0F, elapsed / 20.0F) * 0.35F;
        player.setYRot(player.getYRot() + Mth.wrapDegrees(wantYaw - player.getYRot()) * ease);
        player.setXRot(player.getXRot() + (wantPitch - player.getXRot()) * ease);
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (phase != Phase.RUNNING) return;
        // Shake on the beat: a short jolt every heartbeat, harder as the reveal nears.
        float progress = (float) elapsed / total;
        int beat = elapsed > total - 30 ? 10 : 20;
        int sinceBeat = elapsed % beat;
        if (sinceBeat < 4) {
            float amp = (0.3F + progress * 0.9F) * (1.0F - sinceBeat / 4.0F);
            double t = event.getPartialTick() + elapsed;
            event.setYaw(event.getYaw() + (float) Math.sin(t * 9.1) * amp);
            event.setPitch(event.getPitch() + (float) Math.cos(t * 7.3) * amp * 0.6F);
        }
    }

    // ---- overlay ------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (phase == Phase.OFF) return;
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float pt = event.getPartialTick();

        float open;      // 0..1 how far the bars are in
        float word;      // 0..1 how visible ARISE is
        float flash = 0; // 0..1 result flash
        int wordColor = SHADOW_BLUE;

        if (phase == Phase.RUNNING) {
            float t = (elapsed + pt);
            open = Mth.clamp(t / 12.0F, 0, 1);
            float progress = t / total;
            word = Mth.clamp((progress - 0.25F) / 0.5F, 0, 1);
        } else {
            float t = resultTick + pt;
            open = 1.0F - Mth.clamp((t - 10) / (RESULT_TICKS - 10), 0, 1);
            if (success) {
                flash = Mth.clamp(1.0F - t / 14.0F, 0, 1);
                word = Mth.clamp(1.0F - (t - 8) / 14.0F, 0, 1);
                wordColor = 0xEAF2FF;
            } else {
                word = Mth.clamp(1.0F - t / 10.0F, 0, 1);
                wordColor = FAIL_RED;
            }
        }

        // Dim the world in shadow-deep, then the bars.
        int dimAlpha = (int) (open * 0x70);
        g.fill(0, 0, w, h, (dimAlpha << 24) | SHADOW_DEEP);
        int bar = (int) (h * 0.13F * easeOut(open));
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);

        if (flash > 0) {
            g.fill(0, 0, w, h, ((int) (flash * 0xB0) << 24) | SHADOW_BLUE);
        }

        if (word > 0.02F) {
            int alpha = Math.max(4, (int) (word * 255));
            String text = "ARISE";
            float scale = 4.0F + (phase == Phase.RESULT && success ? 1.0F : 0.0F);
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, h * 0.42F, 0);
            g.pose().scale(scale, scale, 1);
            int tw = mc.font.width(text);
            // A soft glow behind the word: the same string, offset and dimmer.
            int glow = ((alpha / 3) << 24) | (wordColor & 0xFFFFFF);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.drawString(mc.font, text, -tw / 2 + dx, -4 + dy, glow, false);
            }
            g.drawString(mc.font, text, -tw / 2, -4, (alpha << 24) | (wordColor & 0xFFFFFF), true);
            g.pose().popPose();
        }
    }

    private static float easeOut(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }
}

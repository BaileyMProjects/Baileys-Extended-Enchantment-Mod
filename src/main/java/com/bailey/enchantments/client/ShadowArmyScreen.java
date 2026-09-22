package com.bailey.enchantments.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.bailey.enchantments.network.ArmyActionPayload;
import com.bailey.enchantments.network.ArmyRosterPayload;
import com.bailey.enchantments.network.ModNetwork;
import com.bailey.enchantments.shadow.ShadowPlayer;
import com.bailey.enchantments.shadow.ShadowStance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Shadow Army screen: the roster, what is out, and the controls to manage it.
 *
 * <p>Bailey: <i>"a refined and aesthetic/functioning looking menu for all of this to be easily
 * controlled. As the army needs to be manipulated and remove soldiers ect ect."</i>
 *
 * <p>Drawn flat, in the deepslate-and-shadow-blue of this system rather than vanilla's grey
 * buttons, so it reads as part of the Deep Dark set. Nothing here changes the roster: every
 * control sends a request and the server answers with the roster as it now stands.
 *
 * <p>Previews are bare client-side entities of each soldier's type, rendered through the same
 * shadow tint as the real thing. Variant details (a slime's size, a villager's job) are not
 * carried to the preview — the snapshot stays on the server — which is a deliberate trade
 * against sending every soldier's NBT to the client each time the screen opens.
 */
public class ShadowArmyScreen extends Screen {

    // ---- palette ---------------------------------------------------------------------------
    private static final int BG = 0xF00C1016;
    private static final int PANEL = 0xFF141A24;
    private static final int PANEL_EDGE = 0xFF2A3A5A;
    private static final int ROW = 0xFF1A2230;
    private static final int ROW_HOVER = 0xFF202A3C;
    private static final int ACCENT = 0xFF6A9CFF;
    private static final int ACCENT_DIM = 0xFF33507F;
    private static final int TEXT = 0xFFF0F4FF;
    private static final int TEXT_DIM = 0xFF9AA4B8;
    private static final int DANGER = 0xFFB0303A;
    private static final int DANGER_DIM = 0xFF5A1E24;
    private static final int OK = 0xFF4CC38A;

    // ---- geometry --------------------------------------------------------------------------
    private static final int W = 276;
    private static final int H = 214;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 30;
    private static final int ROW_H = 44;
    private static final int PREVIEW = 36;
    private static final int BTN_W = 54;
    private static final int BTN_H = 13;

    private static ShadowArmyScreen open;
    private static ArmyRosterPayload latest;

    private int left, top;
    private int scroll;
    private final List<ArmyRosterPayload.Row> rows = new ArrayList<>();
    private int used, cap;
    private int level = 1, xp, xpToNext, killsSinceOwn, killGate = 10;
    private ShadowStance stance = ShadowStance.GUARD;

    @Nullable private UUID confirmRelease;
    private long confirmUntil;
    @Nullable private UUID renaming;
    @Nullable private EditBox nameBox;

    private final Map<String, LivingEntity> previews = new HashMap<>();

    public ShadowArmyScreen() {
        super(Component.translatable("baileys_enchantments.army.title"));
    }

    // ---- receiving --------------------------------------------------------------------------

    /** Called from the network thread's enqueued work. Opens or refreshes. */
    public static void receive(ArmyRosterPayload payload) {
        latest = payload;
        Minecraft mc = Minecraft.getInstance();
        if (payload.open && !(mc.screen instanceof ShadowArmyScreen)) {
            mc.setScreen(new ShadowArmyScreen());
        }
        if (open != null) open.apply(payload);
    }

    private void apply(ArmyRosterPayload payload) {
        rows.clear();
        rows.addAll(payload.rows);
        used = payload.used;
        cap = payload.cap;
        stance = payload.stance;
        level = payload.level;
        xp = payload.xp;
        xpToNext = payload.xpToNext;
        killsSinceOwn = payload.killsSinceOwn;
        killGate = payload.killGate;
        scroll = Mth.clamp(scroll, 0, maxScroll());
        if (renaming != null && rows.stream().noneMatch(r -> r.id().equals(renaming))) stopRename(false);
    }

    @Override
    protected void init() {
        open = this;
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (latest != null) apply(latest);
    }

    @Override
    public void removed() {
        if (open == this) open = null;
        previews.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Health bars go stale otherwise; a refresh is one small packet a second. */
    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.level != null && minecraft.level.getGameTime() % 20 == 0) {
            send(ArmyActionPayload.of(ArmyActionPayload.Action.OPEN));
        }
    }

    // ---- layout helpers --------------------------------------------------------------------

    private int listTop() { return top + HEADER_H; }
    private int listBottom() { return top + H - FOOTER_H; }
    private int listHeight() { return listBottom() - listTop(); }
    private int maxScroll() { return Math.max(0, rows.size() * ROW_H - listHeight()); }

    private int rowY(int index) { return listTop() + index * ROW_H - scroll; }

    private int deployBtnX() { return left + W - 8 - BTN_W; }
    private int releaseBtnX() { return left + W - 8 - BTN_W; }
    private int deployBtnY(int rowY) { return rowY + 6; }
    private int releaseBtnY(int rowY) { return rowY + 6 + BTN_H + 4; }

    private int footerY() { return top + H - FOOTER_H + 8; }
    private int deployAllX() { return left + 8; }
    private int recallAllX() { return left + 8 + BTN_W + 6; }
    private int stanceX() { return left + W - 8 - 84; }

    // ---- render ------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, PANEL_EDGE);
        g.fill(left, top, left + W, top + H, PANEL);

        renderHeader(g);
        renderList(g, mouseX, mouseY);
        renderFooter(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, BG);
    }

    private void renderHeader(GuiGraphics g) {
        int x = left + 8;
        int y = top + 8;
        g.drawString(font, Component.translatable("baileys_enchantments.army.title").getString(), x, y, ACCENT, true);
        String slots = used + " / " + cap;
        g.drawString(font, slots, left + W - 8 - font.width(slots), y, used >= cap ? DANGER : TEXT, true);

        // The slot bar: one segment per slot, lit for what is used.
        // ⚠️ Segments get thin past ~30 slots, so below a minimum width it draws as one solid
        // bar instead. A 40-slot army would otherwise be a row of single-pixel slivers.
        int barX = x, barY = y + 13, barW = W - 16, segGap = 2;
        int segW = (barW - segGap * (cap - 1)) / Math.max(1, cap);
        if (segW >= 3) {
            for (int i = 0; i < cap; i++) {
                int sx = barX + i * (segW + segGap);
                g.fill(sx, barY, sx + segW, barY + 5, i < used ? ACCENT : ACCENT_DIM);
            }
        } else {
            g.fill(barX, barY, barX + barW, barY + 5, ACCENT_DIM);
            g.fill(barX, barY, barX + barW * Math.min(used, cap) / Math.max(1, cap), barY + 5, ACCENT);
        }

        // The necromancer's own ladder, which belongs to the player rather than the blade.
        int ly = y + 21;
        String lvl = "Lv " + level + (level >= 50 ? " MAX" : "");
        g.drawString(font, lvl, x, ly, level >= 50 ? OK : ACCENT, false);

        if (level < 50 && xpToNext > 0) {
            int xbX = x + font.width(lvl) + 6;
            int xbW = 96;
            g.fill(xbX, ly + 2, xbX + xbW, ly + 5, ACCENT_DIM);
            g.fill(xbX, ly + 2, xbX + xbW * Math.min(xp, xpToNext) / xpToNext, ly + 5, ACCENT);
        }

        // ⚠️ The AFK gate has to be visible, or an army that has quietly stopped earning looks
        // like a bug rather than a rule.
        String gate = killsSinceOwn >= killGate
                ? Component.translatable("baileys_enchantments.army.gate_shut").getString()
                : (killGate - killsSinceOwn) + Component.translatable("baileys_enchantments.army.gate_left").getString();
        g.drawString(font, gate, left + W - 8 - font.width(gate), ly, killsSinceOwn >= killGate ? DANGER : TEXT_DIM, false);

        g.fill(left, top + HEADER_H - 1, left + W, top + HEADER_H, PANEL_EDGE);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(left, listTop(), left + W, listBottom());
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("baileys_enchantments.army.empty"),
                    left + W / 2, listTop() + listHeight() / 2 - 4, TEXT_DIM);
        }
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (y + ROW_H < listTop() || y > listBottom()) continue;
            renderRow(g, rows.get(i), y, mouseX, mouseY);
        }
        g.disableScissor();

        if (maxScroll() > 0) {
            int trackX = left + W - 3;
            int trackH = listHeight();
            int thumbH = Math.max(10, trackH * listHeight() / (rows.size() * ROW_H));
            int thumbY = listTop() + (trackH - thumbH) * scroll / maxScroll();
            g.fill(trackX, listTop(), trackX + 2, listBottom(), ACCENT_DIM);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, ACCENT);
        }
    }

    private void renderRow(GuiGraphics g, ArmyRosterPayload.Row row, int y, int mouseX, int mouseY) {
        boolean hover = mouseX >= left && mouseX < left + W && mouseY >= y && mouseY < y + ROW_H
                && mouseY >= listTop() && mouseY < listBottom();
        g.fill(left + 4, y + 2, left + W - 6, y + ROW_H - 2, hover ? ROW_HOVER : ROW);
        g.fill(left + 4, y + 2, left + 6, y + ROW_H - 2, row.deployed() ? ACCENT : ACCENT_DIM);

        // Preview
        int px = left + 12, py = y + 4;
        g.fill(px, py, px + PREVIEW, py + PREVIEW, PANEL);
        LivingEntity preview = preview(row);
        if (preview != null) {
            int scale = (int) Math.max(4, Math.min(16, 26 / Math.max(1.0F, preview.getBbHeight())));
            g.enableScissor(px, py, px + PREVIEW, py + PREVIEW);
            ShadowRenderClient.previewTint = true;
            try {
                InventoryScreen.renderEntityInInventoryFollowsAngle(g, px + PREVIEW / 2, py + PREVIEW - 4, scale, -0.2F, 0.15F, preview);
            } finally {
                ShadowRenderClient.previewTint = false;
            }
            g.disableScissor();
        }

        // Text
        int tx = px + PREVIEW + 8;
        if (renaming == null || !renaming.equals(row.id())) {
            g.drawString(font, row.name(), tx, y + 6, TEXT, true);
        }
        String tier = "Lv " + row.level() + "  " + tierName(row.tier()) + " 00b7 " + row.slots()
                + (row.slots() == 1 ? " slot" : " slots");
        g.drawString(font, tier, tx, y + 17, TEXT_DIM, false);

        // Intelligence as pips, so "this one has learned things" reads without a number.
        int pipX = left + W - 8 - BTN_W - 6 - 5 * 5;
        for (int i = 0; i < 5; i++) {
            g.fill(pipX + i * 5, y + 18, pipX + i * 5 + 3, y + 21, i < row.intelligence() ? ACCENT : ACCENT_DIM);
        }

        if (row.deployed()) {
            g.drawString(font, "●", tx, y + 28, OK, false);
            String hp = (int) Math.ceil(row.health()) + "/" + (int) Math.ceil(row.maxHealth());
            g.drawString(font, Component.translatable("baileys_enchantments.army.deployed").getString() + "  " + hp, tx + 8, y + 28, TEXT_DIM, false);
            int barW = 70, barX = tx;
            float f = row.maxHealth() > 0 ? Mth.clamp(row.health() / row.maxHealth(), 0, 1) : 0;
            g.fill(barX, y + 37, barX + barW, y + 39, ACCENT_DIM);
            g.fill(barX, y + 37, barX + (int) (barW * f), y + 39, f > 0.3F ? ACCENT : DANGER);
        } else if (row.maxHealth() > 0 && row.health() < row.maxHealth()) {
            // Mending in the blade. Bailey wanted a Pokemon-style cooldown rather than a loss.
            boolean down = row.health() <= 0.01F;
            g.drawString(font, down ? "2716" : "25d1", tx, y + 28, down ? DANGER : TEXT_DIM, false);
            String label = Component.translatable(down ? "baileys_enchantments.army.downed"
                    : "baileys_enchantments.army.mending").getString();
            g.drawString(font, label + "  " + (int) (100 * row.health() / row.maxHealth()) + "%",
                    tx + 8, y + 28, row.ready() ? TEXT_DIM : DANGER, false);
            int barW = 70, barX = tx;
            float f = Mth.clamp(row.health() / row.maxHealth(), 0, 1);
            g.fill(barX, y + 37, barX + barW, y + 39, ACCENT_DIM);
            g.fill(barX, y + 37, barX + (int) (barW * f), y + 39, row.ready() ? ACCENT : DANGER);
        } else {
            g.drawString(font, "25cb", tx, y + 28, TEXT_DIM, false);
            g.drawString(font, Component.translatable("baileys_enchantments.army.stored").getString(), tx + 8, y + 28, TEXT_DIM, false);
        }

        // Buttons
        boolean confirming = confirmRelease != null && confirmRelease.equals(row.id()) && System.currentTimeMillis() < confirmUntil;
        button(g, deployBtnX(), deployBtnY(y), BTN_W, BTN_H,
                Component.translatable(row.deployed() ? "baileys_enchantments.army.recall" : "baileys_enchantments.army.deploy").getString(),
                mouseX, mouseY, ACCENT_DIM, ACCENT, TEXT);
        button(g, releaseBtnX(), releaseBtnY(y), BTN_W, BTN_H,
                Component.translatable(confirming ? "baileys_enchantments.army.sure" : "baileys_enchantments.army.release").getString(),
                mouseX, mouseY, confirming ? DANGER : DANGER_DIM, DANGER, TEXT);
    }

    private void renderFooter(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(left, top + H - FOOTER_H, left + W, top + H - FOOTER_H + 1, PANEL_EDGE);
        int y = footerY();
        button(g, deployAllX(), y, BTN_W, BTN_H, Component.translatable("baileys_enchantments.army.deploy_all").getString(), mouseX, mouseY, ACCENT_DIM, ACCENT, TEXT);
        button(g, recallAllX(), y, BTN_W, BTN_H, Component.translatable("baileys_enchantments.army.recall_all").getString(), mouseX, mouseY, ACCENT_DIM, ACCENT, TEXT);

        String stanceLabel = Component.translatable("baileys_enchantments.army.stance").getString() + ": "
                + Component.translatable("baileys_enchantments.army.stance." + stance.name().toLowerCase()).getString() + " ▸";
        button(g, stanceX(), y, 84, BTN_H, stanceLabel, mouseX, mouseY, ROW, ACCENT_DIM, stance == ShadowStance.AGGRESSIVE ? DANGER : stance == ShadowStance.PASSIVE ? TEXT_DIM : ACCENT);

        if (hit(mouseX, mouseY, stanceX(), y, 84, BTN_H)) {
            List<Component> tip = List.of(Component.translatable("baileys_enchantments.army.stance." + stance.name().toLowerCase() + ".tip"));
            g.renderTooltip(font, tip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int mouseX, int mouseY,
                        int fill, int hoverEdge, int textColor) {
        boolean hover = hit(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, fill);
        if (hover) {
            g.fill(x, y, x + w, y + 1, hoverEdge);
            g.fill(x, y + h - 1, x + w, y + h, hoverEdge);
        }
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String tierName(int tier) {
        return switch (tier) {
            case 1 -> "Soldier";
            case 2 -> "Elite";
            case 3 -> "Monarch";
            default -> "Runt";
        };
    }

    // ---- previews ----------------------------------------------------------------------------

    @Nullable
    private LivingEntity preview(ArmyRosterPayload.Row row) {
        String key = row.typeId() + "|" + row.name();
        if (previews.containsKey(key)) return previews.get(key);

        LivingEntity made = null;
        if (minecraft != null && minecraft.level != null) {
            ResourceLocation id = ResourceLocation.tryParse(row.typeId());
            EntityType<?> type = id != null ? ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
            if (type != null) {
                try {
                    Entity e = type.create(minecraft.level);
                    if (e instanceof LivingEntity living) {
                        if (living instanceof ShadowPlayer sp) sp.setProfileForPreview(row.name());
                        made = living;
                    } else if (e != null) {
                        e.discard();
                    }
                } catch (RuntimeException ignored) {
                    // A modded entity that cannot be built client-side simply has no preview.
                }
            }
        }
        previews.put(key, made);
        return made;
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (nameBox != null && nameBox.mouseClicked(mx, my, button)) return true;
        if (button != 0) return super.mouseClicked(mx, my, button);
        int x = (int) mx, y = (int) my;

        if (renaming != null && (nameBox == null || !nameBox.isMouseOver(mx, my))) stopRename(true);

        int fy = footerY();
        if (hit(x, y, deployAllX(), fy, BTN_W, BTN_H)) { send(ArmyActionPayload.of(ArmyActionPayload.Action.DEPLOY_ALL)); return true; }
        if (hit(x, y, recallAllX(), fy, BTN_W, BTN_H)) { send(ArmyActionPayload.of(ArmyActionPayload.Action.RECALL_ALL)); return true; }
        if (hit(x, y, stanceX(), fy, 84, BTN_H)) { send(ArmyActionPayload.of(ArmyActionPayload.Action.STANCE)); return true; }

        if (y >= listTop() && y < listBottom()) {
            for (int i = 0; i < rows.size(); i++) {
                int ry = rowY(i);
                if (y < ry || y >= ry + ROW_H) continue;
                ArmyRosterPayload.Row row = rows.get(i);

                if (hit(x, y, deployBtnX(), deployBtnY(ry), BTN_W, BTN_H)) {
                    send(ArmyActionPayload.of(row.deployed() ? ArmyActionPayload.Action.RECALL : ArmyActionPayload.Action.DEPLOY, row.id()));
                    return true;
                }
                if (hit(x, y, releaseBtnX(), releaseBtnY(ry), BTN_W, BTN_H)) {
                    boolean confirming = confirmRelease != null && confirmRelease.equals(row.id()) && System.currentTimeMillis() < confirmUntil;
                    if (confirming) {
                        confirmRelease = null;
                        send(ArmyActionPayload.of(ArmyActionPayload.Action.RELEASE, row.id()));
                    } else {
                        confirmRelease = row.id();
                        confirmUntil = System.currentTimeMillis() + 2500;
                    }
                    return true;
                }
                int nameX = left + 12 + PREVIEW + 8;
                if (hit(x, y, nameX, ry + 4, 110, 12)) {
                    startRename(row, nameX, ry + 4);
                    return true;
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) (delta * 18), 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (nameBox != null) {
            if (key == 257 || key == 335) { stopRename(true); return true; }   // enter
            if (key == 256) { stopRename(false); return true; }                // escape
            if (nameBox.keyPressed(key, scan, mods)) return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (nameBox != null && nameBox.charTyped(c, mods)) return true;
        return super.charTyped(c, mods);
    }

    private void startRename(ArmyRosterPayload.Row row, int x, int y) {
        stopRename(false);
        renaming = row.id();
        nameBox = new EditBox(font, x - 2, y - 2, 112, 12, Component.literal(row.name()));
        nameBox.setBordered(false);
        nameBox.setMaxLength(24);
        nameBox.setTextColor(TEXT);
        nameBox.setValue(row.name());
        addRenderableWidget(nameBox);
        setFocused(nameBox);
        nameBox.setFocused(true);
    }

    private void stopRename(boolean commit) {
        if (nameBox != null && renaming != null && commit) {
            String value = nameBox.getValue().strip();
            if (!value.isEmpty()) send(new ArmyActionPayload(ArmyActionPayload.Action.RENAME, renaming, value));
        }
        if (nameBox != null) removeWidget(nameBox);
        nameBox = null;
        renaming = null;
    }

    private static void send(ArmyActionPayload payload) {
        ModNetwork.sendToServer(payload);
    }
}

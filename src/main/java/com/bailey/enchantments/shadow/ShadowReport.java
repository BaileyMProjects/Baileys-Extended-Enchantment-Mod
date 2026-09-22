package com.bailey.enchantments.shadow;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bailey.enchantments.BaileysEnchantments;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Reads the pack maker's overrides and writes the classification of every entity type in the
 * pack to {@code config/baileys_enchantments/shadow_report.json} at server start.
 *
 * <p>The report is the "every single type" guarantee made visible: open it and see the cost,
 * tier, role and deciding rule for everything loaded, including modded types. Sorted most
 * expensive first so the bosses are at the top.
 *
 * <p>⚠️ Server start, not mod load, because tags — including {@code #unraisable} — are not
 * bound until then, and the report must reflect them.
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class ShadowReport {
    private ShadowReport() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve(BaileysEnchantments.MODID);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            Files.createDirectories(dir());
            ShadowClassifier.setConfiguredOverrides(loadOverrides());
            writeReport(event.getServer());
        } catch (IOException e) {
            BaileysEnchantments.LOGGER.warn("Shadow report could not be written: {}", e.toString());
        }
    }

    /**
     * Rewritten on every world save as well, because the interesting half is now <b>observed</b>
     * and observation accrues while you play. A report frozen at server start would show none of
     * the learning that made a mob expensive.
     */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        try {
            writeReport(event.getServer());
        } catch (IOException e) {
            BaileysEnchantments.LOGGER.warn("Shadow report could not be rewritten: {}", e.toString());
        }
    }

    /**
     * {@code shadow_overrides.json}: {@code {"modid:entity": slots}}. Keys beginning with an
     * underscore are documentation and ignored. Written with an example on first run.
     */
    private static Map<ResourceLocation, Integer> loadOverrides() throws IOException {
        Path file = dir().resolve("shadow_overrides.json");
        Map<ResourceLocation, Integer> out = new HashMap<>();

        if (!Files.exists(file)) {
            JsonObject example = new JsonObject();
            example.addProperty("_about", "Pin the slot cost of any entity type. Keys starting with _ are ignored. "
                    + "Costs are clamped to " + ShadowBalance.MIN_SLOTS + ".." + ShadowBalance.MAX_SLOTS + ". "
                    + "See shadow_report.json for what the formula decided.");
            example.addProperty("_example", "\"somemod:big_boss\": 9");
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(example, w);
            }
            return out;
        }

        try (Reader r = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(r);
            if (!root.isJsonObject()) return out;
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                if (entry.getKey().startsWith("_")) continue;
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null || !entry.getValue().isJsonPrimitive()) {
                    BaileysEnchantments.LOGGER.warn("shadow_overrides.json: ignoring '{}'", entry.getKey());
                    continue;
                }
                try {
                    out.put(id, entry.getValue().getAsInt());
                } catch (RuntimeException e) {
                    BaileysEnchantments.LOGGER.warn("shadow_overrides.json: '{}' is not a number", entry.getKey());
                }
            }
        } catch (RuntimeException e) {
            BaileysEnchantments.LOGGER.warn("shadow_overrides.json could not be parsed: {}", e.toString());
        }
        return out;
    }

    private static void writeReport(net.minecraft.server.MinecraftServer server) throws IOException {
        ShadowObservations seen = ShadowObservations.get(server);
        List<ShadowClassifier.Classification> rows = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            ShadowClassifier.Classification c = ShadowClassifier.classify(type);
            if (c.role() == ShadowClassifier.Role.NOT_LIVING) continue;
            rows.add(c);
        }
        rows.sort(Comparator.comparingInt(ShadowClassifier.Classification::slots).reversed()
                .thenComparing(c -> c.id().toString()));

        JsonObject root = new JsonObject();
        root.addProperty("_observation", "An 'observed' block means this mob has been watched doing "
                + "something the formula cannot read — applying effects, or hitting harder than it declares. "
                + "Those raise its cost. Disagree? Pin the number in shadow_overrides.json.");
        root.addProperty("_about", "Base-value classification of every living entity type loaded. "
                + "At kill time the actual entity's health, damage and armour are used instead, so a "
                + "scaled or geared instance may cost more than listed here. Pin a cost in shadow_overrides.json.");
        root.addProperty("_formula", "threat = health / (1 - 0.04 * min(armor, 20)) * (1 + damage / "
                + (int) ShadowBalance.DAMAGE_WEIGHT + "); slots = max(1, round(log2(threat / "
                + (int) ShadowBalance.ONE_SLOT_THREAT + ")) + 1)");
        root.addProperty("_army_slots_at_level_1", ShadowBalance.BASE_ARMY_SLOTS);
        root.addProperty("_army_slots_at_max_level", ShadowBalance.MAX_ARMY_SLOTS);
        root.addProperty("_living_types", rows.size());

        JsonObject entries = new JsonObject();
        for (ShadowClassifier.Classification c : rows) {
            JsonObject o = new JsonObject();
            o.addProperty("slots", c.slots());
            o.addProperty("tier", c.tier().label());
            o.addProperty("role", c.role().label);
            o.addProperty("raisable", c.raisable());
            o.addProperty("health", round(c.health()));
            o.addProperty("damage", round(c.damage()));
            o.addProperty("armor", round(c.armor()));
            o.addProperty("threat", round(c.threat()));
            o.addProperty("decided_by", c.decidedBy());

            // ⚠️ What the mob has been SEEN to do, which is the half a formula cannot read.
            // This is where a four-slot Medusa becomes visible as a mistake: the effects are
            // listed, the multiplier is shown, and shadow_overrides.json is one line away.
            net.minecraft.world.entity.EntityType<?> t =
                    net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(c.id());
            if (t != null) {
                ShadowObservations.Record record = seen.of(t);
                if (!record.effects.isEmpty() || record.maxDamage > 0) {
                    JsonObject obs = new JsonObject();
                    if (record.maxDamage > 0) obs.addProperty("hardest_hit_seen", round(record.maxDamage));
                    if (!record.effects.isEmpty()) {
                        com.google.gson.JsonArray effects = new com.google.gson.JsonArray();
                        record.effects.stream().sorted().forEach(effects::add);
                        obs.add("effects_applied", effects);
                        obs.addProperty("extra_slots", seen.extraSlots(t));
                    }
                    o.add("observed", obs);
                }
            }
            entries.add(c.id().toString(), o);
        }
        root.add("types", entries);

        try (Writer w = Files.newBufferedWriter(dir().resolve("shadow_report.json"))) {
            GSON.toJson(root, w);
        }
        BaileysEnchantments.LOGGER.info("Shadow report: classified {} living entity types", rows.size());
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}

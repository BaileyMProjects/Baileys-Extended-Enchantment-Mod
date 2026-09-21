package com.bailey.enchantments.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// Sculk sensors used as "bookshelves" for the Ancient Enchanting Table sit right next to it,
// so the table's own activity constantly triggers them. Once a sensor is in that shelf position
// it gets force-deactivated and any vibration near it is swallowed before it can re-trigger.
public class SculkMuting {
    private static final double HEARING_RANGE_SQ = 8.0 * 8.0;

    private static final Map<Level, Map<BlockPos, Set<BlockPos>>> MUTED_BY_TABLE = new WeakHashMap<>();

    private SculkMuting() {}

    public static void updateMutedSensors(Level level, BlockPos tablePos) {
        Set<BlockPos> sensors = new HashSet<>();
        // Walks the same volume the table scores, but deliberately does NOT check for
        // power. Since 2026-08-08 only corrupted bookshelves provide power, so a power
        // check would mute nothing at all — and every published world still has sculk
        // sensors sitting in these positions from when they were the power source. They
        // would start shrieking at the table's own activity, which is the exact problem
        // this class exists to prevent. Proximity is what matters here, not power.
        for (BlockPos offset : EnchantPower.offsets()) {
            BlockPos shelfPos = tablePos.offset(offset);
            BlockState shelfState = level.getBlockState(shelfPos);
            if (shelfState.is(Blocks.SCULK_SENSOR) || shelfState.is(Blocks.CALIBRATED_SCULK_SENSOR)) {
                sensors.add(shelfPos.immutable());
                if (SculkSensorBlock.getPhase(shelfState) != SculkSensorPhase.INACTIVE) {
                    SculkSensorBlock.deactivate(level, shelfPos, shelfState);
                }
            }
        }

        Map<BlockPos, Set<BlockPos>> levelMap = MUTED_BY_TABLE.computeIfAbsent(level, l -> new HashMap<>());
        if (sensors.isEmpty()) {
            levelMap.remove(tablePos);
        } else {
            levelMap.put(tablePos.immutable(), sensors);
        }
    }

    public static void clearTable(Level level, BlockPos tablePos) {
        Map<BlockPos, Set<BlockPos>> levelMap = MUTED_BY_TABLE.get(level);
        if (levelMap != null) levelMap.remove(tablePos);
    }

    public static boolean isNearMutedSensor(Level level, BlockPos pos) {
        Map<BlockPos, Set<BlockPos>> levelMap = MUTED_BY_TABLE.get(level);
        if (levelMap == null || levelMap.isEmpty()) return false;
        for (Set<BlockPos> sensors : levelMap.values()) {
            for (BlockPos sensorPos : sensors) {
                if (sensorPos.distSqr(pos) <= HEARING_RANGE_SQ) return true;
            }
        }
        return false;
    }
}

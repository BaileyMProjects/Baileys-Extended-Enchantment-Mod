package com.bailey.enchantments.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

// The Ancient Enchanting Table's structure scan. Replaces vanilla's fixed
// EnchantmentTableBlock.BOOKSHELF_OFFSETS so the table can be built out as a real
// structure instead of a single ring of shelves.
//
// Shared by the table (power), SculkMuting (which sensors to silence) and the
// Ancient Anvil (XP scaling), so all three agree on what counts as "part of the build".
//
// ⚠️ CORRUPTED BOOKSHELVES ARE THE ONLY POWER SOURCE. Bailey's call, 2026-08-08. Sculk
// blocks powered the table on every published build and no longer count for anything.
// This is a deliberate break: existing tables drop to 0 power and keep only row 1 until
// the player crafts corrupted bookshelves.
//
// The volume still contains vanilla's ring (x/z at distance 2, y 0..1) as a strict subset,
// so position geometry is backwards compatible even though block types are not — an old
// structure's shape still scores, its blocks just aren't providers any more. Do not narrow
// this to the distance-3 ring alone.
public final class EnchantPower {
    // Horizontal ring, inclusive. 2 is vanilla's distance; 3 is the new outer ring.
    public static final int INNER_RADIUS = 2;
    public static final int OUTER_RADIUS = 3;
    // Vertical extent, relative to the table: 0..3, i.e. shelves may stack 4 high.
    public static final int LAYERS = 4;

    // The old maximum a vanilla-shaped setup could reach, and where the curve tops out.
    // Everything that scales with the structure keys off powerFactor, so a table sitting
    // at or below BASE_POWER behaves exactly as it did before the rework.
    public static final int BASE_POWER = 15;
    public static final int POWER_SOFT_CAP = 60;

    // One provider, so one weight. The tiered weights that existed while sculk and vanilla
    // bookshelves also counted are gone: with a single block type a weight above 1 would
    // only divide the ROW_POWER thresholds, making row 3 reachable with 5 shelves instead
    // of 15. At weight 1 the thresholds read directly as a shelf count.
    private static final int WEIGHT_PROVIDER = 1;

    private static List<BlockPos> OFFSETS = null;

    private EnchantPower() {}

    // Every candidate position, table-relative. ~160 of them, so iterate this only on
    // slotsChanged / menu open — never every tick.
    public static List<BlockPos> offsets() {
        if (OFFSETS != null) return OFFSETS;
        List<BlockPos> list = new ArrayList<>();
        for (int y = 0; y < LAYERS; y++) {
            for (int x = -OUTER_RADIUS; x <= OUTER_RADIUS; x++) {
                for (int z = -OUTER_RADIUS; z <= OUTER_RADIUS; z++) {
                    int ring = Math.max(Math.abs(x), Math.abs(z));
                    if (ring < INNER_RADIUS) continue;
                    list.add(new BlockPos(x, y, z));
                }
            }
        }
        OFFSETS = List.copyOf(list);
        return OFFSETS;
    }

    // 0 at or below BASE_POWER, rising to 1 at POWER_SOFT_CAP. Lives here rather than in
    // either menu so the table's roll quality and the anvil's XP costs scale on the SAME
    // curve — two copies would drift the moment one is tuned.
    public static float powerFactor(int power) {
        if (power <= BASE_POWER) return 0.0F;
        if (power >= POWER_SOFT_CAP) return 1.0F;
        return (float) (power - BASE_POWER) / (POWER_SOFT_CAP - BASE_POWER);
    }

    // Total structure power at a table. 0 for an unbuilt table.
    public static int totalPower(Level level, BlockPos tablePos) {
        int power = 0;
        for (BlockPos offset : offsets()) {
            power += powerAt(level, tablePos, offset);
        }
        return power;
    }

    // What one position contributes: its weight if it holds a power block and nothing
    // blocks the path to it, otherwise 0.
    public static int powerAt(Level level, BlockPos tablePos, BlockPos offset) {
        int weight = weightOf(level.getBlockState(tablePos.offset(offset)));
        if (weight == 0) return 0;
        return hasLineOfSight(level, tablePos, offset) ? weight : 0;
    }

    // Tag-driven rather than hardcoded to the block, so a datapack can still add providers
    // — including putting sculk back — without a code change.
    private static int weightOf(BlockState state) {
        return state.is(ModTags.Blocks.ENCHANTMENT_POWER_PROVIDER) ? WEIGHT_PROVIDER : 0;
    }

    // Vanilla only ever checks a single midpoint, which is enough at distance 2. At
    // distance 3 there are TWO intermediate blocks and both must be clear, or a shelf
    // behind a wall counts.
    //
    // Integer division truncates, which reproduces vanilla's (x / 2, y, z / 2) exactly
    // for the distance-2 ring — deliberately, so existing setups behave identically.
    private static boolean hasLineOfSight(Level level, BlockPos tablePos, BlockPos offset) {
        int steps = Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ()));
        for (int step = 1; step < steps; step++) {
            BlockPos between = tablePos.offset(
                    offset.getX() * step / steps,
                    offset.getY(),
                    offset.getZ() * step / steps);
            if (!level.getBlockState(between).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)) return false;
        }
        return true;
    }
}

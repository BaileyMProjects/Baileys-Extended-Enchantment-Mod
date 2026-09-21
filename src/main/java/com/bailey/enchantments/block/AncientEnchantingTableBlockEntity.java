package com.bailey.enchantments.block;

import com.bailey.enchantments.util.SculkMuting;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AncientEnchantingTableBlockEntity extends BlockEntity {
    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    public float tRot;
    private static final RandomSource RANDOM = RandomSource.create();

    public AncientEnchantingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANCIENT_ENCHANTING_TABLE.get(), pos, state);
    }

    public static void bookAnimationTick(Level level, BlockPos pos, BlockState state,
                                         AncientEnchantingTableBlockEntity entity) {
        entity.oOpen = entity.open;
        entity.oRot = entity.rot;
        Player player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0, false);
        if (player != null) {
            double d0 = player.getX() - (pos.getX() + 0.5);
            double d1 = player.getZ() - (pos.getZ() + 0.5);
            entity.tRot = (float) Mth.atan2(d1, d0);
            entity.open += 0.1F;
            if (entity.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float f1 = entity.flipT;
                do {
                    entity.flipT = entity.flipT + (RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while (f1 == entity.flipT);
            }
        } else {
            entity.tRot += 0.02F;
            entity.open -= 0.1F;
        }

        while (entity.rot >= (float) Math.PI) entity.rot -= (float) (Math.PI * 2);
        while (entity.rot < (float) -Math.PI) entity.rot += (float) (Math.PI * 2);
        while (entity.tRot >= (float) Math.PI) entity.tRot -= (float) (Math.PI * 2);
        while (entity.tRot < (float) -Math.PI) entity.tRot += (float) (Math.PI * 2);

        float f2 = entity.tRot - entity.rot;
        while (f2 >= (float) Math.PI) f2 -= (float) (Math.PI * 2);
        while (f2 < (float) -Math.PI) f2 += (float) (Math.PI * 2);

        entity.rot += f2 * 0.4F;
        entity.open = Mth.clamp(entity.open, 0.0F, 1.0F);
        entity.time++;
        entity.oFlip = entity.flip;
        float f = (entity.flipT - entity.flip) * 0.4F;
        f = Mth.clamp(f, -0.2F, 0.2F);
        entity.flipA = entity.flipA + (f - entity.flipA) * 0.9F;
        entity.flip = entity.flip + entity.flipA;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   AncientEnchantingTableBlockEntity entity) {
        if (level.getGameTime() % 20 == 0) {
            SculkMuting.updateMutedSensors(level, pos);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null) {
            SculkMuting.clearTable(this.level, this.worldPosition);
        }
    }
}

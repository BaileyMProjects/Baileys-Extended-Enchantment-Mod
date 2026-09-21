package com.bailey.enchantments.block;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.menu.AncientEnchantmentMenu;
import com.bailey.enchantments.util.EnchantPower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

import java.util.List;

public class AncientEnchantingTableBlock extends BaseEntityBlock {
    public static final Component CONTAINER_TITLE =
            Component.translatable("container." + BaileysEnchantments.MODID + ".ancient_enchant");
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    public AncientEnchantingTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AncientEnchantingTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, ModBlockEntities.ANCIENT_ENCHANTING_TABLE.get(),
                        AncientEnchantingTableBlockEntity::bookAnimationTick)
                : createTickerHelper(type, ModBlockEntities.ANCIENT_ENCHANTING_TABLE.get(),
                        AncientEnchantingTableBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // Kept as a thin delegate so callers that only care "is there a shelf here" don't
    // have to think about weights. The volume and line-of-sight rules live in one place
    // now → EnchantPower.
    public static boolean isValidSculkShelf(Level level, BlockPos tablePos, BlockPos offset) {
        return EnchantPower.powerAt(level, tablePos, offset) > 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        // ⚠️ Sample, don't sweep. The scan volume is ~160 positions and this runs every
        // client tick per table; walking all of it would be a per-frame block lookup
        // storm. Four random probes give roughly vanilla's particle density.
        List<BlockPos> offsets = EnchantPower.offsets();
        for (int i = 0; i < 4; i++) {
            BlockPos offset = offsets.get(random.nextInt(offsets.size()));
            if (EnchantPower.powerAt(level, pos, offset) > 0) {
                level.addParticle(ParticleTypes.ENCHANT,
                        pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5,
                        offset.getX() + random.nextFloat() - 0.5,
                        offset.getY() - random.nextFloat() - 1.0F,
                        offset.getZ() + random.nextFloat() - 0.5);
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(this.getMenuProvider(state, level, pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (id, inventory, player) -> new AncientEnchantmentMenu(id, inventory,
                        ContainerLevelAccess.create(level, pos)),
                CONTAINER_TITLE);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }
}

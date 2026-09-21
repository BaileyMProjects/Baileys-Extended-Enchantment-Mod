package com.bailey.enchantments.block;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.menu.AncientAnvilMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class AncientAnvilBlock extends HorizontalDirectionalBlock {
    public static final Component CONTAINER_TITLE =
            Component.translatable("container." + BaileysEnchantments.MODID + ".ancient_anvil");

    // Shapes for N/S (z-axis) and E/W (x-axis) orientations
    private static final VoxelShape SHAPE_NS = Shapes.or(
            Block.box(2.0, 0.0, 0.0, 14.0, 4.0, 16.0),
            Block.box(4.0, 4.0, 3.0, 12.0, 5.0, 13.0),
            Block.box(3.0, 5.0, 4.0, 13.0, 10.0, 12.0),
            Block.box(2.0, 10.0, 0.0, 14.0, 16.0, 16.0));

    private static final VoxelShape SHAPE_EW = Shapes.or(
            Block.box(0.0, 0.0, 2.0, 16.0, 4.0, 14.0),
            Block.box(3.0, 4.0, 4.0, 13.0, 5.0, 12.0),
            Block.box(4.0, 5.0, 3.0, 12.0, 10.0, 13.0),
            Block.box(0.0, 10.0, 2.0, 16.0, 16.0, 14.0));

    public AncientAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(this.getMenuProvider(state, level, pos));
            player.awardStat(Stats.INTERACT_WITH_ANVIL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (id, inventory, player) -> new AncientAnvilMenu(id, inventory,
                        ContainerLevelAccess.create(level, pos)),
                CONTAINER_TITLE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.75;
        double y = pos.getY() + 1.0625;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.75;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SCULK_CHARGE_POP, x, y, z, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(15) == 0) {
            double sx = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
            double sz = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
            level.addParticle(ParticleTypes.SCULK_SOUL, sx, y, sz, 0.0, 0.05, 0.0);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}

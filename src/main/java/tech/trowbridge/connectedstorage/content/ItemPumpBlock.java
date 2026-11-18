package tech.trowbridge.connectedstorage.content;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import tech.trowbridge.connectedstorage.index.AddonBlockEntityTypes;
import tech.trowbridge.connectedstorage.index.AddonTags;
import tech.trowbridge.connectedstorage.link.LinkNetworks;

/**
 * Powers a storage network. Shaped and driven like Create's Mechanical Pump (a small cog, powered by meshing
 * with an adjacent cogwheel); connects to storages, Item Pipes and other pumps on the two ends of
 * its axis. Storages link through running pumps and the pipes within their reach: one pipe block per
 * {@link ItemPumpBlockEntity#RPM_PER_BLOCK} RPM, up to {@link ItemPumpBlockEntity#MAX_REACH}.
 */
public class ItemPumpBlock extends DirectionalKineticBlock
        implements SimpleWaterloggedBlock, ICogWheel, IBE<ItemPumpBlockEntity> {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public ItemPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    public static Direction.Axis getAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(WATERLOGGED));
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return getAxis(state);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // Line the pump up with whatever it can link, preferring the face that was clicked.
        Direction clicked = context.getClickedFace();
        Direction.Axis[] order = { clicked.getAxis(), Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z };
        for (Direction.Axis axis : order) {
            if (canLinkAlong(level, pos, axis)) {
                Direction facing = axis == clicked.getAxis() ? clicked
                        : Direction.get(Direction.AxisDirection.POSITIVE, axis);
                state = state.setValue(FACING, facing);
                break;
            }
        }
        return state.setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
    }

    private static boolean canLinkAlong(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction direction = Direction.get(sign, axis);
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.is(AddonTags.LINKABLE_STORAGE) || neighbor.getBlock() instanceof ItemPipeBlock
                    || (neighbor.getBlock() instanceof ItemPumpBlock && getAxis(neighbor) == axis)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.PUMP.get(state.getValue(FACING));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel serverLevel
                && (!oldState.is(this) || getAxis(oldState) != getAxis(state))) {
            LinkNetworks.get(serverLevel).onTopologyChanged(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)) {
            LinkNetworks.get(serverLevel).onTopologyChanged(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public Class<ItemPumpBlockEntity> getBlockEntityClass() {
        return ItemPumpBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ItemPumpBlockEntity> getBlockEntityType() {
        return AddonBlockEntityTypes.ITEM_PUMP.get();
    }
}

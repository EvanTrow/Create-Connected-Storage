package tech.trowbridge.connectedstorage.content;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import tech.trowbridge.connectedstorage.index.AddonTags;
import tech.trowbridge.connectedstorage.link.LinkNetworks;

/**
 * A pipe-shaped block that merges every linkable storage it touches (directly, or through a chain of other
 * Item Pipes and Pumps) into one shared inventory. The block holds no data: {@link LinkNetworks} works the network
 * out from the blocks in the world, so pipes survive being moved by contraptions.
 */
public class ItemPipeBlock extends Block implements IWrenchable, SimpleWaterloggedBlock {

    public static final EnumProperty<LinkSide> NORTH = EnumProperty.create("north", LinkSide.class);
    public static final EnumProperty<LinkSide> EAST = EnumProperty.create("east", LinkSide.class);
    public static final EnumProperty<LinkSide> SOUTH = EnumProperty.create("south", LinkSide.class);
    public static final EnumProperty<LinkSide> WEST = EnumProperty.create("west", LinkSide.class);
    public static final EnumProperty<LinkSide> UP = EnumProperty.create("up", LinkSide.class);
    public static final EnumProperty<LinkSide> DOWN = EnumProperty.create("down", LinkSide.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final Map<Direction, EnumProperty<LinkSide>> PROPERTY_BY_DIRECTION = new EnumMap<>(Map.of(
            Direction.NORTH, NORTH,
            Direction.EAST, EAST,
            Direction.SOUTH, SOUTH,
            Direction.WEST, WEST,
            Direction.UP, UP,
            Direction.DOWN, DOWN));

    private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> RIMS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.NORTH, Block.box(4, 4, 0, 12, 12, 4));
        ARMS.put(Direction.SOUTH, Block.box(4, 4, 12, 12, 12, 16));
        ARMS.put(Direction.WEST, Block.box(0, 4, 4, 4, 12, 12));
        ARMS.put(Direction.EAST, Block.box(12, 4, 4, 16, 12, 12));
        ARMS.put(Direction.DOWN, Block.box(4, 0, 4, 12, 4, 12));
        ARMS.put(Direction.UP, Block.box(4, 12, 4, 12, 16, 12));

        RIMS.put(Direction.NORTH, Block.box(3, 3, 0, 13, 13, 2));
        RIMS.put(Direction.SOUTH, Block.box(3, 3, 14, 13, 13, 16));
        RIMS.put(Direction.WEST, Block.box(0, 3, 3, 2, 13, 13));
        RIMS.put(Direction.EAST, Block.box(14, 3, 3, 16, 13, 13));
        RIMS.put(Direction.DOWN, Block.box(3, 0, 3, 13, 2, 13));
        RIMS.put(Direction.UP, Block.box(3, 14, 3, 13, 16, 13));
    }

    private final Map<BlockState, VoxelShape> shapes = new IdentityHashMap<>();

    public ItemPipeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(WATERLOGGED, false);
        for (EnumProperty<LinkSide> property : PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(property, LinkSide.NONE);
        }
        registerDefaultState(state);
        for (BlockState possible : stateDefinition.getPossibleStates()) {
            shapes.put(possible, buildShape(possible));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED);
    }

    public static LinkSide getSide(BlockState state, Direction direction) {
        return state.getValue(PROPERTY_BY_DIRECTION.get(direction));
    }

    /** How a pipe connects to {@code neighbor}, which sits in {@code direction} from it. */
    public static LinkSide sideFor(BlockState neighbor, Direction direction) {
        if (neighbor.getBlock() instanceof ItemPipeBlock) {
            return LinkSide.PIPE;
        }
        if (neighbor.getBlock() instanceof ItemPumpBlock) {
            // Pumps only connect on the two ends of their axis.
            return ItemPumpBlock.getAxis(neighbor) == direction.getAxis() ? LinkSide.PIPE : LinkSide.NONE;
        }
        if (neighbor.is(AddonTags.LINKABLE_STORAGE)) {
            return LinkSide.STORAGE;
        }
        return LinkSide.NONE;
    }

    /** {@code state} with every side matched to the blocks around {@code pos}. */
    public static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                    sideFor(level.getBlockState(pos.relative(direction)), direction));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean water = level.getFluidState(pos).getType() == Fluids.WATER;
        return withConnections(defaultBlockState().setValue(WATERLOGGED, water), level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), sideFor(neighborState, direction));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && !level.isClientSide()) {
            // Placed without a placement context (commands, schematics, contraptions): connect to the
            // neighbors now. The nested setBlock comes back through onPlace and reports the change.
            BlockState connected = withConnections(state, level, pos);
            if (connected != state) {
                level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
                return;
            }
        }
        if (level instanceof ServerLevel serverLevel && !sameConnections(state, oldState)) {
            LinkNetworks.get(serverLevel).onTopologyChanged(pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)) {
            LinkNetworks.get(serverLevel).onTopologyChanged(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private boolean sameConnections(BlockState a, BlockState b) {
        if (!a.is(this) || !b.is(this)) {
            return false;
        }
        for (EnumProperty<LinkSide> property : PROPERTY_BY_DIRECTION.values()) {
            if (a.getValue(property) != b.getValue(property)) {
                return false;
            }
        }
        return true;
    }

    private static VoxelShape buildShape(BlockState state) {
        VoxelShape shape = CORE;
        for (Direction direction : Direction.values()) {
            LinkSide side = getSide(state, direction);
            if (side.isConnected()) {
                shape = Shapes.or(shape, ARMS.get(direction));
            }
            if (side == LinkSide.STORAGE) {
                shape = Shapes.or(shape, RIMS.get(direction));
            }
        }
        return shape.optimize();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state;
        for (Direction direction : Direction.values()) {
            rotated = rotated.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(direction)), getSide(state, direction));
        }
        return rotated;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction direction : Direction.values()) {
            mirrored = mirrored.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(direction)), getSide(state, direction));
        }
        return mirrored;
    }

    /** Connections follow the neighbors, so a plain wrench click does nothing. Sneak-wrench still picks it up. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }
}

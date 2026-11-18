package tech.trowbridge.connectedstorage.link;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import tech.trowbridge.connectedstorage.content.LinkSide;
import tech.trowbridge.connectedstorage.content.ItemPipeBlock;
import tech.trowbridge.connectedstorage.content.ItemPumpBlock;
import tech.trowbridge.connectedstorage.content.ItemPumpBlockEntity;
import tech.trowbridge.connectedstorage.index.AddonTags;

/**
 * The blocks a network runs through: Item Pipes (which connect on any side) and Item Pumps
 * (which connect only on the two ends of their axis).
 */
public final class LinkConduits {

    private LinkConduits() {
    }

    public static boolean isConduit(BlockState state) {
        return state.getBlock() instanceof ItemPipeBlock || isPump(state);
    }

    public static boolean isPump(BlockState state) {
        return state.getBlock() instanceof ItemPumpBlock;
    }

    /**
     * How the pipe or pump at {@code pos} carries the network towards {@code direction}. A pump that isn't
     * running at its minimum speed carries nothing: storages and pipes behind it stay cut off.
     */
    public static LinkSide side(Level level, BlockPos pos, BlockState state, Direction direction) {
        if (state.getBlock() instanceof ItemPipeBlock) {
            return ItemPipeBlock.getSide(state, direction);
        }
        if (isPump(state)) {
            if (ItemPumpBlock.getAxis(state) != direction.getAxis() || !isRunningPump(level, pos)) {
                return LinkSide.NONE;
            }
            return ItemPipeBlock.sideFor(level.getBlockState(pos.relative(direction)), direction);
        }
        return LinkSide.NONE;
    }

    /** How something would connect to {@code neighbor} if it could face any way. */
    static LinkSide anySide(BlockState neighbor) {
        if (isConduit(neighbor)) {
            return LinkSide.PIPE;
        }
        return neighbor.is(AddonTags.LINKABLE_STORAGE) ? LinkSide.STORAGE : LinkSide.NONE;
    }

    static boolean isRunningPump(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ItemPumpBlockEntity pump
                && pump.isRunning();
    }
}

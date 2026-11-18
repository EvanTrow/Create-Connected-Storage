package tech.trowbridge.connectedstorage.content;

import java.util.List;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import tech.trowbridge.connectedstorage.link.LinkNetworks;

public class ItemPumpBlockEntity extends KineticBlockEntity {

    /** Each this many RPM lets the pump reach one more pipe block. */
    public static final int RPM_PER_BLOCK = 16;
    /** The furthest a pump reaches along its pipes (at 256 RPM); beyond that needs another pump. */
    public static final int MAX_REACH = 16;

    private int lastReach;

    public ItemPumpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** How many pipe blocks along its pipes this pump reaches right now; 0 when stopped or too slow. */
    public int getReach() {
        if (isOverStressed()) {
            return 0;
        }
        return Math.min(MAX_REACH, (int) (Math.abs(getSpeed()) / RPM_PER_BLOCK));
    }

    /** Whether the pump links anything at all (at least {@link #RPM_PER_BLOCK} RPM). */
    public boolean isRunning() {
        return getReach() > 0;
    }

    @Override
    public void tick() {
        super.tick();
        // Covers speed changes, overstress and network loss alike: re-link whenever the reach changes.
        if (level instanceof ServerLevel serverLevel) {
            int reach = getReach();
            if (reach != lastReach) {
                lastReach = reach;
                LinkNetworks.get(serverLevel).onTopologyChanged(worldPosition);
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal("    ").append(Component.translatable("connectedstorage.goggles.pump")
                .withStyle(ChatFormatting.GRAY)));
        int reach = getReach();
        tooltip.add(Component.literal("     ").append(reach > 0
                ? Component.translatable("connectedstorage.goggles.pump.reach", reach, MAX_REACH).withStyle(ChatFormatting.GREEN)
                : Component.translatable("connectedstorage.goggles.pump.too_slow", RPM_PER_BLOCK).withStyle(ChatFormatting.RED)));
        return true;
    }
}

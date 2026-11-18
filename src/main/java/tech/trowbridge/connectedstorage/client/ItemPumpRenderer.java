package tech.trowbridge.connectedstorage.client;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import tech.trowbridge.connectedstorage.content.ItemPumpBlockEntity;

/** Spins Create's pump cog when Flywheel isn't doing the rendering (same as Create's own pump renderer). */
public class ItemPumpRenderer extends KineticBlockEntityRenderer<ItemPumpBlockEntity> {

    public ItemPumpRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(ItemPumpBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.MECHANICAL_PUMP_COG, state);
    }
}

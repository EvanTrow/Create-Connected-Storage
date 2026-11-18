package tech.trowbridge.connectedstorage;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import tech.trowbridge.connectedstorage.client.ItemPumpRenderer;
import tech.trowbridge.connectedstorage.content.ItemPumpBlockEntity;
import tech.trowbridge.connectedstorage.index.AddonBlockEntityTypes;
import tech.trowbridge.connectedstorage.index.AddonBlocks;
import tech.trowbridge.connectedstorage.ponder.ConnectedStoragePonderPlugin;

@Mod(value = ConnectedStorage.MOD_ID, dist = Dist.CLIENT)
public class ConnectedStorageClient {

    public ConnectedStorageClient(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, ConnectedStorageClient::clientSetup);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, ConnectedStorageClient::registerRenderers);
        PonderIndex.addPlugin(new ConnectedStoragePonderPlugin());
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Create-style "Hold [Shift] for Summary" tooltips, read from the lang file.
            Item pipe = AddonBlocks.ITEM_PIPE_ITEM.get();
            TooltipModifier.REGISTRY.register(pipe, new ItemDescription.Modifier(pipe, FontHelper.Palette.STANDARD_CREATE));
            Item pump = AddonBlocks.ITEM_PUMP_ITEM.get();
            TooltipModifier.REGISTRY.register(pump, new ItemDescription.Modifier(pump, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(pump))));

            // Flywheel draws the spinning cog the same way it does for Create's Mechanical Pump.
            SimpleBlockEntityVisualizer.builder(AddonBlockEntityTypes.ITEM_PUMP.get())
                    .factory(SingleAxisRotatingVisual.<ItemPumpBlockEntity>ofZ(AllPartialModels.MECHANICAL_PUMP_COG))
                    .skipVanillaRender(be -> true)
                    .apply();
        });
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AddonBlockEntityTypes.ITEM_PUMP.get(), ItemPumpRenderer::new);
    }
}

package tech.trowbridge.storagemerger;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import tech.trowbridge.storagemerger.index.AddonBlocks;

@Mod(value = StorageMerger.MOD_ID, dist = Dist.CLIENT)
public class StorageMergerClient {

    public StorageMergerClient(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, StorageMergerClient::clientSetup);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        // Create-style "Hold [Shift] for Summary" tooltip, read from the lang file.
        Item linker = AddonBlocks.STORAGE_LINKER_ITEM.get();
        event.enqueueWork(() -> TooltipModifier.REGISTRY.register(linker,
                new ItemDescription.Modifier(linker, FontHelper.Palette.STANDARD_CREATE)));
    }
}

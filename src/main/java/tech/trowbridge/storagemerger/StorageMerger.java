package tech.trowbridge.storagemerger;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllCreativeModeTabs;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import tech.trowbridge.storagemerger.index.AddonBlocks;
import tech.trowbridge.storagemerger.link.LinkNetworks;
import tech.trowbridge.storagemerger.link.LinkedCapabilities;
import tech.trowbridge.storagemerger.link.SiloInventoryIdentifiers;

@Mod(StorageMerger.MOD_ID)
public class StorageMerger {

    public static final String MOD_ID = "storagemerger";

    public StorageMerger(IEventBus modBus) {
        AddonBlocks.register(modBus);
        // HIGHEST so our provider is registered (and therefore asked) before Create's own vault provider.
        modBus.addListener(EventPriority.HIGHEST, false, RegisterCapabilitiesEvent.class, LinkedCapabilities::register);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, StorageMerger::buildCreativeTabs);
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(SiloInventoryIdentifiers::register));
        LinkNetworks.registerEvents(NeoForge.EVENT_BUS);
    }

    private static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey()) {
            return;
        }
        ItemStack linker = new ItemStack(AddonBlocks.STORAGE_LINKER_ITEM.get());
        try {
            event.insertAfter(AllBlocks.ITEM_VAULT.asStack(), linker,
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (IllegalArgumentException e) {
            event.accept(linker);
        }
    }
}

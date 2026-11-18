package tech.trowbridge.connectedstorage;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.api.stress.BlockStressValues;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import tech.trowbridge.connectedstorage.index.AddonBlockEntityTypes;
import tech.trowbridge.connectedstorage.index.AddonBlocks;
import tech.trowbridge.connectedstorage.link.LinkNetworks;
import tech.trowbridge.connectedstorage.link.LinkedCapabilities;
import tech.trowbridge.connectedstorage.link.SiloInventoryIdentifiers;

@Mod(ConnectedStorage.MOD_ID)
public class ConnectedStorage {

    public static final String MOD_ID = "connectedstorage";

    public ConnectedStorage(IEventBus modBus) {
        AddonBlocks.register(modBus);
        AddonBlockEntityTypes.register(modBus);
        // HIGHEST so our provider is registered (and therefore asked) before Create's own vault provider.
        modBus.addListener(EventPriority.HIGHEST, false, RegisterCapabilitiesEvent.class, LinkedCapabilities::register);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, ConnectedStorage::buildCreativeTabs);
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(ConnectedStorage::setup));
        LinkNetworks.registerEvents(NeoForge.EVENT_BUS);
    }

    private static void setup() {
        SiloInventoryIdentifiers.register();
        BlockStressValues.IMPACTS.register(AddonBlocks.ITEM_PUMP.get(), () -> AddonBlocks.PUMP_STRESS_IMPACT);
    }

    private static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey()) {
            return;
        }
        ItemStack pipe = new ItemStack(AddonBlocks.ITEM_PIPE_ITEM.get());
        ItemStack pump = new ItemStack(AddonBlocks.ITEM_PUMP_ITEM.get());
        try {
            event.insertAfter(AllBlocks.ITEM_VAULT.asStack(), pipe, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.insertAfter(pipe, pump, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (IllegalArgumentException e) {
            event.accept(pipe);
            event.accept(pump);
        }
    }
}

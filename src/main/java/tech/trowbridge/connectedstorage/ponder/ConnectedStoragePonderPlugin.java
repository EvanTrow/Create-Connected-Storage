package tech.trowbridge.connectedstorage.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import tech.trowbridge.connectedstorage.ConnectedStorage;

public class ConnectedStoragePonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return ConnectedStorage.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // One scene, reachable from both the Item Pipe and the Item Pump.
        helper.forComponents(
                        ResourceLocation.fromNamespaceAndPath(ConnectedStorage.MOD_ID, "item_pump"),
                        ResourceLocation.fromNamespaceAndPath(ConnectedStorage.MOD_ID, "item_pipe"))
                .addStoryBoard("item_pump", ItemPumpScenes::linking);
    }
}

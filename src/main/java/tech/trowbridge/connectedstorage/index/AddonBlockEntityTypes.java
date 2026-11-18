package tech.trowbridge.connectedstorage.index;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import tech.trowbridge.connectedstorage.ConnectedStorage;
import tech.trowbridge.connectedstorage.content.ItemPumpBlockEntity;

public class AddonBlockEntityTypes {

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ConnectedStorage.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemPumpBlockEntity>>
            ITEM_PUMP = BLOCK_ENTITIES.register("item_pump",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new ItemPumpBlockEntity(AddonBlockEntityTypes.ITEM_PUMP.get(), pos, state),
                            AddonBlocks.ITEM_PUMP.get())
                            .build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}

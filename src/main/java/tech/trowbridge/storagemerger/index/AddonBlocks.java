package tech.trowbridge.storagemerger.index;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import tech.trowbridge.storagemerger.StorageMerger;
import tech.trowbridge.storagemerger.content.StorageLinkerBlock;

public class AddonBlocks {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, StorageMerger.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, StorageMerger.MOD_ID);

    public static final DeferredHolder<Block, StorageLinkerBlock> STORAGE_LINKER = BLOCKS.register(
            "storage_linker",
            () -> new StorageLinkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.METAL)
                    .strength(1.0f)
                    .noOcclusion()));

    public static final DeferredHolder<Item, BlockItem> STORAGE_LINKER_ITEM = ITEMS.register(
            "storage_linker",
            () -> new BlockItem(STORAGE_LINKER.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}

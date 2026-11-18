package tech.trowbridge.connectedstorage.index;

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
import tech.trowbridge.connectedstorage.ConnectedStorage;
import tech.trowbridge.connectedstorage.content.ItemPipeBlock;
import tech.trowbridge.connectedstorage.content.ItemPumpBlock;

public class AddonBlocks {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, ConnectedStorage.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, ConnectedStorage.MOD_ID);

    public static final DeferredHolder<Block, ItemPipeBlock> ITEM_PIPE = BLOCKS.register(
            "item_pipe",
            () -> new ItemPipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.METAL)
                    .strength(1.0f)
                    .noOcclusion()));

    public static final DeferredHolder<Item, BlockItem> ITEM_PIPE_ITEM = ITEMS.register(
            "item_pipe",
            () -> new BlockItem(ITEM_PIPE.get(), new Item.Properties()));

    public static final DeferredHolder<Block, ItemPumpBlock> ITEM_PUMP = BLOCKS.register(
            "item_pump",
            () -> new ItemPumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.METAL)
                    .strength(1.0f)
                    .noOcclusion()));

    public static final DeferredHolder<Item, BlockItem> ITEM_PUMP_ITEM = ITEMS.register(
            "item_pump",
            () -> new BlockItem(ITEM_PUMP.get(), new Item.Properties()));

    /** Stress impact per RPM: 4 su/RPM, so 64 su at 16 RPM and 1024 su at 256 RPM. */
    public static final double PUMP_STRESS_IMPACT = 4.0;

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}

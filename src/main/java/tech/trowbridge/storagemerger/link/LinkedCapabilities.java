package tech.trowbridge.storagemerger.link;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllBlockEntityTypes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Puts the shared network inventory in front of each linked storage's own item handler.
 *
 * <p>NeoForge asks capability providers in the order they were registered and uses the first non-null answer.
 * This listener runs at {@code EventPriority.HIGHEST}, ahead of Create's and Create: Connected's own providers,
 * and returns {@code null} for any storage that isn't linked so those providers answer as usual.
 */
public final class LinkedCapabilities {

    private static final ResourceLocation ITEM_SILO = ResourceLocation.fromNamespaceAndPath("create_connected", "item_silo");

    /** Nesting depth of {@link #withoutLinking}; while above zero, our provider steps aside. */
    private static final ThreadLocal<int[]> BYPASS = ThreadLocal.withInitial(() -> new int[1]);

    private LinkedCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        register(event, AllBlockEntityTypes.ITEM_VAULT.get());
        BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ITEM_SILO).ifPresent(type -> register(event, type));
    }

    @SuppressWarnings("unchecked")
    private static void register(RegisterCapabilitiesEvent event, BlockEntityType<?> type) {
        LinkableStorage.addType(type);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, (BlockEntityType<BlockEntity>) type,
                (blockEntity, side) -> provide(blockEntity));
    }

    @Nullable
    private static IItemHandler provide(BlockEntity blockEntity) {
        return BYPASS.get()[0] > 0 ? null : LinkNetworks.linkedHandlerFor(blockEntity);
    }

    /** Runs {@code action} with linking switched off, to reach a storage's own handler. */
    static <T> T withoutLinking(Supplier<T> action) {
        int[] depth = BYPASS.get();
        depth[0]++;
        try {
            return action.get();
        } finally {
            depth[0]--;
        }
    }
}

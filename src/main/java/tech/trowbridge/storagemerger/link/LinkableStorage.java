package tech.trowbridge.storagemerger.link;

import java.util.Collections;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * The storage block entity types a Storage Linker can merge. They all share Create's multiblock inventory
 * interface, so the rest of the code never needs to know which mod a storage comes from.
 */
public final class LinkableStorage {

    private static final Set<BlockEntityType<?>> TYPES = Collections.synchronizedSet(new ReferenceOpenHashSet<>());

    private LinkableStorage() {
    }

    static void addType(BlockEntityType<?> type) {
        TYPES.add(type);
    }

    /** The block entity as a linkable multiblock storage, or {@code null} if it isn't one. */
    @Nullable
    public static IMultiBlockEntityContainer.Inventory asStorage(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved() || !TYPES.contains(blockEntity.getType())) {
            return null;
        }
        return blockEntity instanceof IMultiBlockEntityContainer.Inventory inventory ? inventory : null;
    }

    /** The controller position of the storage at {@code pos}, without loading chunks. */
    @Nullable
    static BlockPos controllerOf(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        IMultiBlockEntityContainer.Inventory storage = asStorage(level.getBlockEntity(pos));
        return storage == null ? null : storage.getController();
    }

    /** Whether {@code pos} holds the controller of a loaded linkable storage. */
    static boolean isController(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        IMultiBlockEntityContainer.Inventory storage = asStorage(level.getBlockEntity(pos));
        return storage != null && storage.isController() && pos.equals(storage.getController());
    }
}

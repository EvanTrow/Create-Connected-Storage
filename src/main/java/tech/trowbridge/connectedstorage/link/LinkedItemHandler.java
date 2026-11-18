package tech.trowbridge.connectedstorage.link;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The item handler every linkable storage block hands out. It works out on use whether the storage is
 * currently linked: if so it lays the whole network's slots end to end (this storage first, so inserting
 * fills it before spilling over); if not it is simply the storage's own handler.
 *
 * <p>Create's funnels, chutes and packagers look a handler up once and keep it until a neighbouring block
 * changes; they ignore capability invalidation. Because this object re-resolves itself, a handler grabbed
 * before a link forms, after it breaks, or before the vault re-forms with a new controller stays correct.
 * The resolution is remembered for the rest of the tick unless a network changes in between.
 */
public final class LinkedItemHandler implements IItemHandler {

    private static final IItemHandler[] NONE = new IItemHandler[0];

    private final LinkNetworks networks;
    /** The storage block this handler was handed out for. */
    private final BlockPos pos;

    private IItemHandler @Nullable [] resolved;
    private long resolvedTick;
    private long resolvedEpoch;

    LinkedItemHandler(LinkNetworks networks, BlockPos pos) {
        this.networks = networks;
        this.pos = pos;
    }

    private IItemHandler[] handlers() {
        ServerLevel level = networks.level();
        if (!level.getServer().isSameThread()) {
            return NONE;
        }
        long tick = level.getGameTime();
        long epoch = networks.epoch();
        if (resolved == null || tick != resolvedTick || epoch != resolvedEpoch) {
            resolved = resolve(level);
            resolvedTick = tick;
            // Resolving can itself rebuild a network; read the epoch afterwards so that doesn't count.
            resolvedEpoch = networks.epoch();
        }
        return resolved;
    }

    private IItemHandler[] resolve(ServerLevel level) {
        if (!level.isLoaded(pos)) {
            return NONE;
        }
        IMultiBlockEntityContainer.Inventory storage = LinkableStorage.asStorage(level.getBlockEntity(pos));
        if (storage == null) {
            return NONE;
        }
        BlockPos controller = storage.getController();
        if (controller != null) {
            LinkNetwork network = networks.getNetwork(controller);
            if (network != null && network.isLinked()) {
                return network.handlersFor(controller);
            }
        }
        IItemHandler own = LinkedCapabilities.withoutLinking(
                () -> level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null));
        return own == null ? NONE : new IItemHandler[] { own };
    }

    @Override
    public int getSlots() {
        int slots = 0;
        for (IItemHandler handler : handlers()) {
            slots += handler.getSlots();
        }
        return slots;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        for (IItemHandler handler : handlers()) {
            int slots = handler.getSlots();
            if (slot < slots) {
                return handler.getStackInSlot(slot);
            }
            slot -= slots;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        for (IItemHandler handler : handlers()) {
            int slots = handler.getSlots();
            if (slot < slots) {
                return handler.insertItem(slot, stack, simulate);
            }
            slot -= slots;
        }
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        for (IItemHandler handler : handlers()) {
            int slots = handler.getSlots();
            if (slot < slots) {
                return handler.extractItem(slot, amount, simulate);
            }
            slot -= slots;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        for (IItemHandler handler : handlers()) {
            int slots = handler.getSlots();
            if (slot < slots) {
                return handler.getSlotLimit(slot);
            }
            slot -= slots;
        }
        return 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        for (IItemHandler handler : handlers()) {
            int slots = handler.getSlots();
            if (slot < slots) {
                return handler.isItemValid(slot, stack);
            }
            slot -= slots;
        }
        return false;
    }
}

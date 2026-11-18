package tech.trowbridge.storagemerger.link;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The shared inventory of a storage network, as seen from one member. Slots are the members' slots laid end to
 * end, starting with the member this view belongs to.
 *
 * <p>The view looks the network up on every call instead of holding on to it, so a funnel or chute that cached
 * this object keeps working while the network is rebuilt underneath it.
 */
public final class LinkedItemHandler implements IItemHandler {

    private static final IItemHandler[] NONE = new IItemHandler[0];

    private final LinkNetworks networks;
    private final BlockPos access;

    LinkedItemHandler(LinkNetworks networks, BlockPos access) {
        this.networks = networks;
        this.access = access;
    }

    private IItemHandler[] handlers() {
        if (!networks.level().getServer().isSameThread()) {
            return NONE;
        }
        LinkNetwork network = networks.getNetwork(access);
        if (network == null) {
            return NONE;
        }
        if (network.isLinked()) {
            return network.handlersFor(access);
        }
        // Briefly possible after a split, until cached accessors re-query: act as the storage alone.
        IItemHandler own = LinkedCapabilities.withoutLinking(
                () -> networks.level().getCapability(Capabilities.ItemHandler.BLOCK, access, null));
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

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj instanceof LinkedItemHandler other && other.networks == networks && other.access.equals(access);
    }

    @Override
    public int hashCode() {
        return access.hashCode();
    }
}

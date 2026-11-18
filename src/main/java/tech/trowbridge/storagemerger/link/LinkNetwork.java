package tech.trowbridge.storagemerger.link;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * One connected group of storages, found by {@link LinkNetworks}. A network with a single member is cached too
 * (so unlinked vaults don't trigger a search on every lookup), but only networks with two or more members
 * change how the storages behave.
 */
public final class LinkNetwork {

    private static final IItemHandler[] NO_HANDLERS = new IItemHandler[0];

    private final LinkNetworks owner;
    /** Controller positions of the member storages, in discovery order. */
    final List<BlockPos> members;
    final Set<BlockPos> memberSet;
    /** Every block of every member storage. */
    final Set<BlockPos> memberBlocks;
    /** Every Storage Linker in the network. */
    final Set<BlockPos> linkers;
    /** Chunks this network spans or borders; loading or unloading one of them may change the network. */
    final LongSet chunks;
    final LinkedInventoryIdentifier identifier;

    /** Each member's own (Create / Create: Connected) item handler, kept fresh by NeoForge's invalidation. */
    private final Map<BlockPos, BlockCapabilityCache<IItemHandler, @Nullable Direction>> memberCaches = new HashMap<>();
    private final Map<BlockPos, LinkedItemHandler> handlerViews = new HashMap<>();
    private final Map<BlockPos, IItemHandler[]> orderedHandlers = new HashMap<>();
    private long orderedHandlersTick = Long.MIN_VALUE;

    boolean stale;
    boolean disposed;
    long validatedTick = Long.MIN_VALUE;
    long lastAccessTick;

    LinkNetwork(LinkNetworks owner, List<BlockPos> members, Set<BlockPos> memberBlocks, Set<BlockPos> linkers,
            LongSet chunks) {
        this.owner = owner;
        this.members = List.copyOf(members);
        this.memberSet = Set.copyOf(members);
        this.memberBlocks = memberBlocks;
        this.linkers = linkers;
        this.chunks = chunks;
        this.identifier = new LinkedInventoryIdentifier(this.memberSet, memberBlocks);

        if (isLinked()) {
            ServerLevel level = owner.level();
            for (BlockPos member : this.members) {
                memberCaches.put(member, BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, member,
                        null, () -> !disposed, () -> owner.onMemberInvalidated(this)));
            }
        }
    }

    public boolean isLinked() {
        return members.size() > 1;
    }

    public int size() {
        return members.size();
    }

    /** Whether every member is still a loaded storage controller. */
    boolean validate(ServerLevel level) {
        for (BlockPos member : members) {
            if (!LinkableStorage.isController(level, member)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a change at {@code pos} (a linker placed, removed or reconnected) could affect this network. */
    boolean touches(BlockPos pos) {
        if (linkers.contains(pos) || memberBlocks.contains(pos)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (linkers.contains(neighbor) || memberBlocks.contains(neighbor)) {
                return true;
            }
        }
        return false;
    }

    /** The shared item handler as seen from {@code access}, one of the member controllers. */
    LinkedItemHandler handlerFor(BlockPos access) {
        return handlerViews.computeIfAbsent(access, pos -> new LinkedItemHandler(owner, pos));
    }

    /**
     * The members' own item handlers, with {@code access} first so inserting through a storage fills it before
     * spilling into the rest of the network.
     */
    IItemHandler[] handlersFor(BlockPos access) {
        if (disposed) {
            return NO_HANDLERS;
        }
        long tick = owner.level().getGameTime();
        if (tick != orderedHandlersTick) {
            orderedHandlers.clear();
            orderedHandlersTick = tick;
        }
        IItemHandler[] cached = orderedHandlers.get(access);
        if (cached != null) {
            return cached;
        }

        List<IItemHandler> handlers = new ArrayList<>(members.size());
        addHandler(handlers, access);
        for (BlockPos member : members) {
            if (!member.equals(access)) {
                addHandler(handlers, member);
            }
        }
        IItemHandler[] result = handlers.toArray(NO_HANDLERS);
        orderedHandlers.put(access, result);
        return result;
    }

    private void addHandler(List<IItemHandler> handlers, BlockPos member) {
        BlockCapabilityCache<IItemHandler, @Nullable Direction> cache = memberCaches.get(member);
        if (cache == null) {
            return;
        }
        IItemHandler handler = LinkedCapabilities.withoutLinking(cache::getCapability);
        if (handler != null) {
            handlers.add(handler);
        }
    }

    /** Drops cached handler arrays; called when a member's own handler may have been replaced. */
    void forgetHandlers() {
        orderedHandlers.clear();
        orderedHandlersTick = Long.MIN_VALUE;
    }

    void dispose() {
        disposed = true;
        memberCaches.clear();
        orderedHandlers.clear();
    }
}

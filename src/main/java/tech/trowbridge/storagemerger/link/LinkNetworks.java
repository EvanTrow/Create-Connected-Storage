package tech.trowbridge.storagemerger.link;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import tech.trowbridge.storagemerger.content.LinkSide;
import tech.trowbridge.storagemerger.content.StorageLinkerBlock;

/**
 * Finds and caches the storage networks of one server level.
 *
 * <p>Networks are worked out from the world on demand: start at a storage controller, walk its blocks, follow
 * any Storage Linkers touching it (and the linkers chained to those), and pull in every storage they reach.
 * Results are cached per controller and thrown away when a linker changes, a member re-forms, or a chunk the
 * network touches loads or unloads. Whenever membership changes, the capabilities of every affected block are
 * invalidated so funnels, chutes and other cached accessors pick up the new (or old) handler.
 */
public final class LinkNetworks {

    static final int MAX_LINKERS = 1024;
    static final int MAX_STORAGES = 64;
    static final int MAX_BLOCKS_PER_STORAGE = 4096;
    private static final int CLEANUP_INTERVAL = 1200;

    private static final Map<ServerLevel, LinkNetworks> BY_LEVEL = Collections.synchronizedMap(new IdentityHashMap<>());

    private final ServerLevel level;
    private final Map<BlockPos, LinkNetwork> byController = new java.util.HashMap<>();
    private final Set<BlockPos> pendingLinkerChanges = new HashSet<>();
    private final Set<LinkNetwork> staleNetworks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BlockPos> pendingInvalidations = new HashSet<>();
    private final ConcurrentLinkedQueue<Long> changedChunks = new ConcurrentLinkedQueue<>();
    private boolean selfInvalidating;

    private LinkNetworks(ServerLevel level) {
        this.level = level;
    }

    public static LinkNetworks get(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, LinkNetworks::new);
    }

    @Nullable
    private static LinkNetworks getIfPresent(ServerLevel level) {
        return BY_LEVEL.get(level);
    }

    ServerLevel level() {
        return level;
    }

    // ------------------------------------------------------------------------------------------------------
    // Lookup

    /**
     * The network the storage controlled at {@code controller} belongs to, rebuilding it if needed.
     * Returns {@code null} if there's no loaded storage controller there.
     */
    @Nullable
    public LinkNetwork getNetwork(BlockPos controller) {
        long now = level.getGameTime();
        LinkNetwork network = byController.get(controller);
        if (network != null && !network.stale && !network.disposed) {
            if (network.validatedTick == now || network.validate(level)) {
                network.validatedTick = now;
                network.lastAccessTick = now;
                return network;
            }
            markStale(network);
        }
        return rebuild(controller);
    }

    @Nullable
    private LinkNetwork rebuild(BlockPos seed) {
        Set<LinkNetwork> previous = Collections.newSetFromMap(new IdentityHashMap<>());
        LinkNetwork seedPrevious = byController.get(seed);
        if (seedPrevious != null) {
            previous.add(seedPrevious);
        }

        if (!LinkableStorage.isController(level, seed)) {
            // The storage is gone, unloaded or no longer a controller. Forget its network; if that network was
            // linked, the other members must switch handlers, and they get rebuilt on their next lookup.
            for (LinkNetwork old : previous) {
                retire(old, old.isLinked() ? old.memberBlocks : null);
            }
            return null;
        }

        LinkNetwork fresh = discover(seed);
        boolean changed = false;
        for (BlockPos member : fresh.members) {
            LinkNetwork old = byController.get(member);
            if (old != null) {
                previous.add(old);
            } else if (fresh.isLinked()) {
                // This storage used its own handler until now.
                changed = true;
            }
        }
        for (LinkNetwork old : previous) {
            if (!old.memberSet.equals(fresh.memberSet)) {
                changed = true;
            }
        }
        for (LinkNetwork old : previous) {
            retire(old, changed ? old.memberBlocks : null);
        }

        long now = level.getGameTime();
        fresh.validatedTick = now;
        fresh.lastAccessTick = now;
        for (BlockPos member : fresh.members) {
            byController.put(member, fresh);
        }
        if (changed) {
            pendingInvalidations.addAll(fresh.memberBlocks);
        }
        return fresh;
    }

    /** Removes a network from the cache, optionally queueing its blocks for capability invalidation. */
    private void retire(LinkNetwork network, @Nullable Set<BlockPos> invalidate) {
        for (BlockPos member : network.members) {
            byController.remove(member, network);
        }
        staleNetworks.remove(network);
        network.dispose();
        if (invalidate != null) {
            pendingInvalidations.addAll(invalidate);
        }
    }

    private void markStale(LinkNetwork network) {
        if (!network.disposed) {
            network.stale = true;
            network.forgetHandlers();
            staleNetworks.add(network);
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // Discovery

    private LinkNetwork discover(BlockPos start) {
        LinkedHashSet<BlockPos> members = new LinkedHashSet<>();
        Set<BlockPos> memberBlocks = new HashSet<>();
        Set<BlockPos> linkers = new HashSet<>();
        LongSet chunks = new LongOpenHashSet();
        Deque<BlockPos> storageQueue = new ArrayDeque<>();
        Deque<BlockPos> linkerQueue = new ArrayDeque<>();

        members.add(start);
        storageQueue.add(start);

        while (!storageQueue.isEmpty() || !linkerQueue.isEmpty()) {
            BlockPos controller;
            while ((controller = storageQueue.poll()) != null) {
                Set<BlockPos> blocks = collectBlocks(controller);
                memberBlocks.addAll(blocks);
                for (BlockPos block : blocks) {
                    chunks.add(ChunkPos.asLong(block));
                    for (Direction direction : Direction.values()) {
                        BlockPos neighbor = block.relative(direction);
                        if (blocks.contains(neighbor) || linkers.contains(neighbor)) {
                            continue;
                        }
                        chunks.add(ChunkPos.asLong(neighbor));
                        if (!level.isLoaded(neighbor) || linkers.size() >= MAX_LINKERS) {
                            continue;
                        }
                        BlockState state = level.getBlockState(neighbor);
                        if (state.getBlock() instanceof StorageLinkerBlock
                                && StorageLinkerBlock.getSide(state, direction.getOpposite()) == LinkSide.STORAGE) {
                            linkers.add(neighbor);
                            linkerQueue.add(neighbor);
                        }
                    }
                }
            }

            BlockPos linker;
            while ((linker = linkerQueue.poll()) != null) {
                BlockState state = level.getBlockState(linker);
                if (!(state.getBlock() instanceof StorageLinkerBlock)) {
                    continue;
                }
                for (Direction direction : Direction.values()) {
                    LinkSide side = StorageLinkerBlock.getSide(state, direction);
                    if (!side.isConnected()) {
                        continue;
                    }
                    BlockPos neighbor = linker.relative(direction);
                    chunks.add(ChunkPos.asLong(neighbor));
                    if (!level.isLoaded(neighbor)) {
                        continue;
                    }
                    if (side == LinkSide.PIPE) {
                        if (!linkers.contains(neighbor) && linkers.size() < MAX_LINKERS
                                && level.getBlockState(neighbor).getBlock() instanceof StorageLinkerBlock) {
                            linkers.add(neighbor);
                            linkerQueue.add(neighbor);
                        }
                    } else {
                        BlockPos other = LinkableStorage.controllerOf(level, neighbor);
                        if (other == null) {
                            continue;
                        }
                        chunks.add(ChunkPos.asLong(other));
                        if (!members.contains(other) && members.size() < MAX_STORAGES
                                && LinkableStorage.isController(level, other)) {
                            members.add(other);
                            storageQueue.add(other);
                        }
                    }
                }
            }
        }

        return new LinkNetwork(this, new ArrayList<>(members), memberBlocks, linkers, chunks);
    }

    /** All loaded blocks of the multiblock storage controlled at {@code controller}. */
    private Set<BlockPos> collectBlocks(BlockPos controller) {
        Set<BlockPos> blocks = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        blocks.add(controller);
        queue.add(controller);
        BlockPos pos;
        while ((pos = queue.poll()) != null && blocks.size() < MAX_BLOCKS_PER_STORAGE) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!blocks.contains(neighbor) && controller.equals(LinkableStorage.controllerOf(level, neighbor))) {
                    blocks.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return blocks;
    }

    /** Storage controllers reachable from the linker at {@code pos}, or from its neighbors if it was removed. */
    private void collectSeeds(BlockPos pos, Set<BlockPos> seeds) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(pos);
        queue.add(pos);
        BlockPos linker;
        while ((linker = queue.poll()) != null) {
            if (!level.isLoaded(linker)) {
                continue;
            }
            BlockState state = level.getBlockState(linker);
            boolean isLinker = state.getBlock() instanceof StorageLinkerBlock;
            if (!isLinker && !linker.equals(pos)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = linker.relative(direction);
                if (!level.isLoaded(neighbor)) {
                    continue;
                }
                // A removed linker no longer has connection properties; look at every neighbor instead.
                LinkSide side = isLinker ? StorageLinkerBlock.getSide(state, direction)
                        : StorageLinkerBlock.sideFor(level.getBlockState(neighbor));
                if (side == LinkSide.PIPE) {
                    if (visited.size() < MAX_LINKERS && visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                } else if (side == LinkSide.STORAGE) {
                    BlockPos controller = LinkableStorage.controllerOf(level, neighbor);
                    if (controller != null) {
                        seeds.add(controller);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // Change tracking

    /** A Storage Linker at {@code pos} was placed, removed or changed its connections. */
    public void onTopologyChanged(BlockPos pos) {
        pendingLinkerChanges.add(pos.immutable());
    }

    /** A member's own handler was invalidated by someone else (usually Create re-forming the multiblock). */
    void onMemberInvalidated(LinkNetwork network) {
        if (!selfInvalidating) {
            markStale(network);
        }
    }

    private void onChunkChanged(long chunk) {
        changedChunks.add(chunk);
    }

    private void tick() {
        Long chunk;
        if (!changedChunks.isEmpty()) {
            LongSet chunks = new LongOpenHashSet();
            while ((chunk = changedChunks.poll()) != null) {
                chunks.add(chunk.longValue());
            }
            for (LinkNetwork network : distinctNetworks()) {
                for (long c : chunks) {
                    if (network.chunks.contains(c)) {
                        markStale(network);
                        break;
                    }
                }
            }
        }

        Set<BlockPos> seeds = new LinkedHashSet<>();
        if (!pendingLinkerChanges.isEmpty()) {
            List<LinkNetwork> networks = distinctNetworks();
            for (BlockPos pos : pendingLinkerChanges) {
                for (LinkNetwork network : networks) {
                    if (network.touches(pos)) {
                        markStale(network);
                    }
                }
                collectSeeds(pos, seeds);
            }
            pendingLinkerChanges.clear();
            // A cached network's block list can lag behind a storage that grew, so don't trust it here.
            for (BlockPos seed : seeds) {
                LinkNetwork network = byController.get(seed);
                if (network != null) {
                    markStale(network);
                }
            }
        }

        if (!staleNetworks.isEmpty()) {
            List<LinkNetwork> stale = new ArrayList<>(staleNetworks);
            staleNetworks.clear();
            for (LinkNetwork network : stale) {
                seeds.addAll(network.members);
            }
        }

        // Rebuild eagerly so storages switch handlers right away, not on their next lookup.
        for (BlockPos seed : seeds) {
            getNetwork(seed);
        }

        if (level.getGameTime() % CLEANUP_INTERVAL == 0) {
            cleanup();
        }

        flushInvalidations();
    }

    /** Forgets unlinked storages nobody asked about for a while. Linked networks stay so they can be updated. */
    private void cleanup() {
        long cutoff = level.getGameTime() - CLEANUP_INTERVAL;
        for (LinkNetwork network : distinctNetworks()) {
            if (!network.isLinked() && network.lastAccessTick < cutoff) {
                retire(network, null);
            }
        }
    }

    private void flushInvalidations() {
        if (pendingInvalidations.isEmpty()) {
            return;
        }
        List<BlockPos> positions = new ArrayList<>(pendingInvalidations);
        pendingInvalidations.clear();
        selfInvalidating = true;
        try {
            for (BlockPos pos : positions) {
                level.invalidateCapabilities(pos);
            }
        } finally {
            selfInvalidating = false;
        }
    }

    private List<LinkNetwork> distinctNetworks() {
        Set<LinkNetwork> networks = Collections.newSetFromMap(new IdentityHashMap<>());
        networks.addAll(byController.values());
        return new ArrayList<>(networks);
    }

    // ------------------------------------------------------------------------------------------------------
    // Access from other code

    /**
     * The shared inventory for a linked storage block entity, or {@code null} if it isn't linked (or this isn't
     * the server thread), in which case the storage's own handler applies.
     */
    @Nullable
    public static LinkedItemHandler linkedHandlerFor(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return null;
        }
        IMultiBlockEntityContainer.Inventory storage = LinkableStorage.asStorage(blockEntity);
        BlockPos controller = storage == null ? null : storage.getController();
        if (controller == null) {
            return null;
        }
        LinkNetwork network = get(level).getNetwork(controller);
        return network != null && network.isLinked() ? network.handlerFor(controller) : null;
    }

    // ------------------------------------------------------------------------------------------------------
    // Packager / Stock Keeper identity

    /**
     * The inventory identifier for a linked storage, so every member of a network counts as one inventory.
     * Returns {@code null} to keep the storage's own identifier.
     */
    @Nullable
    public static InventoryIdentifier identifierFor(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return null;
        }
        IMultiBlockEntityContainer.Inventory storage = LinkableStorage.asStorage(blockEntity);
        if (storage == null) {
            return null;
        }
        BlockPos controller = storage.getController();
        if (controller == null) {
            return null;
        }
        LinkNetwork network = get(level).getNetwork(controller);
        return network != null && network.isLinked() ? network.identifier : null;
    }

    // ------------------------------------------------------------------------------------------------------
    // Events

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(LinkNetworks::onLevelTick);
        gameBus.addListener(LinkNetworks::onChunkLoad);
        gameBus.addListener(LinkNetworks::onChunkUnload);
        gameBus.addListener(LinkNetworks::onLevelUnload);
        gameBus.addListener(LinkNetworks::onServerStopped);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LinkNetworks networks = getIfPresent(level);
            if (networks != null) {
                networks.tick();
            }
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LinkNetworks networks = getIfPresent(level);
            if (networks != null) {
                networks.onChunkChanged(event.getChunk().getPos().toLong());
            }
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LinkNetworks networks = getIfPresent(level);
            if (networks != null) {
                networks.onChunkChanged(event.getChunk().getPos().toLong());
            }
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BY_LEVEL.remove(level);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        BY_LEVEL.clear();
    }
}

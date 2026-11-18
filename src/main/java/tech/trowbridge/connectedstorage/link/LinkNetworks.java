package tech.trowbridge.connectedstorage.link;

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
import tech.trowbridge.connectedstorage.content.LinkSide;
import tech.trowbridge.connectedstorage.content.ItemPipeBlock;
import tech.trowbridge.connectedstorage.content.ItemPumpBlockEntity;

/**
 * Finds and caches the storage networks of one server level.
 *
 * <p>Networks are worked out from the world on demand: start at a storage controller, walk its blocks, follow
 * any Item Pipes and Pumps touching it (and those chained to them), and pull in every storage they reach.
 * Storages only merge through running pumps and the pipes within those pumps' reach (1 pipe block per 16 RPM).
 * Results are cached per controller and thrown away when a pipe or pump changes, a member re-forms, or a chunk the
 * network touches loads or unloads. Whenever membership changes, the capabilities of every affected block are
 * invalidated so funnels, chutes and other cached accessors pick up the new (or old) handler.
 */
public final class LinkNetworks {

    static final int MAX_CONDUITS = 1024;
    static final int MAX_STORAGES = 64;
    static final int MAX_BLOCKS_PER_STORAGE = 4096;
    private static final int CLEANUP_INTERVAL = 1200;

    private static final Map<ServerLevel, LinkNetworks> BY_LEVEL = Collections.synchronizedMap(new IdentityHashMap<>());

    private final ServerLevel level;
    private final Map<BlockPos, LinkNetwork> byController = new java.util.HashMap<>();
    private final Set<BlockPos> pendingConduitChanges = new HashSet<>();
    private final Set<LinkNetwork> staleNetworks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BlockPos> pendingInvalidations = new HashSet<>();
    private final ConcurrentLinkedQueue<Long> changedChunks = new ConcurrentLinkedQueue<>();
    private boolean selfInvalidating;
    /** Bumped whenever any network is built, dropped or goes stale, so handlers know to re-resolve. */
    private long epoch;
    /**
     * The handler handed out for each storage block, kept for as long as the block is a storage so anything
     * holding on to it (Create's funnels and packagers, Jade) keeps following link changes.
     */
    private final Map<BlockPos, LinkedItemHandler> views = new java.util.HashMap<>();

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

    long epoch() {
        return epoch;
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
            // A pump starting or stopping flips isLinked() without changing who the members are.
            if (!old.memberSet.equals(fresh.memberSet) || old.isLinked() != fresh.isLinked()) {
                changed = true;
            }
        }
        for (LinkNetwork old : previous) {
            retire(old, changed ? old.memberBlocks : null);
        }

        long now = level.getGameTime();
        fresh.validatedTick = now;
        fresh.lastAccessTick = now;
        epoch++;
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
        epoch++;
        if (invalidate != null) {
            pendingInvalidations.addAll(invalidate);
        }
    }

    private void markStale(LinkNetwork network) {
        if (!network.disposed) {
            network.stale = true;
            epoch++;
            network.forgetHandlers();
            staleNetworks.add(network);
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // Discovery

    /** What a search from one storage found. */
    private record Reach(List<BlockPos> members, Set<BlockPos> memberBlocks, Set<BlockPos> conduits,
            Set<BlockPos> runningPumps, LongSet chunks) {
    }

    /**
     * Works out the network of the storage controlled at {@code start}, in three passes:
     * <ol>
     * <li>everything connected through running pumps and any pipes (kept for change tracking);</li>
     * <li>which of those pipes the running pumps reach, each pump {@code speed / 16} pipe blocks along the pipe;</li>
     * <li>everything connected through running pumps and reached pipes only: the actual members.</li>
     * </ol>
     */
    private LinkNetwork discover(BlockPos start) {
        Reach everything = explore(start, null);
        Set<BlockPos> poweredPipes = poweredPipes(everything.runningPumps(), everything.conduits());
        Reach linked = explore(start, poweredPipes);
        return new LinkNetwork(this, linked.members(), linked.memberBlocks(), everything.conduits(),
                everything.chunks(), !linked.runningPumps().isEmpty());
    }

    /**
     * Breadth-first search from a storage through pipes and running pumps. With {@code allowedPipes} set, only
     * those pipes can be passed through.
     */
    private Reach explore(BlockPos start, @Nullable Set<BlockPos> allowedPipes) {
        LinkedHashSet<BlockPos> members = new LinkedHashSet<>();
        Set<BlockPos> memberBlocks = new HashSet<>();
        Set<BlockPos> conduits = new HashSet<>();
        Set<BlockPos> runningPumps = new HashSet<>();
        LongSet chunks = new LongOpenHashSet();
        Deque<BlockPos> storageQueue = new ArrayDeque<>();
        Deque<BlockPos> conduitQueue = new ArrayDeque<>();

        members.add(start);
        storageQueue.add(start);

        while (!storageQueue.isEmpty() || !conduitQueue.isEmpty()) {
            BlockPos controller;
            while ((controller = storageQueue.poll()) != null) {
                Set<BlockPos> blocks = collectBlocks(controller);
                memberBlocks.addAll(blocks);
                for (BlockPos block : blocks) {
                    chunks.add(ChunkPos.asLong(block));
                    for (Direction direction : Direction.values()) {
                        BlockPos neighbor = block.relative(direction);
                        if (blocks.contains(neighbor) || conduits.contains(neighbor)) {
                            continue;
                        }
                        chunks.add(ChunkPos.asLong(neighbor));
                        if (!level.isLoaded(neighbor) || conduits.size() >= MAX_CONDUITS) {
                            continue;
                        }
                        BlockState state = level.getBlockState(neighbor);
                        if (LinkConduits.side(level, neighbor, state, direction.getOpposite()) == LinkSide.STORAGE
                                && passable(neighbor, state, allowedPipes)) {
                            conduits.add(neighbor);
                            conduitQueue.add(neighbor);
                        }
                    }
                }
            }

            BlockPos conduit;
            while ((conduit = conduitQueue.poll()) != null) {
                BlockState state = level.getBlockState(conduit);
                if (!LinkConduits.isConduit(state)) {
                    continue;
                }
                if (LinkConduits.isPump(state) && LinkConduits.isRunningPump(level, conduit)) {
                    runningPumps.add(conduit);
                }
                for (Direction direction : Direction.values()) {
                    LinkSide side = LinkConduits.side(level, conduit, state, direction);
                    if (!side.isConnected()) {
                        continue;
                    }
                    BlockPos neighbor = conduit.relative(direction);
                    chunks.add(ChunkPos.asLong(neighbor));
                    if (!level.isLoaded(neighbor)) {
                        continue;
                    }
                    if (side == LinkSide.PIPE) {
                        BlockState neighborState = level.getBlockState(neighbor);
                        if (!conduits.contains(neighbor) && conduits.size() < MAX_CONDUITS
                                && LinkConduits.isConduit(neighborState)
                                && passable(neighbor, neighborState, allowedPipes)) {
                            conduits.add(neighbor);
                            conduitQueue.add(neighbor);
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
        return new Reach(new ArrayList<>(members), memberBlocks, conduits, runningPumps, chunks);
    }

    /** Pumps are always passable (a stopped one already connects nothing); pipes only if allowed. */
    private static boolean passable(BlockPos pos, BlockState state, @Nullable Set<BlockPos> allowedPipes) {
        return allowedPipes == null || LinkConduits.isPump(state) || allowedPipes.contains(pos);
    }

    /**
     * The pipes within reach of a running pump: walking along the pipe from each end of the pump, the first
     * pipe block is 1 away, and a pump reaches {@link ItemPumpBlockEntity#getReach()} blocks. Pipes
     * past another pump are that pump's job.
     */
    private Set<BlockPos> poweredPipes(Set<BlockPos> runningPumps, Set<BlockPos> knownConduits) {
        Set<BlockPos> powered = new HashSet<>();
        for (BlockPos pump : runningPumps) {
            if (!(level.getBlockEntity(pump) instanceof ItemPumpBlockEntity be)) {
                continue;
            }
            int reach = be.getReach();
            BlockState pumpState = level.getBlockState(pump);
            Set<BlockPos> visited = new HashSet<>();
            Deque<BlockPos> frontier = new ArrayDeque<>();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pump.relative(direction);
                if (LinkConduits.side(level, pump, pumpState, direction) == LinkSide.PIPE
                        && knownConduits.contains(neighbor) && isPipe(neighbor) && visited.add(neighbor)) {
                    frontier.add(neighbor);
                }
            }
            for (int distance = 1; distance <= reach && !frontier.isEmpty(); distance++) {
                Deque<BlockPos> next = new ArrayDeque<>();
                BlockPos pipe;
                while ((pipe = frontier.poll()) != null) {
                    powered.add(pipe);
                    BlockState state = level.getBlockState(pipe);
                    for (Direction direction : Direction.values()) {
                        BlockPos neighbor = pipe.relative(direction);
                        if (ItemPipeBlock.getSide(state, direction) == LinkSide.PIPE
                                && knownConduits.contains(neighbor) && isPipe(neighbor) && visited.add(neighbor)) {
                            next.add(neighbor);
                        }
                    }
                }
                frontier = next;
            }
        }
        return powered;
    }

    private boolean isPipe(BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ItemPipeBlock;
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

    /** Storage controllers reachable from the pipe or pump at {@code pos}, or from its neighbors if it was removed. */
    private void collectSeeds(BlockPos pos, Set<BlockPos> seeds) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(pos);
        queue.add(pos);
        BlockPos conduit;
        while ((conduit = queue.poll()) != null) {
            if (!level.isLoaded(conduit)) {
                continue;
            }
            BlockState state = level.getBlockState(conduit);
            boolean isConduit = LinkConduits.isConduit(state);
            if (!isConduit && !conduit.equals(pos)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = conduit.relative(direction);
                if (!level.isLoaded(neighbor)) {
                    continue;
                }
                // A removed block has no connections left; look at every neighbor instead. The changed block
                // itself is always searched this way too, since a turned pump may have just left old neighbors.
                LinkSide side = isConduit && !conduit.equals(pos) ? LinkConduits.side(level, conduit, state, direction)
                        : LinkConduits.anySide(level.getBlockState(neighbor));
                if (side == LinkSide.PIPE) {
                    if (visited.size() < MAX_CONDUITS && visited.add(neighbor)) {
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

    /** A pipe or pump at {@code pos} was placed, removed, turned, reconnected, or a pump started or stopped. */
    public void onTopologyChanged(BlockPos pos) {
        pendingConduitChanges.add(pos.immutable());
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
        if (!pendingConduitChanges.isEmpty()) {
            List<LinkNetwork> networks = distinctNetworks();
            for (BlockPos pos : pendingConduitChanges) {
                for (LinkNetwork network : networks) {
                    if (network.touches(pos)) {
                        markStale(network);
                    }
                }
                collectSeeds(pos, seeds);
            }
            pendingConduitChanges.clear();
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
        views.keySet().removeIf(pos -> !level.isLoaded(pos) || LinkableStorage.asStorage(level.getBlockEntity(pos)) == null);
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
     * The item handler for a linkable storage block entity: the same object every time for a given block, which
     * serves the whole network while linked and just the storage otherwise. Returns {@code null} off the server
     * thread (or on the client), in which case the storage's own handler applies.
     */
    @Nullable
    public static LinkedItemHandler handlerFor(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || LinkableStorage.asStorage(blockEntity) == null) {
            return null;
        }
        LinkNetworks networks = get(level);
        return networks.views.computeIfAbsent(blockEntity.getBlockPos().immutable(),
                pos -> new LinkedItemHandler(networks, pos));
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

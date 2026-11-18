package tech.trowbridge.storagemerger.gametest;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import tech.trowbridge.storagemerger.StorageMerger;
import tech.trowbridge.storagemerger.content.LinkSide;
import tech.trowbridge.storagemerger.content.StorageLinkerBlock;
import tech.trowbridge.storagemerger.index.AddonBlocks;

/**
 * Run with {@code ./gradlew runGameTestServer}. Each test builds vaults and linkers in an empty 9x4x9 area.
 */
@GameTestHolder(StorageMerger.MOD_ID)
@PrefixGameTestTemplate(false)
public class LinkingGameTests {

    private static final String EMPTY = "empty";

    // ---------------------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY)
    public static void twoVaultsShareOneInventory(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos link = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);
        int[] single = new int[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    single[0] = slots(helper, a);
                    check(single[0] > 0 && slots(helper, b) == single[0], "unlinked vaults should have their own slots");
                    helper.setBlock(link, linker());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    int per = single[0];
                    BlockState linkState = helper.getBlockState(link);
                    check(StorageLinkerBlock.getSide(linkState, Direction.WEST) == LinkSide.STORAGE
                            && StorageLinkerBlock.getSide(linkState, Direction.EAST) == LinkSide.STORAGE,
                            "linker should connect to both vaults, state " + linkState);
                    check(slots(helper, a) == 2 * per, "vault A should see both vaults, got " + slots(helper, a));
                    check(slots(helper, b) == 2 * per, "vault B should see both vaults, got " + slots(helper, b));

                    IItemHandler viaA = items(helper, a);
                    for (int i = 0; i <= per; i++) {
                        ItemStack left = ItemHandlerHelper.insertItemStacked(viaA, new ItemStack(Items.COBBLESTONE, 64), false);
                        check(left.isEmpty(), "stack " + i + " didn't fit, left " + left);
                    }
                    check(ownCount(helper, a, Items.COBBLESTONE) == 64 * per, "items should fill vault A first");
                    check(ownCount(helper, b, Items.COBBLESTONE) == 64, "overflow should spill into vault B");

                    IItemHandler viaB = items(helper, b);
                    int extracted = 0;
                    for (int slot = 0; slot < viaB.getSlots(); slot++) {
                        extracted += viaB.extractItem(slot, 64, false).getCount();
                    }
                    check(extracted == 64 * (per + 1), "vault B should reach every item, extracted " + extracted);
                    check(ownCount(helper, a, Items.COBBLESTONE) == 0, "vault A should be empty after extracting via B");
                })
                .thenSucceed();
    }

    /** Callers that cache by identity need the same handler object on every lookup. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void linkedHandlerIsStableOverTime(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos link = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);
        IItemHandler[] first = new IItemHandler[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                    helper.setBlock(link, linker());
                })
                .thenIdle(3)
                .thenExecute(() -> first[0] = items(helper, a))
                .thenIdle(60)
                .thenExecute(() -> check(items(helper, a) == first[0],
                        "vault A should hand out the same linked handler object"))
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void removingLinkerSplitsVaults(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos link = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);
        int[] single = new int[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    single[0] = slots(helper, a);
                    helper.setBlock(link, linker());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    check(slots(helper, a) == 2 * single[0], "should be linked before the split");
                    ItemHandlerHelper.insertItemStacked(items(helper, b), new ItemStack(Items.DIAMOND, 10), false);
                    helper.setBlock(link, Blocks.AIR.defaultBlockState());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    check(slots(helper, a) == single[0], "vault A should be on its own again, got " + slots(helper, a));
                    check(slots(helper, b) == single[0], "vault B should be on its own again, got " + slots(helper, b));
                    check(ownCount(helper, b, Items.DIAMOND) == 10, "items should stay in the vault they were in");
                    check(count(items(helper, a), Items.DIAMOND) == 0, "vault A should no longer reach vault B");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void chainedLinkersJoinThreeVaults(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(5, 1, 1);
        BlockPos c = new BlockPos(3, 1, 4);
        int[] single = new int[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                    helper.setBlock(c, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    single[0] = slots(helper, a);
                    for (BlockPos pos : new BlockPos[] { new BlockPos(2, 1, 1), new BlockPos(3, 1, 1),
                            new BlockPos(4, 1, 1), new BlockPos(3, 1, 2), new BlockPos(3, 1, 3) }) {
                        helper.setBlock(pos, linker());
                    }
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    int expected = 3 * single[0];
                    check(slots(helper, a) == expected, "A should see all three vaults, got " + slots(helper, a));
                    check(slots(helper, b) == expected, "B should see all three vaults, got " + slots(helper, b));
                    check(slots(helper, c) == expected, "C should see all three vaults, got " + slots(helper, c));
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void cachedHandlersFollowLinkChanges(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos link = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);
        int[] single = new int[1];
        @SuppressWarnings("unchecked")
        BlockCapabilityCache<IItemHandler, @Nullable Direction>[] cache = new BlockCapabilityCache[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    // What a funnel or chute does: cache the handler and only re-query when invalidated.
                    cache[0] = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, helper.getLevel(),
                            helper.absolutePos(a), Direction.UP);
                    single[0] = cachedSlots(cache[0]);
                    check(single[0] == slots(helper, a), "cache should match a fresh lookup");
                    helper.setBlock(link, linker());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    check(cachedSlots(cache[0]) == 2 * single[0], "cached handler should pick up the link, got "
                            + cachedSlots(cache[0]));
                    helper.setBlock(link, Blocks.AIR.defaultBlockState());
                })
                .thenIdle(3)
                .thenExecute(() -> check(cachedSlots(cache[0]) == single[0],
                        "cached handler should drop the link, got " + cachedSlots(cache[0])))
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void linkedVaultsCountAsOneInventory(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos link = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                    helper.setBlock(link, linker());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    InventoryIdentifier idA = identifier(helper, a);
                    InventoryIdentifier idB = identifier(helper, b);
                    check(idA != null && idA.equals(idB), "linked vaults should share an identifier: " + idA + " / " + idB);
                    check(idA.contains(new BlockFace(helper.absolutePos(b), Direction.UP)),
                            "the identifier should cover vault B");
                    helper.setBlock(link, Blocks.AIR.defaultBlockState());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    InventoryIdentifier idA = identifier(helper, a);
                    InventoryIdentifier idB = identifier(helper, b);
                    check(idA != null && !idA.equals(idB), "split vaults should have separate identifiers");
                    check(!idA.contains(new BlockFace(helper.absolutePos(b), Direction.UP)),
                            "vault A's identifier should no longer cover vault B");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void growingVaultStaysLinked(GameTestHelper helper) {
        BlockPos extension = new BlockPos(1, 1, 1);
        BlockPos a = new BlockPos(2, 1, 1);
        BlockPos link = new BlockPos(3, 1, 1);
        BlockPos b = new BlockPos(4, 1, 1);
        int[] single = new int[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    single[0] = slots(helper, a);
                    helper.setBlock(link, linker());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    check(slots(helper, b) == 2 * single[0], "should be linked before growing");
                    // Extends vault A into a 2-long vault whose controller moves to the new block.
                    helper.setBlock(extension, vault());
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    int expected = 3 * single[0];
                    check(slots(helper, b) == expected, "B should see the grown vault, got " + slots(helper, b));
                    check(slots(helper, extension) == expected,
                            "the new vault block should see the network, got " + slots(helper, extension));
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void siloLinksWithVault(GameTestHelper helper) {
        Block silo = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("create_connected", "item_silo")).orElse(null);
        if (silo == null) {
            helper.succeed(); // Create: Connected isn't installed.
            return;
        }
        BlockPos s = new BlockPos(1, 1, 1);
        BlockPos link = new BlockPos(2, 1, 1);
        BlockPos v = new BlockPos(3, 1, 1);
        int[] own = new int[2];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(s, silo.defaultBlockState());
                    helper.setBlock(v, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    own[0] = slots(helper, s);
                    own[1] = slots(helper, v);
                    helper.setBlock(link, linker());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    int expected = own[0] + own[1];
                    check(slots(helper, s) == expected, "silo should see the vault too, got " + slots(helper, s));
                    check(slots(helper, v) == expected, "vault should see the silo too, got " + slots(helper, v));
                    InventoryIdentifier idS = identifier(helper, s);
                    check(idS != null && idS.equals(identifier(helper, v)), "silo and vault should share an identifier");
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------------------

    private static BlockState vault() {
        return AllBlocks.ITEM_VAULT.getDefaultState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X);
    }

    private static BlockState linker() {
        return AddonBlocks.STORAGE_LINKER.get().defaultBlockState();
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(pos), Direction.UP);
        if (handler == null) {
            throw new GameTestAssertException("no item handler at " + pos);
        }
        return handler;
    }

    private static int slots(GameTestHelper helper, BlockPos pos) {
        return items(helper, pos).getSlots();
    }

    private static int cachedSlots(BlockCapabilityCache<IItemHandler, @Nullable Direction> cache) {
        IItemHandler handler = cache.getCapability();
        if (handler == null) {
            throw new GameTestAssertException("cache has no item handler");
        }
        return handler.getSlots();
    }

    private static int count(IItemHandler handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Items stored in this block's own vault, ignoring any linking. */
    private static int ownCount(GameTestHelper helper, BlockPos pos, Item item) {
        com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity vault = helper.getBlockEntity(pos);
        return count(vault.getInventoryOfBlock(), item);
    }

    @Nullable
    private static InventoryIdentifier identifier(GameTestHelper helper, BlockPos pos) {
        return InventoryIdentifier.get(helper.getLevel(), new BlockFace(helper.absolutePos(pos), Direction.UP));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

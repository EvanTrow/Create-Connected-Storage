package tech.trowbridge.connectedstorage.gametest;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;

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
import tech.trowbridge.connectedstorage.ConnectedStorage;
import tech.trowbridge.connectedstorage.content.LinkSide;
import tech.trowbridge.connectedstorage.content.ItemPipeBlock;
import tech.trowbridge.connectedstorage.index.AddonBlocks;

/**
 * Run with {@code ./gradlew runGameTestServer}. Each test builds vaults, pipes and pumps in an empty 9x4x9 area.
 * Pumps are driven by a creative motor turning a cogwheel that sits on top of the pump.
 */
@GameTestHolder(ConnectedStorage.MOD_ID)
@PrefixGameTestTemplate(false)
public class LinkingGameTests {

    private static final String EMPTY = "empty";
    /** Reach 4 pipe blocks. */
    private static final int FAST = 64;
    /** Below the 16 RPM minimum: reach 0. */
    private static final int SLOW = 8;

    // ---------------------------------------------------------------------------------------------------------
    // Pumps

    @GameTest(template = EMPTY)
    public static void pumpBetweenVaultsSharesOneInventory(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
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
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
                .thenExecute(() -> {
                    int per = single[0];
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

    @GameTest(template = EMPTY)
    public static void pipesThroughPumpJoinVaults(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(3, 1, 1);
        BlockPos b = new BlockPos(5, 1, 1);
        int[] single = new int[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    single[0] = slots(helper, a);
                    helper.setBlock(new BlockPos(2, 1, 1), pipe());
                    helper.setBlock(new BlockPos(4, 1, 1), pipe());
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
                .thenExecute(() -> {
                    BlockState left = helper.getBlockState(new BlockPos(2, 1, 1));
                    check(ItemPipeBlock.getSide(left, Direction.EAST) == LinkSide.PIPE,
                            "the pipe should connect to the pump, state " + left);
                    check(slots(helper, a) == 2 * single[0], "A should reach B through the pump, got " + slots(helper, a));
                    check(slots(helper, b) == 2 * single[0], "B should reach A through the pump, got " + slots(helper, b));
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void pipeWithoutPumpDoesNothing(GameTestHelper helper) {
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
                    helper.setBlock(link, pipe());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockState linkState = helper.getBlockState(link);
                    check(ItemPipeBlock.getSide(linkState, Direction.WEST) == LinkSide.STORAGE
                            && ItemPipeBlock.getSide(linkState, Direction.EAST) == LinkSide.STORAGE,
                            "pipe should still connect to both vaults, state " + linkState);
                    check(slots(helper, a) == single[0], "without a pump vault A should stay alone, got " + slots(helper, a));
                    check(slots(helper, b) == single[0], "without a pump vault B should stay alone, got " + slots(helper, b));
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pumpNeedsEnoughSpeed(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
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
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, SLOW))
                .thenIdle(5)
                .thenExecute(() -> {
                    check(cachedSlots(cache[0]) == single[0], "a pump at " + SLOW + " RPM shouldn't link, got "
                            + cachedSlots(cache[0]));
                    setMotorSpeed(helper, pump, FAST);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    check(cachedSlots(cache[0]) == 2 * single[0], "speeding up to " + FAST + " RPM should link, got "
                            + cachedSlots(cache[0]));
                    setMotorSpeed(helper, pump, 0);
                })
                .thenIdle(5)
                .thenExecute(() -> check(cachedSlots(cache[0]) == single[0],
                        "stopping the pump should unlink, got " + cachedSlots(cache[0])))
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void pumpOnlyConnectsAlongItsAxis(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
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
                    // Facing south: its ends point at air, its sides at the vaults.
                    placePoweredPump(helper, pump, Direction.SOUTH);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
                .thenExecute(() -> check(slots(helper, a) == single[0],
                        "a sideways pump shouldn't link, got " + slots(helper, a)))
                .thenSucceed();
    }

    /** A stopped pump cuts off whatever is behind it, even when another pump in the network is running. */
    @GameTest(template = EMPTY)
    public static void unpoweredPumpBlocksTheLink(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos powered = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);
        BlockPos pipe = new BlockPos(4, 1, 1);
        BlockPos stopped = new BlockPos(5, 1, 1);
        BlockPos c = new BlockPos(6, 1, 1);
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
                    placePoweredPump(helper, powered, Direction.EAST);
                    helper.setBlock(pipe, pipe());
                    helper.setBlock(stopped, AddonBlocks.ITEM_PUMP.get().defaultBlockState()
                            .setValue(DirectionalKineticBlock.FACING, Direction.EAST));
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, powered, FAST))
                .thenIdle(5)
                .thenExecute(() -> {
                    int per = single[0];
                    check(slots(helper, a) == 2 * per, "A should link with B through the running pump, got " + slots(helper, a));
                    check(slots(helper, b) == 2 * per, "B should link with A only, got " + slots(helper, b));
                    check(slots(helper, c) == per, "C sits behind a stopped pump and should stay alone, got " + slots(helper, c));
                })
                .thenSucceed();
    }

    /** A pump reaches 1 pipe block per 16 RPM, counted along the pipe from the pump. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pumpReachDependsOnSpeed(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(7, 1, 1); // after 4 pipe blocks
        int[] single = new int[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    single[0] = slots(helper, a);
                    for (int x = 3; x <= 6; x++) {
                        helper.setBlock(new BlockPos(x, 1, 1), pipe());
                    }
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, 48)) // reach 3: one pipe short
                .thenIdle(5)
                .thenExecute(() -> {
                    check(slots(helper, a) == single[0], "48 RPM reaches 3 pipes, B is 4 away, got " + slots(helper, a));
                    setMotorSpeed(helper, pump, 64); // reach 4
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    check(slots(helper, a) == 2 * single[0], "64 RPM reaches 4 pipes, got " + slots(helper, a));
                    setMotorSpeed(helper, pump, 48);
                })
                .thenIdle(5)
                .thenExecute(() -> check(slots(helper, a) == single[0],
                        "slowing back down should drop B, got " + slots(helper, a)))
                .thenSucceed();
    }

    /** Pipes past one pump's reach can be powered by another running pump further along the line. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void secondPumpExtendsReach(GameTestHelper helper) {
        BlockPos a = new BlockPos(0, 1, 1);
        BlockPos first = new BlockPos(1, 1, 1);
        BlockPos second = new BlockPos(4, 1, 1);
        BlockPos b = new BlockPos(6, 1, 1);
        int[] single = new int[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    single[0] = slots(helper, a);
                    // a, pump, pipe, pipe, pump, pipe, b
                    helper.setBlock(new BlockPos(2, 1, 1), pipe());
                    helper.setBlock(new BlockPos(3, 1, 1), pipe());
                    helper.setBlock(new BlockPos(5, 1, 1), pipe());
                    placePoweredPump(helper, first, Direction.EAST);
                    placePoweredPump(helper, second, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    setMotorSpeed(helper, first, 16); // reach 1
                    setMotorSpeed(helper, second, 0);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    check(slots(helper, a) == single[0], "one pump at 16 RPM can't reach B, got " + slots(helper, a));
                    setMotorSpeed(helper, second, 16); // its reach covers the pipe the first one can't
                })
                .thenIdle(5)
                .thenExecute(() -> check(slots(helper, a) == 2 * single[0],
                        "two pumps at 16 RPM should cover the whole line, got " + slots(helper, a)))
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Handlers held the way Create's funnels, chutes and packagers hold them: looked up once, kept until a
    // neighbouring block changes. They ignore capability invalidation, so the handler itself has to follow links.

    @GameTest(template = EMPTY)
    public static void heldHandlerDropsLinkWhenPipeBreaks(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(3, 1, 1);
        BlockPos rightPipe = new BlockPos(4, 1, 1);
        BlockPos b = new BlockPos(5, 1, 1);
        int[] single = new int[1];
        IItemHandler[] held = new IItemHandler[2];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    single[0] = slots(helper, a);
                    helper.setBlock(new BlockPos(2, 1, 1), pipe());
                    helper.setBlock(rightPipe, pipe());
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
                .thenExecute(() -> {
                    held[0] = items(helper, a);
                    held[1] = items(helper, b);
                    check(held[0].getSlots() == 2 * single[0], "should be linked first, got " + held[0].getSlots());
                    helper.setBlock(rightPipe, Blocks.AIR.defaultBlockState());
                })
                .thenIdle(3)
                .thenExecute(() -> checkHeldAlone(helper, held, single[0], a, b))
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void heldHandlerDropsLinkWhenPumpSlows(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);
        int[] single = new int[1];
        IItemHandler[] held = new IItemHandler[2];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    single[0] = slots(helper, a);
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
                .thenExecute(() -> {
                    held[0] = items(helper, a);
                    held[1] = items(helper, b);
                    check(held[0].getSlots() == 2 * single[0], "should be linked first, got " + held[0].getSlots());
                    setMotorSpeed(helper, pump, SLOW);
                })
                .thenIdle(5)
                .thenExecute(() -> checkHeldAlone(helper, held, single[0], a, b))
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void heldHandlerPicksUpNewLink(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);
        int[] single = new int[1];
        IItemHandler[] held = new IItemHandler[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, SLOW))
                .thenIdle(5)
                .thenExecute(() -> {
                    held[0] = items(helper, a);
                    single[0] = held[0].getSlots();
                    setMotorSpeed(helper, pump, FAST);
                })
                .thenIdle(5)
                .thenExecute(() -> check(held[0].getSlots() == 2 * single[0],
                        "a handler grabbed before linking should see the link, got " + held[0].getSlots()))
                .thenSucceed();
    }

    /** Held handlers for A and B should each see only their own vault, and inserting must not spill over. */
    private static void checkHeldAlone(GameTestHelper helper, IItemHandler[] held, int per, BlockPos a, BlockPos b) {
        check(held[0].getSlots() == per, "held handler for A should drop the link, got " + held[0].getSlots());
        check(held[1].getSlots() == per, "held handler for B should drop the link, got " + held[1].getSlots());
        for (int i = 0; i <= per; i++) {
            ItemHandlerHelper.insertItemStacked(held[0], new ItemStack(Items.COBBLESTONE, 64), false);
        }
        check(ownCount(helper, b, Items.COBBLESTONE) == 0, "inserting into A must not reach B any more");
        check(ownCount(helper, a, Items.COBBLESTONE) == 64 * per, "A should simply fill up");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Ponder

    /** Every block in the Ponder schematic must parse to exactly the state written (no unknown ids or values). */
    @GameTest(template = EMPTY)
    public static void ponderSchematicBlocksResolve(GameTestHelper helper) {
        String path = "/assets/connectedstorage/ponder/item_pump.nbt";
        try (java.io.InputStream in = LinkingGameTests.class.getResourceAsStream(path)) {
            check(in != null, "missing " + path);
            net.minecraft.nbt.CompoundTag root = net.minecraft.nbt.NbtIo.readCompressed(in,
                    net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            net.minecraft.nbt.ListTag palette = root.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
            check(!palette.isEmpty(), "empty palette");
            for (int i = 0; i < palette.size(); i++) {
                net.minecraft.nbt.CompoundTag entry = palette.getCompound(i);
                BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(
                        helper.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK), entry);
                check(!state.isAir(), "unknown block " + entry);
                net.minecraft.nbt.CompoundTag written = entry.getCompound("Properties");
                for (String key : written.getAllKeys()) {
                    String actual = state.getValues().entrySet().stream()
                            .filter(e -> e.getKey().getName().equals(key))
                            .map(e -> net.minecraft.Util.getPropertyName(e.getKey(), e.getValue()))
                            .findFirst().orElse(null);
                    check(written.getString(key).equals(actual),
                            "property " + key + " of " + entry.getString("Name") + " became " + actual);
                }
            }
        } catch (java.io.IOException e) {
            throw new GameTestAssertException("couldn't read " + path + ": " + e);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Network behaviour

    @GameTest(template = EMPTY)
    public static void removingPumpSplitsVaults(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
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
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
                .thenExecute(() -> {
                    check(slots(helper, a) == 2 * single[0], "should be linked before the split");
                    ItemHandlerHelper.insertItemStacked(items(helper, b), new ItemStack(Items.DIAMOND, 10), false);
                    helper.setBlock(pump, Blocks.AIR.defaultBlockState());
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
    public static void chainedPipesJoinThreeVaults(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(5, 1, 1);
        BlockPos c = new BlockPos(4, 1, 4);
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
                    placePoweredPump(helper, pump, Direction.EAST);
                    for (BlockPos pos : new BlockPos[] { new BlockPos(3, 1, 1), new BlockPos(4, 1, 1),
                            new BlockPos(4, 1, 2), new BlockPos(4, 1, 3) }) {
                        helper.setBlock(pos, pipe());
                    }
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
                .thenExecute(() -> {
                    int expected = 3 * single[0];
                    check(slots(helper, a) == expected, "A should see all three vaults, got " + slots(helper, a));
                    check(slots(helper, b) == expected, "B should see all three vaults, got " + slots(helper, b));
                    check(slots(helper, c) == expected, "C should see all three vaults, got " + slots(helper, c));
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void linkedVaultsCountAsOneInventory(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
                .thenExecute(() -> {
                    InventoryIdentifier idA = identifier(helper, a);
                    InventoryIdentifier idB = identifier(helper, b);
                    check(idA != null && idA.equals(idB), "linked vaults should share an identifier: " + idA + " / " + idB);
                    check(idA.contains(new BlockFace(helper.absolutePos(b), Direction.UP)),
                            "the identifier should cover vault B");
                    setMotorSpeed(helper, pump, 0);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    InventoryIdentifier idA = identifier(helper, a);
                    InventoryIdentifier idB = identifier(helper, b);
                    check(idA != null && !idA.equals(idB), "unpowered vaults should have separate identifiers");
                })
                .thenSucceed();
    }

    /** Callers that cache by identity need the same handler object on every lookup. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void linkedHandlerIsStableOverTime(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);
        IItemHandler[] first = new IItemHandler[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
                .thenExecute(() -> first[0] = items(helper, a))
                .thenIdle(60)
                .thenExecute(() -> check(items(helper, a) == first[0],
                        "vault A should hand out the same linked handler object"))
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void growingVaultStaysLinked(GameTestHelper helper) {
        BlockPos extension = new BlockPos(0, 1, 1);
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);
        int[] single = new int[1];
        IItemHandler[] heldA = new IItemHandler[1];

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(a, vault());
                    helper.setBlock(b, vault());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    single[0] = slots(helper, a);
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
                .thenExecute(() -> {
                    check(slots(helper, b) == 2 * single[0], "should be linked before growing");
                    heldA[0] = items(helper, a);
                    // Extends vault A into a 2-long vault whose controller moves to the new block.
                    helper.setBlock(extension, vault());
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    int expected = 3 * single[0];
                    check(slots(helper, b) == expected, "B should see the grown vault, got " + slots(helper, b));
                    check(slots(helper, extension) == expected,
                            "the new vault block should see the network, got " + slots(helper, extension));
                    check(heldA[0].getSlots() == expected,
                            "a handler held from before the controller moved should follow it, got " + heldA[0].getSlots());
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
        BlockPos pump = new BlockPos(2, 1, 1);
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
                    placePoweredPump(helper, pump, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> setMotorSpeed(helper, pump, FAST))
                .thenIdle(5)
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

    private static BlockState pipe() {
        return AddonBlocks.ITEM_PIPE.get().defaultBlockState();
    }

    /**
     * A pump at {@code pos} with a cogwheel on top (same axis, so they mesh) and a creative motor driving that
     * cogwheel from behind. The motor starts at its default speed; set it with {@link #setMotorSpeed}.
     */
    private static void placePoweredPump(GameTestHelper helper, BlockPos pos, Direction facing) {
        Direction.Axis axis = facing.getAxis();
        BlockPos cog = pos.above();
        BlockPos motor = cog.relative(Direction.get(Direction.AxisDirection.NEGATIVE, axis));
        helper.setBlock(pos, AddonBlocks.ITEM_PUMP.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, facing));
        helper.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, axis));
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.get(Direction.AxisDirection.POSITIVE, axis)));
    }

    private static void setMotorSpeed(GameTestHelper helper, BlockPos pump, int rpm) {
        BlockState state = helper.getBlockState(pump);
        Direction.Axis axis = state.getValue(DirectionalKineticBlock.FACING).getAxis();
        BlockPos motor = pump.above().relative(Direction.get(Direction.AxisDirection.NEGATIVE, axis));
        CreativeMotorBlockEntity be = helper.getBlockEntity(motor);
        be.generatedSpeed.setValue(rpm);
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
        ItemVaultBlockEntity vault = helper.getBlockEntity(pos);
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

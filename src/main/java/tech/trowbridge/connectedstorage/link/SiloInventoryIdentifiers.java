package tech.trowbridge.connectedstorage.link;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.jetbrains.annotations.Nullable;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.AllInventoryIdentifiers;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create: Connected's Item Silo has a {@code getInvId()} like Create's vault, but registers no finder that uses
 * it, so Packagers treat every silo block as a separate inventory. This registers one (only if nothing else has),
 * returning the linked network's identifier for linked silos and the silo's own identifier otherwise.
 */
public final class SiloInventoryIdentifiers {

    private static final ResourceLocation ITEM_SILO = ResourceLocation.fromNamespaceAndPath("create_connected", "item_silo");

    @Nullable
    private static MethodHandle getInvId;

    private SiloInventoryIdentifiers() {
    }

    public static void register() {
        Block silo = BuiltInRegistries.BLOCK.getOptional(ITEM_SILO).orElse(null);
        if (silo == null || InventoryIdentifier.REGISTRY.get(silo) != null) {
            return;
        }
        try {
            InventoryIdentifier.REGISTRY.register(silo, SiloInventoryIdentifiers::find);
        } catch (IllegalArgumentException e) {
            LogUtils.getLogger().debug("Item Silo already has an inventory identifier finder", e);
        }
    }

    @Nullable
    private static InventoryIdentifier find(Level level, BlockState state, BlockFace face) {
        BlockEntity blockEntity = level.getBlockEntity(face.getPos());
        if (blockEntity == null) {
            return null;
        }
        InventoryIdentifier linked = LinkNetworks.identifierFor(blockEntity);
        if (linked != null) {
            return linked;
        }
        InventoryIdentifier own = ownIdentifier(blockEntity);
        return own != null ? own : AllInventoryIdentifiers.fallback(level, state, face);
    }

    @Nullable
    private static InventoryIdentifier ownIdentifier(BlockEntity blockEntity) {
        try {
            if (getInvId == null) {
                getInvId = MethodHandles.publicLookup().findVirtual(blockEntity.getClass(), "getInvId",
                        MethodType.methodType(InventoryIdentifier.class));
            }
            return (InventoryIdentifier) getInvId.invoke(blockEntity);
        } catch (Throwable e) {
            return null;
        }
    }
}

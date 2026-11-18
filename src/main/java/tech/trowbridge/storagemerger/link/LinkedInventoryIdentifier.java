package tech.trowbridge.storagemerger.link;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.packager.InventoryIdentifier;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;

/**
 * Identifies a whole storage network as one inventory, so Packagers attached to different members of the same
 * network don't count (or pull) the same items twice.
 */
public final class LinkedInventoryIdentifier implements InventoryIdentifier {

    private final Set<BlockPos> members;
    private final Set<BlockPos> blocks;

    LinkedInventoryIdentifier(Set<BlockPos> members, Set<BlockPos> blocks) {
        this.members = members;
        this.blocks = blocks;
    }

    @Override
    public boolean contains(BlockFace face) {
        return blocks.contains(face.getPos());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj instanceof LinkedInventoryIdentifier other && other.members.equals(members);
    }

    @Override
    public int hashCode() {
        return members.hashCode();
    }

    @Override
    public String toString() {
        return "LinkedInventoryIdentifier" + members;
    }
}

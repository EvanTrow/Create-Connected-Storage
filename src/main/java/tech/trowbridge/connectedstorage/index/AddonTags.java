package tech.trowbridge.connectedstorage.index;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import tech.trowbridge.connectedstorage.ConnectedStorage;

public class AddonTags {

    /** Storage blocks Item Pipes and Pumps connect to and merge. */
    public static final TagKey<Block> LINKABLE_STORAGE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ConnectedStorage.MOD_ID, "linkable_storage"));
}

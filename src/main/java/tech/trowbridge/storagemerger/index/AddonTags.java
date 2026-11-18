package tech.trowbridge.storagemerger.index;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import tech.trowbridge.storagemerger.StorageMerger;

public class AddonTags {

    /** Storage blocks a Storage Linker connects to and merges. */
    public static final TagKey<Block> LINKABLE_STORAGE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(StorageMerger.MOD_ID, "linkable_storage"));
}

package tech.trowbridge.storagemerger.content;

import net.minecraft.util.StringRepresentable;

/**
 * What a Storage Linker connects to on one of its six sides.
 */
public enum LinkSide implements StringRepresentable {
    /** Nothing to connect to. */
    NONE("none"),
    /** Another Storage Linker: the network continues through this side. */
    PIPE("pipe"),
    /** A linkable storage block (Item Vault, Item Silo): it joins the network. */
    STORAGE("storage");

    private final String name;

    LinkSide(String name) {
        this.name = name;
    }

    public boolean isConnected() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

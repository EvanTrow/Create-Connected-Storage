package tech.trowbridge.connectedstorage.content;

import net.minecraft.util.StringRepresentable;

/**
 * What an Item Pipe connects to on one of its six sides.
 */
public enum LinkSide implements StringRepresentable {
    /** Nothing to connect to. */
    NONE("none"),
    /** Another Item Pipe or an Item Pump: the network continues through this side. */
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

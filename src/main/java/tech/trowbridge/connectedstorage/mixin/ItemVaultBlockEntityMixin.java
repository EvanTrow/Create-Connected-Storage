package tech.trowbridge.connectedstorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;
import tech.trowbridge.connectedstorage.link.LinkNetworks;

/** Linked vaults report their whole network as one inventory to Packagers and Stock Keepers. */
@Mixin(ItemVaultBlockEntity.class)
public abstract class ItemVaultBlockEntityMixin {

    @Inject(method = "getInvId", at = @At("HEAD"), cancellable = true)
    private void connectedstorage$linkedInvId(CallbackInfoReturnable<InventoryIdentifier> cir) {
        InventoryIdentifier linked = LinkNetworks.identifierFor((BlockEntity) (Object) this);
        if (linked != null) {
            cir.setReturnValue(linked);
        }
    }
}

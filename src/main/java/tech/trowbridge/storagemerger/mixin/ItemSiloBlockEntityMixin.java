package tech.trowbridge.storagemerger.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.api.packager.InventoryIdentifier;

import net.minecraft.world.level.block.entity.BlockEntity;
import tech.trowbridge.storagemerger.link.LinkNetworks;

/**
 * Same as {@link ItemVaultBlockEntityMixin}, for Create: Connected's Item Silo. Targeted by name so this mod
 * doesn't need Create: Connected to compile or run; without it the mixin is skipped.
 */
@Pseudo
@Mixin(targets = "com.hlysine.create_connected.content.itemsilo.ItemSiloBlockEntity")
public abstract class ItemSiloBlockEntityMixin {

    @Inject(method = "getInvId", at = @At("HEAD"), cancellable = true, require = 0)
    private void storagemerger$linkedInvId(CallbackInfoReturnable<InventoryIdentifier> cir) {
        InventoryIdentifier linked = LinkNetworks.identifierFor((BlockEntity) (Object) this);
        if (linked != null) {
            cir.setReturnValue(linked);
        }
    }
}

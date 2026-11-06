package com.warfactory.medical.mixin.ragdoll;

import com.warfactory.medical.ragdoll.Ragdolls;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.sarocesch.sarosragdoll.network.PacketGuiAction", remap = false)
public abstract class SaroGuiMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void wfmedical$rejectStockGuiActions(CallbackInfo ci) {
        // Gate the server handler too: hiding the stock GUI alone does not prevent its packets.
        if (ModList.get().isLoaded("corpse") && Ragdolls.enabled()) ci.cancel();
    }
}

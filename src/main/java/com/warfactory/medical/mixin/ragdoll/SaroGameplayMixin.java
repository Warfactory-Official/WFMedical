package com.warfactory.medical.mixin.ragdoll;

import com.warfactory.medical.ragdoll.Ragdolls;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.sarocesch.sarosragdoll.SarosRagdollMod", remap = false)
public abstract class SaroGameplayMixin {
    @Inject(method = {"onDamage", "onInteract", "onLogout", "onCommands"}, at = @At("HEAD"), cancellable = true)
    private static void wfmedical$keepMedicalAuthority(CallbackInfo ci) {
        if (ModList.get().isLoaded("corpse") && Ragdolls.enabled()) ci.cancel();
    }
}

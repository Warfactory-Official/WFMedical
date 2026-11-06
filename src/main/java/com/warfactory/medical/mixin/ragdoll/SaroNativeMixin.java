package com.warfactory.medical.mixin.ragdoll;

import com.jme3.system.JmeSystem;
import com.jme3.system.NativeLibraryLoader;
import com.warfactory.medical.ragdoll.Ragdolls;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "de.sarocesch.sarosragdoll.ragdoll.physics.NativeInstaller", remap = false)
public abstract class SaroNativeMixin {
    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Ljava/lang/System;load(Ljava/lang/String;)V"))
    private static void wfmedical$loadWithPhysicsClassLoader(String library) {
        if (!ModList.get().isLoaded("corpse") || !Ragdolls.enabled()) {
            System.load(library);
            return;
        }
        // NeoForge puts Bullet in the plugin layer; JNI must load through Bullet's own class loader.
        Path directory = Path.of(library).getParent().resolve("wfmedical");
        Path alias = directory.resolve(JmeSystem.getPlatform() + "ReleaseSp_" + System.mapLibraryName("bulletjme"));
        try {
            Files.createDirectories(directory);
            Files.copy(Path.of(library), alias, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot prepare the bundled Bullet native", error);
        }
        if (!NativeLibraryLoader.loadLibbulletjme(true, directory.toFile(), "Release", "Sp"))
            throw new IllegalStateException("Cannot load the bundled Bullet native through its class loader");
    }
}

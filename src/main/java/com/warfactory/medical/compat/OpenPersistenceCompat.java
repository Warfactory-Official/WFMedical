package com.warfactory.medical.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;


public final class OpenPersistenceCompat {

    public static final String MOD_ID = "openpersistence";

    private static final ResourceLocation BODY_TYPE = new ResourceLocation(MOD_ID, "player");

    private static volatile boolean ownerMethodResolved;
    private static volatile Method ownerMethod;

    private OpenPersistenceCompat() {
    }


    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }


    public static boolean isPersistentBody(Entity entity) {
        if (entity == null || !isLoaded()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return BODY_TYPE.equals(key);
    }


    public static Optional<UUID> bodyOwner(Entity body) {
        if (!isPersistentBody(body)) {
            return Optional.empty();
        }
        Method m = resolveOwnerMethod(body.getClass());
        if (m == null) {
            return Optional.empty();
        }
        try {
            Object result = m.invoke(body);
            if (result instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof UUID uuid) {
                return Optional.of(uuid);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Treat any reflective failure as "unknown owner"
        }
        return Optional.empty();
    }

    private static Method resolveOwnerMethod(Class<?> bodyClass) {
        if (!ownerMethodResolved) {
            synchronized (OpenPersistenceCompat.class) {
                if (!ownerMethodResolved) {
                    Method resolved = null;
                    try {
                        resolved = bodyClass.getMethod("getPlayerUUID");
                    } catch (NoSuchMethodException ignored) {
                        // Older/newer OP without this accessor
                    }
                    ownerMethod = resolved;
                    ownerMethodResolved = true;
                }
            }
        }
        return ownerMethod;
    }
}

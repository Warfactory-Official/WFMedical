package com.warfactory.medical.compat.tacz;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * CLIENT-only dev harvest tool for {@link TaczArmPose}
 */
public final class TaczPoseCaptureClient {

    private static final String THIRD_PERSON_MANAGER = "com.tacz.guns.api.client.other.ThirdPersonManager";

    private TaczPoseCaptureClient() {
    }

    /**
     * Invoke TACZ's third-person animation with dummy parts and emit the posed rotations via {@code out}.
     */
    public static void dump(LivingEntity holder, Consumer<String> out) {
        try {
            Class<?> managerClass = Class.forName(THIRD_PERSON_MANAGER);
            Method getAnimation = managerClass.getMethod("getAnimation", String.class);
            getAnimation.setAccessible(true);
            Object animation = getAnimation.invoke(null, "default");
            if (animation == null) {
                out.accept("[wfmedical] TACZ third-person animation 'default' not found.");
                return;
            }
            Method hold = accessible(animation.getClass(), "animateGunHold", LivingEntity.class,
                    ModelPart.class, ModelPart.class, ModelPart.class, ModelPart.class);
            Method aim = accessible(animation.getClass(), "animateGunAim", LivingEntity.class,
                    ModelPart.class, ModelPart.class, ModelPart.class, ModelPart.class, float.class);

            ModelPart[] parts = {emptyPart(), emptyPart(), emptyPart(), emptyPart()};

            reset(parts);
            hold.invoke(animation, holder, parts[0], parts[1], parts[2], parts[3]);
            out.accept("HOLD  " + describe(parts));

            reset(parts);
            aim.invoke(animation, holder, parts[0], parts[1], parts[2], parts[3], 1.0F);
            out.accept("AIM   " + describe(parts));
            out.accept("[wfmedical] map the 4 parts (identify right/left arm by which rotated) -> TaczArmPose.");
        } catch (Throwable t) {
            out.accept("[wfmedical] TACZ pose capture failed: " + t);
        }
    }

    private static Method accessible(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        Method m = owner.getMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private static ModelPart emptyPart() {
        return new ModelPart(List.of(), Map.of());
    }

    private static void reset(ModelPart[] parts) {
        for (ModelPart p : parts) {
            p.x = 0.0F;
            p.y = 0.0F;
            p.z = 0.0F;
            p.xRot = 0.0F;
            p.yRot = 0.0F;
            p.zRot = 0.0F;
        }
    }

    private static String describe(ModelPart[] parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            sb.append(String.format("p%d(x=%.3f y=%.3f z=%.3f) ", i, p.xRot, p.yRot, p.zRot));
        }
        return sb.toString();
    }
}

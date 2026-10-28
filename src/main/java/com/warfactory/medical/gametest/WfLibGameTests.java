package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.compat.wflib.WfLibArmorCompat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.lang.reflect.Method;

/** WFLib-dependent holders: only this mode's tests are registered, so the required count reflects what ran. */
@EventBusSubscriber(modid = WFMedical.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WfLibGameTests {

    private WfLibGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        boolean loaded = WfLibArmorCompat.isLoaded();
        for (Class<?> holder : new Class<?>[]{WfLibArmorGameTest.class, WfLibStrikeGameTest.class,
                WfLibStressGameTest.class}) {
            for (Method m : holder.getDeclaredMethods()) {
                WfLibMode mode = m.getAnnotation(WfLibMode.class);
                if (mode == null || mode.value() == loaded) {
                    event.register(m);
                }
            }
        }
    }
}

package com.warfactory.medical.support;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.warfactory.medical.config.MedicalConfig;

import java.lang.reflect.Constructor;

/**
 * Binds {@link MedicalConfig#SPEC} to an in-memory config so the static accessors work under JUnit.
 *
 * <p>Without this every {@code MedicalConfig.x()} throws {@code IllegalStateException: Config not loaded},
 * because a {@code ModConfigSpec.ConfigValue} only resolves once FML has handed the spec a backing file.
 * That would put most of the mod off-limits to unit tests -- {@code TraumaGenerator} reads
 * {@code fallFractureMinBlocks}, {@code TreatmentService} reads {@code clottingAgentDurationTicks},
 * {@code ClientMedicalCache} reads {@code logMedicalSync}. Correcting an empty config against the spec
 * fills every key with its declared default, i.e. the shipped configuration.
 *
 * <p>{@code ModConfigSpec.acceptConfig} takes an {@code IConfigSpec.ILoadedConfig}, which is a sealed
 * interface permitting only the package-private record {@code net.neoforged.fml.config.LoadedConfig} --
 * so it can be neither implemented nor subclassed here, and the record has to be built reflectively.
 * Its other two components (the file path and the owning {@code ModConfig}) are only read by
 * {@code save()}, which nothing here calls: the config is pre-corrected, so {@code acceptConfig} finds
 * nothing to write back.
 */
public final class TestConfig {

    private static CommentedConfig backing;

    private TestConfig() {
    }

    /** Load spec defaults once per JVM. Safe to call from every test. */
    public static synchronized void load() {
        if (MedicalConfig.SPEC.isLoaded()) {
            // FML's JUnit bootstrap may already have registered and loaded the mod's config. Adopt whatever
            // it bound rather than replacing it, so set()/get() address the config the accessors read.
            if (backing == null) {
                backing = adoptExistingBacking();
            }
            return;
        }
        CommentedConfig cfg = CommentedConfig.inMemory();
        MedicalConfig.SPEC.correct(cfg);
        backing = cfg;
        MedicalConfig.SPEC.acceptConfig(loadedConfig(cfg));
    }

    /**
     * Override a single config key for the duration of a test, then put it back.
     *
     * <p>The path is the dotted spec path, e.g. {@code "hitlocation.geometricHitLocation"}. Returns the
     * previous value so a test can restore it in a {@code finally} or an {@code @AfterEach}.
     */
    public static synchronized Object set(String path, Object value) {
        load();
        Object prev = backing.get(path);
        backing.set(path, value);
        MedicalConfig.SPEC.afterReload();
        return prev;
    }

    /** Read a config key straight off the backing config, for tests that assert on a default. */
    public static synchronized Object get(String path) {
        load();
        return backing.get(path);
    }

    private static CommentedConfig adoptExistingBacking() {
        try {
            java.lang.reflect.Field f =
                    net.neoforged.neoforge.common.ModConfigSpec.class.getDeclaredField("loadedConfig");
            f.setAccessible(true);
            Object loaded = f.get(MedicalConfig.SPEC);
            return ((net.neoforged.fml.config.IConfigSpec.ILoadedConfig) loaded).config();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Spec reports loaded but its backing config is unreachable; FML internals moved.", e);
        }
    }

    private static net.neoforged.fml.config.IConfigSpec.ILoadedConfig loadedConfig(CommentedConfig cfg) {
        try {
            Class<?> cls = Class.forName("net.neoforged.fml.config.LoadedConfig");
            Constructor<?> ctor = cls.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return (net.neoforged.fml.config.IConfigSpec.ILoadedConfig) ctor.newInstance(cfg, null, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Could not build an ILoadedConfig; FML's config internals moved. See TestConfig's javadoc.", e);
        }
    }
}

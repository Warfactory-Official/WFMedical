package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.compat.TaczCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Proves the TACZ mixins actually attach.
 *
 * <p>This is the test the rest of the TACZ integration rests on. {@code wfmedical.tacz.mixins.json}
 * sets {@code defaultRequire: 0}, which it must -- TACZ is optional, and a required injector would
 * hard-crash a server without it. The cost of that is silence: if TACZ renames {@code onBulletTick} or
 * changes the descriptor of {@code getFixedBoundingBox}, every injector quietly matches nothing, the
 * mod loads perfectly, and bullet hit capture is simply dead. Nothing in a compile catches it either,
 * because mixin targets are strings resolved at class-load time, not symbols javac can see.
 *
 * <p>Two independent assertions per target, because either alone can pass while the integration is
 * broken:
 * <ol>
 *   <li>the TACZ method the injector names still exists -- fails loudly on an upstream rename; and</li>
 *   <li>the handler was <em>merged into</em> the target class -- Mixin copies {@code wfmedical$...}
 *       methods into the target on application, so their presence is direct evidence the injector
 *       applied rather than silently no-oping.</li>
 * </ol>
 *
 * <p>Loading the target classes by name here is the point, not an accident: in an ordinary gametest run
 * nothing fires a gun, so the TACZ classes are never loaded, the mixins are never applied, and a green
 * suite says nothing at all about them.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class TaczMixinContractGameTest {

    private static final String TEMPLATE = "empty";

    private static final String BULLET = "com.tacz.guns.entity.EntityKineticBullet";
    private static final String ENTITY_UTIL = "com.tacz.guns.util.EntityUtil";
    private static final String HITBOX_HELPER = "com.tacz.guns.util.HitboxHelper";
    private static final String SHOOT = "com.tacz.guns.entity.shooter.LivingEntityShoot";
    private static final String RELOAD = "com.tacz.guns.entity.shooter.LivingEntityReload";

    /** True when TACZ is in the loading mod list, so these tests can skip cleanly without it. */
    private static boolean taczPresent() {
        return LoadingModList.get().getModFileById(TaczCompat.MOD_ID) != null;
    }

    private static Class<?> load(GameTestHelper helper, String fqn) {
        try {
            // Initialise, so the mixin transformer has definitely run over it.
            return Class.forName(fqn, true, TaczMixinContractGameTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            helper.fail("TACZ is loaded but " + fqn + " is missing -- the integration targets a class "
                    + "this TACZ build no longer has");
            throw new AssertionError("unreachable");
        }
    }

    private static void assertHasMethod(GameTestHelper helper, Class<?> owner, String name, String why) {
        boolean found = Arrays.stream(owner.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name));
        if (!found) {
            helper.fail(owner.getSimpleName() + " has no method '" + name + "'. " + why
                    + " Methods present: " + methodNames(owner));
        }
    }

    private static String methodNames(Class<?> owner) {
        return Arrays.stream(owner.getDeclaredMethods())
                .map(Method::getName).distinct().sorted().collect(Collectors.joining(", "));
    }

    /**
     * Mixin does not merge a handler under its own name: it uniquifies it, so
     * {@code wfmedical$captureHitPos} arrives in the target as
     * {@code handler$zbb000$wfmedical$captureHitPos} (and a @Redirect as {@code redirect$...$...}).
     * The infix is generated per config, so match on the suffix rather than the whole name.
     */
    private static void assertMixinApplied(GameTestHelper helper, Class<?> target, String handler) {
        boolean found = Arrays.stream(target.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(handler) || m.getName().endsWith("$" + handler));
        if (!found) {
            helper.fail(target.getSimpleName() + " never received the mixin handler '" + handler
                    + "'. Mixin merges handler methods into the target on application, so its absence "
                    + "means the injector matched nothing -- and with defaultRequire:0 that failure is "
                    + "silent. Methods present: " + methodNames(target));
        }
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void taczIsActuallyLoadedForTheseTests(GameTestHelper helper) {
        // Guards every test below from passing vacuously: they all no-op without TACZ, so if the dev
        // runtime ever stops shipping it, this is the one that says so instead of a silent green run.
        helper.assertTrue(taczPresent(),
                "TACZ is not on the gametest runtime classpath -- the TACZ integration is untested. "
                        + "Check the runtimeOnly curse.maven dependency in build.gradle.");
        helper.assertTrue(TaczCompat.isLoaded(), "TaczCompat.isLoaded() disagrees with the mod list");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void bulletMixinTargetsStillExist(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        Class<?> bullet = load(helper, BULLET);
        assertHasMethod(helper, bullet, "onHitEntity",
                "EntityKineticBulletMixin injects at its HEAD to capture the resolved hit point and the "
                        + "true per-tick ray segment; without it hit capture is dead.");
        assertHasMethod(helper, bullet, "tacAttackEntity",
                "EntityKineticBulletMixin injects here to capture the bullet's total damage, which is how "
                        + "TACZ's AP/non-AP double hurt is coalesced into one trauma.");
        assertHasMethod(helper, bullet, "onBulletTick",
                "EntityKineticBulletMixin redirects the findEntityOnPath call inside it.");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void bulletMixinIsApplied(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        Class<?> bullet = load(helper, BULLET);
        assertMixinApplied(helper, bullet, "wfmedical$captureHitPos");
        assertMixinApplied(helper, bullet, "wfmedical$captureTotalDamage");
        assertMixinApplied(helper, bullet, "wfmedical$debugFindEntityOnPath");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void entityUtilMixinTargetsStillExist(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        assertHasMethod(helper, load(helper, ENTITY_UTIL), "getHitResult",
                "EntityUtilMixin redirects the getFixedBoundingBox call inside it, which is what widens "
                        + "TACZ's own hit box to the WFMedical registration envelope.");
        assertHasMethod(helper, load(helper, ENTITY_UTIL), "findEntityOnPath",
                "EntityKineticBulletMixin's @Redirect names this as the call it replaces.");
        assertHasMethod(helper, load(helper, HITBOX_HELPER), "getFixedBoundingBox",
                "EntityUtilMixin's @Redirect names this as the call it replaces.");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void entityUtilMixinIsApplied(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        assertMixinApplied(helper, load(helper, ENTITY_UTIL), "wfmedical$envelopeBoxTacz");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void downedGatingMixinsAreApplied(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        // These two are what stop a downed player shooting and reloading. A silent miss here is a
        // gameplay bug that looks like a balance complaint rather than a broken injector.
        Class<?> shoot = load(helper, SHOOT);
        assertHasMethod(helper, shoot, "shoot", "LivingEntityShootMixin cancels it while downed.");
        assertMixinApplied(helper, shoot, "wfmedical$blockShootWhenDowned");

        Class<?> reload = load(helper, RELOAD);
        assertHasMethod(helper, reload, "reload", "LivingEntityReloadMixin cancels it while downed.");
        assertMixinApplied(helper, reload, "wfmedical$blockReloadWhenDowned");
        helper.succeed();
    }
}

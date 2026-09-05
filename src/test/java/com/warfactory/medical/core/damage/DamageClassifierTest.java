package com.warfactory.medical.core.damage;

import com.warfactory.medical.support.TestConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DeathMessageType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The message-id half of damage classification: how an unrecognised modded damage source is guessed at.
 *
 * <p>These sources carry a {@link Holder#direct} damage type, so no vanilla damage-type <em>tag</em>
 * resolves and only the name heuristics run -- which is exactly the situation for a modded source the mod
 * has never seen. The tag-driven half (vanilla fall/fire/explosion/projectile) needs registry-backed
 * holders and lives in {@code DamageClassifierGameTest}.
 */
class DamageClassifierTest {

    @BeforeAll
    static void loadConfig() {
        TestConfig.load();
    }

    private static DamageSource named(String msgId) {
        return new DamageSource(Holder.direct(new DamageType(msgId, DamageScaling.NEVER, 0.0F,
                DamageEffects.HURT, DeathMessageType.DEFAULT)));
    }

    private static DamageCategory classify(String msgId) {
        return DamageClassifier.classify(named(msgId));
    }

    @Test
    void aNullSourceIsGeneric() {
        assertEquals(DamageCategory.GENERIC, DamageClassifier.classify(null));
    }

    @Test
    void anUnrecognisedNameIsGeneric() {
        assertEquals(DamageCategory.GENERIC, classify("wibble"));
    }

    @Nested
    class NameHeuristics {

        @Test
        void gunshotWordsReadAsBallistic() {
            assertEquals(DamageCategory.BALLISTIC, classify("gunfire"));
            assertEquals(DamageCategory.BALLISTIC, classify("gunshot"));
            assertEquals(DamageCategory.BALLISTIC, classify("mymod.bullet.impact"));
            assertEquals(DamageCategory.BALLISTIC, classify("GUNFIRE"), "matching is case-insensitive");
        }

        @Test
        void burningWordsReadAsFire() {
            assertEquals(DamageCategory.FIRE, classify("fire"));
            assertEquals(DamageCategory.FIRE, classify("mymod.lava.pool"));
            assertEquals(DamageCategory.FIRE, classify("plasma_burn"));
            assertEquals(DamageCategory.FIRE, classify("flame"));
        }

        @Test
        void blastWordsReadAsExplosion() {
            assertEquals(DamageCategory.EXPLOSION, classify("mymod.explosion"));
            assertEquals(DamageCategory.EXPLOSION, classify("shaped_blast"));
            assertEquals(DamageCategory.EXPLOSION, classify("explosive_bolt"),
                    "'explos' is a substring match, so any inflection counts");
        }

        @Test
        void fallWordsReadAsFall() {
            assertEquals(DamageCategory.FALL, classify("fall"));
            assertEquals(DamageCategory.FALL, classify("mymod.fall.hard"));
        }

        @Test
        void radiologicalAndChemicalWordsGetTheirOwnCategories() {
            assertEquals(DamageCategory.RADIATION, classify("radiation"));
            assertEquals(DamageCategory.RADIATION, classify("nuclear_fallout"));
            assertEquals(DamageCategory.CHEMICAL, classify("chemical"));
            assertEquals(DamageCategory.CHEMICAL, classify("acid_spray"));
            assertEquals(DamageCategory.CHEMICAL, classify("toxic_cloud"));
            assertEquals(DamageCategory.CHEMICAL, classify("mustard_gas"));
        }

        @Test
        void wholeWordMatchingKeepsIncidentalSubstringsOut() {
            // "fire", "fall" and "gas" are matched as whole tokens, not as substrings, or half the modded
            // damage sources in existence would be misfiled.
            assertNotEquals(DamageCategory.FIRE, classify("misfired"));
            assertNotEquals(DamageCategory.FIRE, classify("campfired"));
            assertNotEquals(DamageCategory.FALL, classify("waterfalling"));
            assertNotEquals(DamageCategory.CHEMICAL, classify("gasket"));
        }

        @Test
        void aTokenIsFoundWhereverItSitsInTheName() {
            assertEquals(DamageCategory.FIRE, classify("mymod:machine/fire/vent"));
            assertEquals(DamageCategory.FALL, classify("fall.from.height"));
        }

        @Test
        void theFirstMatchingRuleWins() {
            // Fire is checked before explosion, which is checked before fall. Pinned because the order is
            // the only thing deciding a name that matches two rules.
            assertEquals(DamageCategory.FIRE, classify("fire_explosion"));
            assertEquals(DamageCategory.EXPLOSION, classify("explosion_fall"));
            assertEquals(DamageCategory.BALLISTIC, classify("bullet_fire"),
                    "ballistic is checked ahead of everything else in the name pass");
        }
    }

    @Nested
    class ConfigOverride {

        @Test
        void aConfiguredMappingBeatsTheGuesser() {
            Object prev = TestConfig.set("compat.damageSourceCategories",
                    List.of("mymod.plasma_burn=RADIATION"));
            try {
                assertEquals(DamageCategory.RADIATION, classify("mymod.plasma_burn"),
                        "the name says fire; the config says radiation and must win");
            } finally {
                TestConfig.set("compat.damageSourceCategories", prev);
            }
        }

        @Test
        void aConfiguredMappingCanNameAnOtherwiseGenericSource() {
            Object prev = TestConfig.set("compat.damageSourceCategories", List.of("wibble=BLUNT"));
            try {
                assertEquals(DamageCategory.BLUNT, classify("wibble"));
            } finally {
                TestConfig.set("compat.damageSourceCategories", prev);
            }
        }

        @Test
        void anUnmappedSourceIsUnaffectedByOtherMappings() {
            Object prev = TestConfig.set("compat.damageSourceCategories", List.of("something_else=FIRE"));
            try {
                assertEquals(DamageCategory.GENERIC, classify("wibble"));
            } finally {
                TestConfig.set("compat.damageSourceCategories", prev);
            }
        }
    }
}

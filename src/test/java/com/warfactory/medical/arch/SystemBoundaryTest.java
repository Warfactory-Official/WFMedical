package com.warfactory.medical.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.warfactory.medical.MedicalSystem;
import com.warfactory.medical.WFMedical;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** WFMedical systems: package deps within {@code requires()}, other systems through their {@code api} only; common code off client packages. Gametests and datagen exempt. */
@AnalyzeClasses(packages = "com.warfactory.medical",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class SystemBoundaryTest {
    private static final String ROOT = "com.warfactory.medical.";
    private static final Map<String, Set<String>> ALLOWED = new HashMap<>();

    static {
        for (MedicalSystem system : WFMedical.SYSTEMS) {
            Set<String> allowed = new HashSet<>(system.requires());
            allowed.add(system.id());
            ALLOWED.put(system.id(), allowed);
        }
    }

    /** Owning system of a package; null outside system packages. */
    private static String system(String pkg) {
        if (!pkg.startsWith(ROOT)) {
            return null;
        }
        String[] parts = pkg.substring(ROOT.length()).split("\\.");
        String id = parts[0].equals("mixin") && parts.length > 1 ? parts[1] : parts[0];
        return ALLOWED.containsKey(id) ? id : null;
    }

    @ArchTest
    static final ArchRule systemsUseOnlyRequiredSystems = classes()
            .that(resideInAPackage("com.warfactory.medical..")).and(not(resideInAPackage("..gametest..")))
            .and(not(nameMatching(".*\\.DataGenerators(\\$.*)?")))
            .should(new ArchCondition<JavaClass>("depend only on their own system, the systems it requires and other systems' api") {
                @Override
                public void check(JavaClass c, ConditionEvents events) {
                    String own = system(c.getPackageName());
                    if (own == null) {
                        return;
                    }
                    for (Dependency d : c.getDirectDependenciesFromSelf()) {
                        String target = system(d.getTargetClass().getPackageName());
                        String targetPkg = d.getTargetClass().getPackageName();
                        if (target != null && !ALLOWED.get(own).contains(target)
                                && !(targetPkg + ".").startsWith(ROOT + target + ".api.")) {
                            events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                        }
                    }
                }
            });

    @ArchTest
    static final ArchRule commonCodeStaysOffClientPackages = noClasses()
            .that(resideInAPackage("com.warfactory.medical..")).and(not(resideInAPackage("..client..")))
            .and(not(resideInAPackage("com.warfactory.medical.mixin..")))
            .and(not(resideInAPackage("com.warfactory.medical.fx..")))
            .and(not(simpleNameEndingWith("System"))).and(not(simpleNameEndingWith("Client")))
            .should().dependOnClassesThat(resideInAPackage("com.warfactory.medical..client.."))
            .because("client classes load only through registerClient");
}

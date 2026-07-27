package com.miracle.ai.seahorse.agent.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * R2: domain 不依赖 application
 * domain 包不应依赖 application 包，避免领域模型被用例污染
 */
public class R2DomainIsolationTest {

    private final JavaClasses kernelClasses = new ClassFileImporter()
            .importPackages("com.miracle.ai.seahorse.agent.kernel");

    @Test
    void domainShouldNotDependOnApplication() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.miracle.ai.seahorse.agent.kernel.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.miracle.ai.seahorse.agent.kernel.application..")
                .because("Domain layer should be independent of application layer (DDD principle)");
        try {
            rule.check(kernelClasses);
            System.out.println("R2 domain->application: PASS");
        } catch (AssertionError e) {
            System.out.println("R2 violation:\n" + e.getMessage());
            throw e;
        }
    }

    @Test
    void domainShouldNotDependOnAdapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.miracle.ai.seahorse.agent.kernel.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.miracle.ai.seahorse.agent.adapters..")
                .because("Domain must not depend on adapter layer");
        try {
            rule.check(kernelClasses);
            System.out.println("R2 domain->adapter: PASS");
        } catch (AssertionError e) {
            System.out.println("R2 domain->adapter violation:\n" + e.getMessage());
            throw e;
        }
    }
}

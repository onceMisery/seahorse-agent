package com.miracle.ai.seahorse.agent.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * R1: kernel 不依赖 adapter / Spring Web / SDK
 * - kernel classes reside in com.miracle.ai.seahorse.agent.kernel..
 * - should not depend on adapters, spring-web, and heavy SDKs
 */
public class R1KernelIsolationTest {

    private final JavaClasses kernelClasses = new ClassFileImporter()
            .importPackages("com.miracle.ai.seahorse.agent.kernel");

    @Test
    void kernelShouldNotDependOnAdapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.miracle.ai.seahorse.agent.kernel..")
                .should().dependOnClassesThat().resideInAPackage("com.miracle.ai.seahorse.agent.adapters..")
                .because("Kernel must be framework-agnostic and not depend on adapter layer (Ports & Adapters)");

        rule.check(kernelClasses);
    }

    @Test
    void kernelShouldNotDependOnSpringWeb() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.miracle.ai.seahorse.agent.kernel..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.web.servlet..",
                        "org.springframework.boot.web.."
                )
                .because("Kernel should not depend on Spring Web; web concerns belong to adapter-web");

        rule.check(kernelClasses);
    }

    @Test
    void kernelShouldNotDependOnHeavySdk() {
        // Milvus, Tika, AWS S3, Redisson, Pulsar, Lucene, Elasticsearch, Agentscope are adapter concerns
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.miracle.ai.seahorse.agent.kernel..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.milvus..",
                        "org.apache.tika..",
                        "software.amazon.awssdk..",
                        "org.redisson..",
                        "org.apache.pulsar..",
                        "org.apache.lucene..",
                        "org.elasticsearch..",
                        "co.elastic.clients..",
                        "io.agentscope..",
                        "org.springframework.amqp.."
                )
                .because("SDK dependencies must be isolated in adapter modules, not kernel");

        rule.check(kernelClasses);
    }
}

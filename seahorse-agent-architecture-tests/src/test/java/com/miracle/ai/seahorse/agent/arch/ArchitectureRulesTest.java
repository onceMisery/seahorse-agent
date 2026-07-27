package com.miracle.ai.seahorse.agent.arch;

import org.junit.jupiter.api.Test;

/**
 * Aggregate entry point for Architecture tests R1-R5
 * Ensures all rules run in single Maven invocation if needed.
 */
public class ArchitectureRulesTest {

    @Test
    void runAllRules() {
        // Delegate to individual rule tests to keep CI output simple
        // JUnit will also discover other Test classes, but this ensures at least one entry
        new R1KernelIsolationTest().kernelShouldNotDependOnAdapters();
        new R1KernelIsolationTest().kernelShouldNotDependOnSpringWeb();
        new R1KernelIsolationTest().kernelShouldNotDependOnHeavySdk();

        new R2DomainIsolationTest().domainShouldNotDependOnApplication();
        new R2DomainIsolationTest().domainShouldNotDependOnAdapters();

        new R3SubdomainIsolationTest().checkWhitelistSizeIs35();
        new R3SubdomainIsolationTest().subdomainShouldNotDependOnOtherSubdomainUnlessWhitelisted();

        new R4AdapterIsolationTest().adaptersShouldNotDependOnOtherAdapters();

        new R5ControllerDependencyTest().controllersShouldNotDirectlyReferenceKernelServiceImplementation();
    }
}

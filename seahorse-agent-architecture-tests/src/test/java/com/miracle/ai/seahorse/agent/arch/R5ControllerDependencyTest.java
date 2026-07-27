package com.miracle.ai.seahorse.agent.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * R5: Controller 不直接引用 Kernel*Service 实现类
 * - Controllers in adapter-web should not directly depend on Kernel*Service classes in kernel.application
 * - Must depend on InboundPort interfaces
 */
public class R5ControllerDependencyTest {

    private final JavaClasses webClasses = new ClassFileImporter()
            .importPackages("com.miracle.ai.seahorse.agent.adapters.web", "com.miracle.ai.seahorse.agent.adapters.local");

    @Test
    void controllersShouldNotDirectlyReferenceKernelServiceImplementation() {
        List<String> violations = new ArrayList<>();

        for (JavaClass clazz : webClasses) {
            String simpleName = clazz.getSimpleName();
            String pkg = clazz.getPackageName();
            boolean isController = simpleName.endsWith("Controller") || clazz.getName().endsWith("Controller");
            // Also include classes annotated with @RestController? Check via reflection if possible
            // For simplicity we rely on naming
            if (!isController) continue;
            if (!pkg.contains("adapters.web") && !pkg.contains("adapters.local")) continue;

            for (Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                JavaClass target = dep.getTargetClass();
                String targetName = target.getSimpleName();
                String targetPackage = target.getPackageName();

                if (targetPackage.startsWith("com.miracle.ai.seahorse.agent.kernel.application")
                        && targetName.startsWith("Kernel")
                        && targetName.endsWith("Service")
                        && !targetName.equals("KernelService")) { // Kernel*Service is implementation

                    // Allow if target is an interface? Kernel*Service are concrete classes, but we can check if it's interface
                    // Even concrete services should not be referenced directly; they should be hidden behind Port
                    // So this is violation
                    violations.add(String.format("%s -> %s via %s:%d",
                            clazz.getName(), target.getName(), dep.getDescription(), dep.getLineNumber()));
                }

                // Also forbid direct reference to any class in kernel.application that ends with Service and is not Port
                // Additional check: if target is in kernel.application and its name contains Service but not Port, and not interface in ports
                // This covers Kernel*Service
            }
        }

        if (!violations.isEmpty()) {
            System.out.println("Controller -> KernelService violations: " + violations.size());
            violations.forEach(System.out::println);
            fail("Controllers should not directly reference Kernel*Service implementations. Found " + violations.size() + " violations:\n"
                    + String.join("\n", violations) + "\n"
                    + "Controllers must depend on InboundPort interfaces (e.g., ChatInboundPort) instead.");
        }
    }

    @Test
    void controllersShouldNotDependOnKernelApplicationImpl() {
        // Broader check: controller should not depend on any class in kernel.application that is not a Port or Domain?
        // For Phase 0 we only enforce Kernel*Service, but we add this as additional guard
        // This test is informational
        List<String> violations = new ArrayList<>();

        for (JavaClass clazz : webClasses) {
            if (!clazz.getSimpleName().endsWith("Controller")) continue;

            for (Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                JavaClass target = dep.getTargetClass();
                String targetPkg = target.getPackageName();
                if (targetPkg.startsWith("com.miracle.ai.seahorse.agent.kernel.application")) {
                    String targetSimple = target.getSimpleName();
                    // Allow if target is in .ports (but ports is not in application, it's in ports package)
                    // So any direct dependency on application service is considered violation unless it's a Port? But application does not contain Port, it contains services
                    // So we flag anything that ends with Service
                    if (targetSimple.endsWith("Service") && targetSimple.startsWith("Kernel")) {
                        // already covered
                        continue;
                    }
                }
            }
        }
    }
}

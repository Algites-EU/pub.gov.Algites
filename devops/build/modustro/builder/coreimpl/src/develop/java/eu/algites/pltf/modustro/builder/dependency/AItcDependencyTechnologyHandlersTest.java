package eu.algites.pltf.modustro.builder.dependency;

import eu.algites.pltf.modustro.builder.catalog.AIcBuiltinTechnologyKindDefinitions;
import eu.algites.pltf.modustro.builder.dependency.java.AIcJavaDependencyTechnologyHandler;
import eu.algites.pltf.modustro.builder.dependency.python.AIcPythonDependencyTechnologyHandler;
import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyDefinition;
import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyIdentity;
import eu.algites.pltf.modustro.builder.model.dependency.AIcVersionBound;
import eu.algites.pltf.modustro.builder.model.dependency.AIcVersionRequirement;
import eu.algites.pltf.modustro.builder.model.dependency.AInDependencyUsage;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AIcDependencyTechnologyResolutionPlan;
import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputTypeDefinition;
import eu.algites.pltf.modustro.builder.model.technology.AIcTechnologyKindDefinition;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests DependencyKind x TechnologyKind dependency mappings.
 */
public final class AItcDependencyTechnologyHandlersTest {

    /**
     * Creates a test instance.
     */
    public AItcDependencyTechnologyHandlersTest() {
    }

    /**
     * Verifies that modustro java usages map directly to standard gradle configuration names.
     */
    @Test
    public void modustroJavaUsagesMapDirectlyToStandardGradleConfigurationNames() {
        AIcDependencyDefinition locDependency = dependency(
            "modustro",
            Set.of(
                AInDependencyUsage.PRODUCT_API,
                AInDependencyUsage.PRODUCT_COMPILE_ONLY_API,
                AInDependencyUsage.PRODUCT_ANNOTATION_PROCESSOR,
                AInDependencyUsage.DEVELOP_RUNTIME_ONLY
            ),
            Set.of()
        );
        AIcDependencyTechnologyResolutionPlan locPlan = new AIcJavaDependencyTechnologyHandler("modustro").createResolutionPlan(
            List.of(locDependency),
            List.of(),
            AIcBuiltinTechnologyKindDefinitions.javaDefinition()
        );
        Assert.assertEquals(locPlan.dependencyKind(), "modustro");
        Assert.assertEquals(locPlan.technologyKind(), "java");
        Assert.assertEquals(locPlan.entries().size(), 1);
        Assert.assertEquals(locPlan.entries().get(0).nativeUsageMappings().get(AInDependencyUsage.PRODUCT_API), "api");
        Assert.assertEquals(locPlan.entries().get(0).nativeUsageMappings().get(AInDependencyUsage.PRODUCT_COMPILE_ONLY_API), "compileOnlyApi");
        Assert.assertEquals(locPlan.entries().get(0).nativeUsageMappings().get(AInDependencyUsage.PRODUCT_ANNOTATION_PROCESSOR), "annotationProcessor");
        Assert.assertEquals(locPlan.entries().get(0).nativeUsageMappings().get(AInDependencyUsage.DEVELOP_RUNTIME_ONLY), "testRuntimeOnly");
        Assert.assertEquals(locPlan.entries().get(0).requiredBuildOutputTypes(), Set.of("java_classes_jar"));
        Assert.assertTrue(locPlan.diagnostics().isEmpty());
    }

    /**
     * Verifies that native java does not use modustro dependency output defaults.
     */
    @Test
    public void nativeJavaDoesNotUseModustroDependencyOutputDefaults() {
        AIcDependencyDefinition locDependency = dependency(
            "java",
            Set.of(AInDependencyUsage.PRODUCT_IMPLEMENTATION),
            Set.of()
        );
        AIcDependencyTechnologyResolutionPlan locPlan = new AIcJavaDependencyTechnologyHandler("java").createResolutionPlan(
            List.of(locDependency),
            List.of(),
            AIcBuiltinTechnologyKindDefinitions.javaDefinition()
        );
        Assert.assertEquals(locPlan.entries().get(0).requiredBuildOutputTypes(), Set.of());
        Assert.assertEquals(locPlan.entries().get(0).nativeUsageMappings().get(AInDependencyUsage.PRODUCT_IMPLEMENTATION), "implementation");
    }

    /**
     * Verifies that modustro python usages use lossy mappings and produce diagnostics.
     */
    @Test
    public void modustroPythonUsagesUseLossyMappingsAndProduceDiagnostics() {
        AIcDependencyDefinition locDependency = dependency(
            "modustro",
            Set.of(
                AInDependencyUsage.PRODUCT_IMPLEMENTATION,
                AInDependencyUsage.PRODUCT_COMPILE_ONLY,
                AInDependencyUsage.PRODUCT_COMPILE_ONLY_API,
                AInDependencyUsage.DEVELOP_COMPILE_ONLY,
                AInDependencyUsage.PRODUCT_ANNOTATION_PROCESSOR
            ),
            Set.of()
        );
        AIcDependencyTechnologyResolutionPlan locPlan = new AIcPythonDependencyTechnologyHandler("modustro").createResolutionPlan(
            List.of(locDependency),
            List.of(),
            AIcBuiltinTechnologyKindDefinitions.pythonDefinition()
        );
        Assert.assertEquals(locPlan.entries().get(0).requiredBuildOutputTypes(), Set.of("python_distribution"));
        Assert.assertEquals(
            locPlan.entries().get(0).nativeUsageMappings().get(AInDependencyUsage.PRODUCT_IMPLEMENTATION),
            AIcPythonDependencyTechnologyHandler.PACKAGE_RUNTIME
        );
        Assert.assertEquals(
            locPlan.entries().get(0).nativeUsageMappings().get(AInDependencyUsage.PRODUCT_COMPILE_ONLY),
            AIcPythonDependencyTechnologyHandler.BUILD_SOURCE_PROCESSING
        );
        Assert.assertEquals(
            locPlan.entries().get(0).nativeUsageMappings().get(AInDependencyUsage.PRODUCT_ANNOTATION_PROCESSOR),
            AIcPythonDependencyTechnologyHandler.NO_OP
        );
        Assert.assertEquals(locPlan.diagnostics().size(), 4);
    }

    /**
     * Verifies that native python does not use modustro dependency output defaults.
     */
    @Test
    public void nativePythonDoesNotUseModustroDependencyOutputDefaults() {
        AIcDependencyDefinition locDependency = dependency(
            "python",
            Set.of(AInDependencyUsage.PRODUCT_API),
            Set.of()
        );
        AIcDependencyTechnologyResolutionPlan locPlan = new AIcPythonDependencyTechnologyHandler("python").createResolutionPlan(
            List.of(locDependency),
            List.of(),
            AIcBuiltinTechnologyKindDefinitions.pythonDefinition()
        );
        Assert.assertEquals(locPlan.entries().get(0).requiredBuildOutputTypes(), Set.of());
        Assert.assertEquals(
            locPlan.entries().get(0).nativeUsageMappings().get(AInDependencyUsage.PRODUCT_API),
            AIcPythonDependencyTechnologyHandler.PACKAGE_RUNTIME
        );
    }

    /**
     * Verifies that built in registry is keyed by dependency kind and technology kind.
     */
    @Test
    public void builtInRegistryIsKeyedByDependencyKindAndTechnologyKind() {
        AIcBuiltinDependencyTechnologyHandlers locRegistry = new AIcBuiltinDependencyTechnologyHandlers();
        Assert.assertEquals(locRegistry.require("modustro", "java").dependencyKind(), "modustro");
        Assert.assertEquals(locRegistry.require("java", "java").dependencyKind(), "java");
        Assert.assertEquals(locRegistry.require("modustro", "python").technologyKind(), "python");
        Assert.assertEquals(locRegistry.require("python", "python").dependencyKind(), "python");
    }

    /**
     * Verifies that built in registry rejects unsupported pair.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void builtInRegistryRejectsUnsupportedPair() {
        new AIcBuiltinDependencyTechnologyHandlers().require("java", "python");
    }

    /**
     * Verifies that handler rejects mismatched dependency kind.
     */
    @Test(expectedExceptions = AIxModelValidationException.class)
    public void handlerRejectsMismatchedDependencyKind() {
        AIcDependencyDefinition locDependency = dependency(
            "java",
            Set.of(AInDependencyUsage.PRODUCT_IMPLEMENTATION),
            Set.of()
        );
        new AIcJavaDependencyTechnologyHandler("modustro").createResolutionPlan(
            List.of(locDependency),
            List.of(),
            AIcBuiltinTechnologyKindDefinitions.javaDefinition()
        );
    }

    /**
     * Verifies that dependency cannot request non consumable output.
     */
    @Test(expectedExceptions = AIxModelValidationException.class)
    public void dependencyCannotRequestNonConsumableOutput() {
        AIcDependencyDefinition locDependency = dependency(
            "modustro",
            Set.of(AInDependencyUsage.PRODUCT_IMPLEMENTATION),
            Set.of("docs_site")
        );
        AIcTechnologyKindDefinition locJavaDefinition = new AIcTechnologyKindDefinition(
            "java",
            List.of(),
            List.of(new AIcBuildOutputTypeDefinition("docs_site", true, false, Set.of(), null)),
            Set.of("docs_site"),
            Set.of()
        );
        new AIcJavaDependencyTechnologyHandler("modustro").createResolutionPlan(
            List.of(locDependency),
            List.of(),
            locJavaDefinition
        );
    }

    /**
     * Verifies that exact version wins over inherited preference and produces info diagnostic.
     */
    @Test
    public void exactVersionWinsOverInheritedPreferenceAndProducesInfoDiagnostic() {
        AIcDependencyDefinition locDependency = new AIcDependencyDefinition(
            new AIcDependencyIdentity("modustro", "eu.algites.example", "example", null),
            Set.of(AInDependencyUsage.PRODUCT_IMPLEMENTATION),
            Set.of(),
            new AIcVersionRequirement(
                "1.0-SNAPSHOT",
                null,
                null,
                null,
                Set.of(),
                "2.0.0"
            )
        );

        AIcDependencyTechnologyResolutionPlan locPlan = new AIcJavaDependencyTechnologyHandler("modustro").createResolutionPlan(
            List.of(locDependency),
            List.of(),
            AIcBuiltinTechnologyKindDefinitions.javaDefinition()
        );

        Assert.assertNotNull(locPlan.entries().get(0).versionRequirement().exact());
        Assert.assertNull(locPlan.entries().get(0).versionRequirement().preferred());
        Assert.assertEquals(locPlan.diagnostics().size(), 1);
        Assert.assertEquals(locPlan.diagnostics().get(0).severity().name(), "INFO");
    }

    /**
     * Verifies that exact version outside strict maximum fails technology resolution.
     */
    @Test(expectedExceptions = AIxModelValidationException.class)
    public void exactVersionOutsideStrictMaximumFailsTechnologyResolution() {
        AIcDependencyDefinition locDependency = new AIcDependencyDefinition(
            new AIcDependencyIdentity("modustro", "eu.algites.example", "example", null),
            Set.of(AInDependencyUsage.PRODUCT_IMPLEMENTATION),
            Set.of(),
            new AIcVersionRequirement(
                "2.0.0",
                null,
                new AIcVersionBound("1.0.0", true),
                true,
                Set.of(),
                null
            )
        );

        new AIcJavaDependencyTechnologyHandler("modustro").createResolutionPlan(
            List.of(locDependency),
            List.of(),
            AIcBuiltinTechnologyKindDefinitions.javaDefinition()
        );
    }

    private static AIcDependencyDefinition dependency(
        String aDependencyKind,
        Set<AInDependencyUsage> aUsages,
        Set<String> aRequiredBuildOutputTypes
    ) {
        return new AIcDependencyDefinition(
            new AIcDependencyIdentity(aDependencyKind, "eu.algites.example", "example", null),
            aUsages,
            aRequiredBuildOutputTypes,
            null
        );
    }
}

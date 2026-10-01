package eu.algites.pltf.modustro.builder.dependency.java;

import eu.algites.pltf.modustro.builder.dependency.AIcDependencyTechnologyHandlerSupport;
import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyIdentity;
import eu.algites.pltf.modustro.builder.model.dependency.AInDependencyUsage;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AIcDependencyResolutionDiagnostic;
import java.util.List;
import java.util.Map;

/**
 * Maps portable Modustro dependency usages to standard Gradle Java/Java-Library configuration names.
 *
 * This class exposes only logical configuration names and has no dependency on the Gradle API.
 */
public final class AIcJavaDependencyTechnologyHandler extends AIcDependencyTechnologyHandlerSupport {

    private final String dependencyKind;

    /**
     * Creates an {@code AIcJavaDependencyTechnologyHandler} instance.
     *
     * @param aDependencyKind DependencyKind identifier
     */
    public AIcJavaDependencyTechnologyHandler(String aDependencyKind) {
        if (!java.util.Set.of("modustro", "java").contains(aDependencyKind)) {
            throw new IllegalArgumentException("Unsupported DependencyKind '" + aDependencyKind + "' for TechnologyKind '" + technologyKind() + "'.");
        }
        dependencyKind = aDependencyKind;
    }

    /**
     * Returns the DependencyKind identifier handled by this object.
     * @return DependencyKind identifier
     */
    @Override
    public String dependencyKind() {
        return dependencyKind;
    }

    /**
     * Returns the TechnologyKind identifier.
     * @return TechnologyKind identifier
     */
    @Override
    public String technologyKind() {
        return "java";
    }

    /**
     * Maps one portable dependency usage to a technology-native usage identifier.
     *
     * @param aIdentity dependency or item identity
     * @param aUsage portable dependency usage
     * @param aMappings technology-native usage mappings
     * @param aDiagnostics resolution diagnostics
     */
    @Override
    protected void mapUsage(
        AIcDependencyIdentity aIdentity,
        AInDependencyUsage aUsage,
        Map<AInDependencyUsage, String> aMappings,
        List<AIcDependencyResolutionDiagnostic> aDiagnostics
    ) {
        String locConfiguration = switch (aUsage) {
            case PRODUCT_API -> "api";
            case PRODUCT_IMPLEMENTATION -> "implementation";
            case PRODUCT_COMPILE_ONLY -> "compileOnly";
            case PRODUCT_COMPILE_ONLY_API -> "compileOnlyApi";
            case PRODUCT_RUNTIME_ONLY -> "runtimeOnly";
            case PRODUCT_ANNOTATION_PROCESSOR -> "annotationProcessor";
            case DEVELOP_IMPLEMENTATION -> "testImplementation";
            case DEVELOP_COMPILE_ONLY -> "testCompileOnly";
            case DEVELOP_RUNTIME_ONLY -> "testRuntimeOnly";
            case DEVELOP_ANNOTATION_PROCESSOR -> "testAnnotationProcessor";
        };
        aMappings.put(aUsage, locConfiguration);
    }
}

package eu.algites.pltf.modustro.builder.dependency.python;

import eu.algites.pltf.modustro.builder.dependency.AIcDependencyTechnologyHandlerSupport;
import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyIdentity;
import eu.algites.pltf.modustro.builder.model.dependency.AInDependencyUsage;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AIcDependencyResolutionDiagnostic;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AInDependencyResolutionDiagnosticSeverity;
import java.util.List;
import java.util.Map;

/**
 * Maps portable Modustro dependency usages to Python package/build/development roles.
 */
public final class AIcPythonDependencyTechnologyHandler extends AIcDependencyTechnologyHandlerSupport {

    private final String dependencyKind;

    public AIcPythonDependencyTechnologyHandler(String aDependencyKind) {
        if (!java.util.Set.of("modustro", "python").contains(aDependencyKind)) {
            throw new IllegalArgumentException("Unsupported DependencyKind '" + aDependencyKind + "' for TechnologyKind '" + technologyKind() + "'.");
        }
        dependencyKind = aDependencyKind;
    }

    @Override
    public String dependencyKind() {
        return dependencyKind;
    }

    public static final String PACKAGE_RUNTIME = "package_runtime";
    public static final String BUILD_SOURCE_PROCESSING = "build_source_processing";
    public static final String DEVELOPMENT = "development";
    public static final String NO_OP = "no_op";

    @Override
    public String technologyKind() {
        return "python";
    }

    @Override
    protected void mapUsage(
        AIcDependencyIdentity aIdentity,
        AInDependencyUsage aUsage,
        Map<AInDependencyUsage, String> aMappings,
        List<AIcDependencyResolutionDiagnostic> aDiagnostics
    ) {
        switch (aUsage) {
            case PRODUCT_API -> aMappings.put(aUsage, PACKAGE_RUNTIME);
            case PRODUCT_IMPLEMENTATION -> {
                aMappings.put(aUsage, PACKAGE_RUNTIME);
                warning(
                    aIdentity,
                    "PYTHON_IMPLEMENTATION_COLLAPSES_TO_RUNTIME",
                    "Python has no separate implementation-vs-API package dependency metadata; product_implementation is emitted as a normal runtime/package dependency.",
                    aDiagnostics
                );
            }
            case PRODUCT_RUNTIME_ONLY -> aMappings.put(aUsage, PACKAGE_RUNTIME);
            case PRODUCT_COMPILE_ONLY -> aMappings.put(aUsage, BUILD_SOURCE_PROCESSING);
            case PRODUCT_COMPILE_ONLY_API -> {
                aMappings.put(aUsage, BUILD_SOURCE_PROCESSING);
                warning(
                    aIdentity,
                    "PYTHON_COMPILE_ONLY_API_NOT_EXPORTED",
                    "Python has no exported compile-only API dependency role; product_compile_only_api maps to the Modustro build/source-processing role and is not published as a runtime dependency.",
                    aDiagnostics
                );
            }
            case PRODUCT_ANNOTATION_PROCESSOR, DEVELOP_ANNOTATION_PROCESSOR -> {
                aMappings.put(aUsage, NO_OP);
                warning(
                    aIdentity,
                    "PYTHON_ANNOTATION_PROCESSOR_UNSUPPORTED",
                    "Python currently has no Modustro annotation-processing hook; usage '" + aUsage.wireValue() + "' is a no-op for the Python build.",
                    aDiagnostics
                );
            }
            case DEVELOP_IMPLEMENTATION -> aMappings.put(aUsage, DEVELOPMENT);
            case DEVELOP_COMPILE_ONLY, DEVELOP_RUNTIME_ONLY -> {
                aMappings.put(aUsage, DEVELOPMENT);
                warning(
                    aIdentity,
                    "PYTHON_DEVELOP_USAGE_COLLAPSED",
                    "Python development dependency handling does not distinguish usage '" + aUsage.wireValue() + "'; it is mapped to the common development environment.",
                    aDiagnostics
                );
            }
        }
    }

    private static void warning(
        AIcDependencyIdentity aIdentity,
        String aCode,
        String aMessage,
        List<AIcDependencyResolutionDiagnostic> aDiagnostics
    ) {
        aDiagnostics.add(new AIcDependencyResolutionDiagnostic(
            aCode,
            AInDependencyResolutionDiagnosticSeverity.WARNING,
            aIdentity,
            aMessage
        ));
    }
}

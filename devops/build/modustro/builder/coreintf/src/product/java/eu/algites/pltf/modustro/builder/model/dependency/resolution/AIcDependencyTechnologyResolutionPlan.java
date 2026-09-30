package eu.algites.pltf.modustro.builder.model.dependency.resolution;

import java.util.List;
import java.util.Objects;

/**
 * Gradle-independent dependency-resolution plan for one DependencyKind x TechnologyKind pair.
 */
public final class AIcDependencyTechnologyResolutionPlan {

    private final String dependencyKind;
    private final String technologyKind;
    private final List<AIcDependencyTechnologyPlanEntry> entries;
    private final List<AIcDependencyResolutionDiagnostic> diagnostics;

    public AIcDependencyTechnologyResolutionPlan(
        String aDependencyKind,
        String aTechnologyKind,
        List<AIcDependencyTechnologyPlanEntry> aEntries,
        List<AIcDependencyResolutionDiagnostic> aDiagnostics
    ) {
        dependencyKind = requireText(aDependencyKind, "dependencyKind");
        technologyKind = requireText(aTechnologyKind, "technologyKind");
        entries = List.copyOf(Objects.requireNonNull(aEntries, "entries"));
        diagnostics = List.copyOf(Objects.requireNonNull(aDiagnostics, "diagnostics"));
    }

    private static String requireText(String aValue, String aName) {
        String locValue = Objects.requireNonNull(aValue, aName).trim();
        if (locValue.isEmpty()) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    public String dependencyKind() {
        return dependencyKind;
    }

    public String technologyKind() {
        return technologyKind;
    }

    public List<AIcDependencyTechnologyPlanEntry> entries() {
        return entries;
    }

    public List<AIcDependencyResolutionDiagnostic> diagnostics() {
        return diagnostics;
    }
}

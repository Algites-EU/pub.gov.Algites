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

    /**
     * Creates an {@code AIcDependencyTechnologyResolutionPlan} instance.
     *
     * @param aDependencyKind DependencyKind identifier
     * @param aTechnologyKind TechnologyKind identifier
     * @param aEntries resolution-plan entries
     * @param aDiagnostics resolution diagnostics
     */
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

    /**
     * Returns the DependencyKind identifier handled by this object.
     * @return DependencyKind identifier
     */
    public String dependencyKind() {
        return dependencyKind;
    }

    /**
     * Returns the TechnologyKind identifier.
     * @return TechnologyKind identifier
     */
    public String technologyKind() {
        return technologyKind;
    }

    /**
     * Returns the technology-resolution plan entries.
     * @return immutable plan-entry list
     */
    public List<AIcDependencyTechnologyPlanEntry> entries() {
        return entries;
    }

    /**
     * Returns diagnostics produced while creating the plan.
     * @return immutable diagnostic list
     */
    public List<AIcDependencyResolutionDiagnostic> diagnostics() {
        return diagnostics;
    }
}

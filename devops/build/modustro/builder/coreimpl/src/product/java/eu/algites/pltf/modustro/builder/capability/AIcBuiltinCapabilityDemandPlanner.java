package eu.algites.pltf.modustro.builder.capability;

import eu.algites.pltf.modustro.builder.catalog.AIcBuiltinTechnologyKindDefinitions;
import eu.algites.pltf.modustro.builder.model.AInModelScope;
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDefinition;
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDemand;
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDemandDependency;
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDemandGraph;
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDemandKey;
import eu.algites.pltf.modustro.builder.model.capability.AIiCapabilityDemandPlanner;
import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputProductionPlan;
import eu.algites.pltf.modustro.builder.model.technology.AIcTechnologyKindDefinition;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Built-in Phase-4 capability demand planner with deterministic deduplication and prerequisite expansion.
 */
public final class AIcBuiltinCapabilityDemandPlanner implements AIiCapabilityDemandPlanner {

    /**
     * Creates the built-in capability demand planner.
     */
    public AIcBuiltinCapabilityDemandPlanner() {
    }

    private static final Map<String, Set<String>> PREREQUISITES = Map.of(
        key("java", "generation_of_native_documentation"), Set.of("source_native_processing", "dependency_resolution"),
        key("python", "generation_of_native_documentation"), Set.of("source_native_processing", "dependency_resolution")
    );

    /**
     * Creates a deduplicated capability demand graph and expands built-in prerequisites.
     *
     * @param aArtifactScopeIdentity stable identity of the artifact scope
     * @param aBuildOutputPlans build-output production plans
     * @param aAdditionalDemands additional explicit capability demands
     * @return validated capability demand graph
     * @throws IllegalArgumentException if a demand is invalid or the resulting graph is inconsistent
     */
    @Override
    public AIcCapabilityDemandGraph createDemandGraph(
        String aArtifactScopeIdentity,
        Collection<AIcBuildOutputProductionPlan> aBuildOutputPlans,
        Collection<AIcCapabilityDemand> aAdditionalDemands
    ) {
        String locScopeIdentity = requireText(aArtifactScopeIdentity, "artifactScopeIdentity");
        Objects.requireNonNull(aBuildOutputPlans, "buildOutputPlans");
        Objects.requireNonNull(aAdditionalDemands, "additionalDemands");

        LinkedHashMap<AIcCapabilityDemandKey, AIcCapabilityDemand> locDemands = new LinkedHashMap<>();
        LinkedHashSet<AIcCapabilityDemandDependency> locDependencies = new LinkedHashSet<>();

        for (AIcBuildOutputProductionPlan locPlan : aBuildOutputPlans) {
            for (String locCapabilityId : locPlan.requiredCapabilityIds()) {
                AIcCapabilityDemandKey locKey = new AIcCapabilityDemandKey(
                    locPlan.technologyKind(),
                    locCapabilityId,
                    AInModelScope.ARTIFACT,
                    locScopeIdentity
                );
                addDemand(locDemands, new AIcCapabilityDemand(locKey, Set.of("build_output:" + locPlan.buildOutputType())));
                expandPrerequisites(locKey, locDemands, locDependencies);
            }
        }

        for (AIcCapabilityDemand locDemand : aAdditionalDemands) {
            validateDemand(locDemand);
            addDemand(locDemands, locDemand);
            expandPrerequisites(locDemand.key(), locDemands, locDependencies);
        }

        return new AIcCapabilityDemandGraph(List.copyOf(locDemands.values()), locDependencies);
    }

    private void expandPrerequisites(
        AIcCapabilityDemandKey aDependent,
        Map<AIcCapabilityDemandKey, AIcCapabilityDemand> aDemands,
        Set<AIcCapabilityDemandDependency> aDependencies
    ) {
        validateKey(aDependent);
        for (String locPrerequisiteId : PREREQUISITES.getOrDefault(key(aDependent.technologyKind(), aDependent.capabilityId()), Set.of())) {
            AIcCapabilityDemandKey locPrerequisite = new AIcCapabilityDemandKey(
                aDependent.technologyKind(),
                locPrerequisiteId,
                aDependent.scope(),
                aDependent.scopeIdentity()
            );
            validateKey(locPrerequisite);
            addDemand(
                aDemands,
                new AIcCapabilityDemand(locPrerequisite, Set.of("prerequisite_of:" + aDependent.capabilityId()))
            );
            aDependencies.add(new AIcCapabilityDemandDependency(locPrerequisite, aDependent));
            expandPrerequisites(locPrerequisite, aDemands, aDependencies);
        }
    }

    private static void addDemand(
        Map<AIcCapabilityDemandKey, AIcCapabilityDemand> aDemands,
        AIcCapabilityDemand aDemand
    ) {
        aDemands.merge(aDemand.key(), aDemand, AIcCapabilityDemand::merge);
    }

    private static void validateDemand(AIcCapabilityDemand aDemand) {
        Objects.requireNonNull(aDemand, "demand");
        validateKey(aDemand.key());
    }

    private static void validateKey(AIcCapabilityDemandKey aKey) {
        AIcTechnologyKindDefinition locTechnology = technologyDefinition(aKey.technologyKind());
        AIcCapabilityDefinition locCapability = locTechnology.capabilities().stream()
            .filter(locItem -> locItem.capabilityId().equals(aKey.capabilityId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "TechnologyKind '" + aKey.technologyKind() + "' does not define capability '" + aKey.capabilityId() + "'."
            ));
        if (!locCapability.allowedScopes().contains(aKey.scope())) {
            throw new IllegalArgumentException(
                "Capability '" + aKey.capabilityId() + "' for TechnologyKind '" + aKey.technologyKind()
                    + "' is not allowed at scope '" + aKey.scope().name().toLowerCase() + "'."
            );
        }
    }

    private static AIcTechnologyKindDefinition technologyDefinition(String aTechnologyKind) {
        return switch (aTechnologyKind) {
            case "java" -> AIcBuiltinTechnologyKindDefinitions.javaDefinition();
            case "python" -> AIcBuiltinTechnologyKindDefinitions.pythonDefinition();
            case "modustro" -> AIcBuiltinTechnologyKindDefinitions.modustroDefinition();
            default -> throw new IllegalArgumentException("Unsupported TechnologyKind '" + aTechnologyKind + "'.");
        };
    }

    private static String key(String aTechnologyKind, String aCapabilityId) {
        return aTechnologyKind + ":" + aCapabilityId;
    }

    private static String requireText(String aValue, String aName) {
        Objects.requireNonNull(aValue, aName);
        String locValue = aValue.trim();
        if (locValue.isEmpty()) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }
}

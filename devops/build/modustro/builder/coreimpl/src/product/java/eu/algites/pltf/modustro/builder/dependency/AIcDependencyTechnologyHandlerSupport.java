package eu.algites.pltf.modustro.builder.dependency;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyConstraintDefinition;
import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyDefinition;
import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyIdentity;
import eu.algites.pltf.modustro.builder.model.dependency.AIcVersionRequirement;
import eu.algites.pltf.modustro.builder.model.dependency.AInDependencyUsage;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AIcDependencyResolutionDiagnostic;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AIcDependencyTechnologyPlanEntry;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AIcDependencyTechnologyResolutionPlan;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AIiDependencyTechnologyHandler;
import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputTypeDefinition;
import eu.algites.pltf.modustro.builder.model.technology.AIcTechnologyKindDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared Gradle-independent mechanics for dependency technology handlers.
 */
public abstract class AIcDependencyTechnologyHandlerSupport implements AIiDependencyTechnologyHandler {

    @Override
    public final AIcDependencyTechnologyResolutionPlan createResolutionPlan(
        List<AIcDependencyDefinition> aDependencies,
        List<AIcDependencyConstraintDefinition> aConstraints,
        AIcTechnologyKindDefinition aTechnologyDefinition
    ) {
        Objects.requireNonNull(aDependencies, "dependencies");
        Objects.requireNonNull(aConstraints, "constraints");
        Objects.requireNonNull(aTechnologyDefinition, "technologyDefinition");
        if (!technologyKind().equals(aTechnologyDefinition.technologyKind())) {
            throw new AIxModelValidationException(
                "Dependency handler for TechnologyKind '" + technologyKind() + "' cannot use definition for '" +
                    aTechnologyDefinition.technologyKind() + "'."
            );
        }
        aDependencies.forEach(locDependency -> validateDependencyKind(locDependency.identity()));
        aConstraints.forEach(locConstraint -> validateDependencyKind(locConstraint.identity()));

        List<AIcDependencyTechnologyPlanEntry> locEntries = new ArrayList<>();
        List<AIcDependencyResolutionDiagnostic> locDiagnostics = new ArrayList<>();
        aDependencies.forEach(locDependency -> locEntries.add(createEntry(
            locDependency.identity(),
            false,
            locDependency.usages(),
            locDependency.requiredBuildOutputTypes(),
            locDependency.versionRequirement(),
            aTechnologyDefinition,
            locDiagnostics
        )));
        aConstraints.forEach(locConstraint -> locEntries.add(createEntry(
            locConstraint.identity(),
            true,
            locConstraint.usages(),
            Set.of(),
            locConstraint.versionRequirement(),
            aTechnologyDefinition,
            locDiagnostics
        )));
        return new AIcDependencyTechnologyResolutionPlan(dependencyKind(), technologyKind(), locEntries, locDiagnostics);
    }

    private void validateDependencyKind(AIcDependencyIdentity aIdentity) {
        if (!dependencyKind().equals(aIdentity.dependencyKind())) {
            throw new AIxModelValidationException(
                "Dependency handler for DependencyKind '" + dependencyKind() + "' cannot process dependency kind '" +
                    aIdentity.dependencyKind() + "'."
            );
        }
    }

    private AIcDependencyTechnologyPlanEntry createEntry(
        AIcDependencyIdentity aIdentity,
        boolean aConstraintOnly,
        Set<AInDependencyUsage> aUsages,
        Set<String> aRequiredBuildOutputTypes,
        AIcVersionRequirement aVersionRequirement,
        AIcTechnologyKindDefinition aTechnologyDefinition,
        List<AIcDependencyResolutionDiagnostic> aDiagnostics
    ) {
        Set<AInDependencyUsage> locUsages = aUsages.isEmpty()
            ? Set.of(AInDependencyUsage.PRODUCT_IMPLEMENTATION)
            : new LinkedHashSet<>(aUsages);
        if (!"modustro".equals(dependencyKind()) && !aRequiredBuildOutputTypes.isEmpty()) {
            throw new AIxModelValidationException(
                "Native DependencyKind '" + dependencyKind() + "' cannot request Modustro BuildOutputTypes."
            );
        }
        Set<String> locRequiredOutputs;
        if (aConstraintOnly || !"modustro".equals(dependencyKind())) {
            locRequiredOutputs = new LinkedHashSet<>();
        } else if (aRequiredBuildOutputTypes.isEmpty()) {
            locRequiredOutputs = new LinkedHashSet<>(aTechnologyDefinition.defaultDependencyOutputTypes());
        } else {
            locRequiredOutputs = new LinkedHashSet<>(aRequiredBuildOutputTypes);
        }
        validateDependencyOutputs(locRequiredOutputs, aTechnologyDefinition, aIdentity);
        Map<AInDependencyUsage, String> locMappings = new LinkedHashMap<>();
        for (AInDependencyUsage locUsage : locUsages) {
            mapUsage(aIdentity, locUsage, locMappings, aDiagnostics);
        }
        AIcVersionRequirement locVersionRequirement = AIcDependencyVersionTechnologyBridge.normalize(
            aIdentity,
            aVersionRequirement,
            technologyKind(),
            aDiagnostics
        );
        return new AIcDependencyTechnologyPlanEntry(
            aIdentity,
            aConstraintOnly,
            locUsages,
            locMappings,
            locRequiredOutputs,
            locVersionRequirement
        );
    }

    private void validateDependencyOutputs(
        Set<String> aRequiredOutputs,
        AIcTechnologyKindDefinition aTechnologyDefinition,
        AIcDependencyIdentity aIdentity
    ) {
        Map<String, AIcBuildOutputTypeDefinition> locOutputs = new LinkedHashMap<>();
        for (AIcBuildOutputTypeDefinition locOutput : aTechnologyDefinition.buildOutputTypes()) {
            locOutputs.put(locOutput.buildOutputType(), locOutput);
        }
        for (String locOutputId : aRequiredOutputs) {
            AIcBuildOutputTypeDefinition locOutput = locOutputs.get(locOutputId);
            if (locOutput == null) {
                throw new AIxModelValidationException(
                    "Dependency '" + aIdentity.artifactId() + "' requires unknown BuildOutputType '" + locOutputId +
                        "' for TechnologyKind '" + technologyKind() + "'."
                );
            }
            if (!locOutput.canBeUsedInDependency()) {
                throw new AIxModelValidationException(
                    "Dependency '" + aIdentity.artifactId() + "' requires BuildOutputType '" + locOutputId +
                        "', which cannot be used in dependencies."
                );
            }
        }
    }

    protected abstract void mapUsage(
        AIcDependencyIdentity aIdentity,
        AInDependencyUsage aUsage,
        Map<AInDependencyUsage, String> aMappings,
        List<AIcDependencyResolutionDiagnostic> aDiagnostics
    );
}

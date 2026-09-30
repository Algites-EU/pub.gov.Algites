package eu.algites.pltf.modustro.builder.validation;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDefinition;
import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputTypeDefinition;
import eu.algites.pltf.modustro.builder.model.technology.AIcTechnologyKindDefinition;
import eu.algites.pltf.modustro.builder.model.technology.AIiTechnologyKindDefinitionValidator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates cross-reference rules of a TechnologyKind definition.
 */
public final class AIcTechnologyKindDefinitionValidator implements AIiTechnologyKindDefinitionValidator {

    @Override
    public void validate(AIcTechnologyKindDefinition aDefinition) {
        Map<String, AIcCapabilityDefinition> locCapabilities = new LinkedHashMap<>();
        for (AIcCapabilityDefinition locCapability : aDefinition.capabilities()) {
            if (locCapabilities.putIfAbsent(locCapability.capabilityId(), locCapability) != null) {
                throw new AIxModelValidationException("Duplicate capability '" + locCapability.capabilityId() + "' in TechnologyKind '" + aDefinition.technologyKind() + "'.");
            }
        }

        Map<String, AIcBuildOutputTypeDefinition> locOutputs = new LinkedHashMap<>();
        for (AIcBuildOutputTypeDefinition locOutput : aDefinition.buildOutputTypes()) {
            if (locOutputs.putIfAbsent(locOutput.buildOutputType(), locOutput) != null) {
                throw new AIxModelValidationException("Duplicate BuildOutputType '" + locOutput.buildOutputType() + "' in TechnologyKind '" + aDefinition.technologyKind() + "'.");
            }
            validateOutput(locOutput);
        }

        for (String locDefaultOutput : aDefinition.defaultBuildOutputTypes()) {
            AIcBuildOutputTypeDefinition locOutput = requiredOutput(aDefinition, locOutputs, locDefaultOutput, "DefaultBuildOutputTypes");
            if (!locOutput.canBeProduced()) {
                throw new AIxModelValidationException("Default build output '" + locDefaultOutput + "' is not producible.");
            }
        }

        for (String locDefaultOutput : aDefinition.defaultDependencyOutputTypes()) {
            AIcBuildOutputTypeDefinition locOutput = requiredOutput(aDefinition, locOutputs, locDefaultOutput, "DefaultDependencyOutputTypes");
            if (!locOutput.canBeUsedInDependency()) {
                throw new AIxModelValidationException("Default dependency output '" + locDefaultOutput + "' cannot be used in a dependency.");
            }
        }

        for (AIcBuildOutputTypeDefinition locOutput : locOutputs.values()) {
            for (String locAlternativeId : locOutput.dependencyOutputAlternatives()) {
                AIcBuildOutputTypeDefinition locAlternative = requiredOutput(aDefinition, locOutputs, locAlternativeId, "DependencyOutputAlternatives");
                if (!locAlternative.canBeProduced() || !locAlternative.canBeUsedInDependency()) {
                    throw new AIxModelValidationException(
                        "Dependency output alternative '" + locAlternativeId + "' for '" + locOutput.buildOutputType() + "' must be both producible and usable in a dependency."
                    );
                }
            }
        }
    }

    private static void validateOutput(AIcBuildOutputTypeDefinition aOutput) {
        if (!aOutput.canBeProduced() && !aOutput.canBeUsedInDependency()) {
            throw new AIxModelValidationException("BuildOutputType '" + aOutput.buildOutputType() + "' is neither producible nor usable in a dependency.");
        }
        if (!aOutput.dependencyOutputAlternatives().isEmpty()) {
            if (aOutput.canBeProduced()) {
                throw new AIxModelValidationException("BuildOutputType '" + aOutput.buildOutputType() + "' declares dependency alternatives and therefore must not be directly producible.");
            }
            if (!aOutput.canBeUsedInDependency()) {
                throw new AIxModelValidationException("BuildOutputType '" + aOutput.buildOutputType() + "' declares dependency alternatives but cannot be used in a dependency.");
            }
            if (aOutput.dependencyOutputAlternatives().contains(aOutput.buildOutputType())) {
                throw new AIxModelValidationException("BuildOutputType '" + aOutput.buildOutputType() + "' cannot list itself as a dependency output alternative.");
            }
        }
        if (!aOutput.canBeProduced() && aOutput.canBeUsedInDependency() && aOutput.dependencyOutputAlternatives().isEmpty()) {
            throw new AIxModelValidationException("Non-producible dependency BuildOutputType '" + aOutput.buildOutputType() + "' must declare DependencyOutputAlternatives.");
        }
    }

    private static AIcBuildOutputTypeDefinition requiredOutput(
        AIcTechnologyKindDefinition aDefinition,
        Map<String, AIcBuildOutputTypeDefinition> aOutputs,
        String aOutputId,
        String aSource
    ) {
        AIcBuildOutputTypeDefinition locOutput = aOutputs.get(aOutputId);
        if (locOutput == null) {
            throw new AIxModelValidationException(aSource + " of TechnologyKind '" + aDefinition.technologyKind() + "' references unknown BuildOutputType '" + aOutputId + "'.");
        }
        return locOutput;
    }
}

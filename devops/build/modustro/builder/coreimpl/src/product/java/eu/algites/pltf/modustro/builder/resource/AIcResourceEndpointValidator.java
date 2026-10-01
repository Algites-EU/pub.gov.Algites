package eu.algites.pltf.modustro.builder.resource;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointDefinition;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceKindDefinition;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceStabilityRequirement;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validates effective ResourceEndpoints against built-in ResourceKind contracts.
 */
public final class AIcResourceEndpointValidator {

    private final Map<String, AIcResourceKindDefinition> definitions;

    /**
     * Creates a validator for the supplied ResourceKind definitions.
     *
     * @param aDefinitions ResourceKind definitions keyed internally by ResourceKind identifier
     */
    public AIcResourceEndpointValidator(Iterable<AIcResourceKindDefinition> aDefinitions) {
        definitions = java.util.stream.StreamSupport.stream(aDefinitions.spliterator(), false)
            .collect(Collectors.toUnmodifiableMap(AIcResourceKindDefinition::resourceKind, Function.identity()));
    }

    /**
     * Creates a validator for the built-in ResourceKinds.
     * @return built-in ResourceEndpoint validator
     */
    public static AIcResourceEndpointValidator builtin() {
        return new AIcResourceEndpointValidator(AIcBuiltinResourceKindDefinitions.all());
    }

    /**
     * Validates one effective ResourceEndpoint.
     *
     * @param aEndpoint endpoint to validate
     * @throws AIxModelValidationException when the endpoint violates its ResourceKind contract
     */
    public void validate(AIcResourceEndpointDefinition aEndpoint) throws AIxModelValidationException {
        AIcResourceKindDefinition locDefinition = definitions.get(aEndpoint.resourceKind());
        if (locDefinition == null) {
            throw new AIxModelValidationException("Unsupported ResourceKind '" + aEndpoint.resourceKind() + "'.");
        }
        if (!locDefinition.technologyKinds().contains(aEndpoint.technologyKind())) {
            throw new AIxModelValidationException(
                "ResourceKind '" + aEndpoint.resourceKind() + "' does not support TechnologyKind '" + aEndpoint.technologyKind() + "'."
            );
        }
        if (!locDefinition.actions().contains(aEndpoint.action())) {
            throw new AIxModelValidationException(
                "ResourceKind '" + aEndpoint.resourceKind() + "' does not support action '" + aEndpoint.action().wireValue() + "'."
            );
        }
        if (locDefinition.stabilityRequirement() == AInResourceStabilityRequirement.REQUIRED && aEndpoint.stability() == null) {
            throw new AIxModelValidationException("ResourceKind '" + aEndpoint.resourceKind() + "' requires Stability.");
        }
        if (locDefinition.stabilityRequirement() == AInResourceStabilityRequirement.FORBIDDEN && aEndpoint.stability() != null) {
            throw new AIxModelValidationException("ResourceKind '" + aEndpoint.resourceKind() + "' forbids Stability.");
        }
    }
}

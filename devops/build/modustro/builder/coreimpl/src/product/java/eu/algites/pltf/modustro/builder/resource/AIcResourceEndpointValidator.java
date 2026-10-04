package eu.algites.pltf.modustro.builder.resource;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointDefinition;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceKindDefinition;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceStabilityRequirement;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates effective ResourceEndpoints against built-in ResourceKind contracts.
 */
public final class AIcResourceEndpointValidator {

    private static final Set<String> VISIBILITIES = Set.of("public", "private");
    private static final Pattern ENDPOINT_ID = Pattern.compile("^algites-[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern AUXILIARY_ID = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

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
        if (!VISIBILITIES.contains(aEndpoint.visibility())) {
            throw new AIxModelValidationException(
                "Unsupported ResourceEndpoint visibility '" + aEndpoint.visibility() + "'."
            );
        }
        if (!ENDPOINT_ID.matcher(aEndpoint.id()).matches()) {
            throw new AIxModelValidationException(
                "ResourceEndpoint id '" + aEndpoint.id() + "' must start with 'algites-' and use lowercase dash-separated form."
            );
        }
        if (aEndpoint.credentialProfile() != null && !AUXILIARY_ID.matcher(aEndpoint.credentialProfile()).matches()) {
            throw new AIxModelValidationException(
                "ResourceEndpoint '" + aEndpoint.id() + "' has invalid credential-profile id '" + aEndpoint.credentialProfile() + "'."
            );
        }
        if (aEndpoint.resourceEndpointProviderAdapter() != null
            && !AUXILIARY_ID.matcher(aEndpoint.resourceEndpointProviderAdapter()).matches()) {
            throw new AIxModelValidationException(
                "ResourceEndpoint '" + aEndpoint.id() + "' has invalid provider-adapter id '"
                    + aEndpoint.resourceEndpointProviderAdapter() + "'."
            );
        }
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
        AIcValidateIdDimensions(aEndpoint);
    }

    private static void AIcValidateIdDimensions(AIcResourceEndpointDefinition aEndpoint) {
        String locResourceKind = aEndpoint.resourceKind().replace('_', '-');
        String locSuffix = "-" + aEndpoint.technologyKind()
            + "-" + locResourceKind
            + "-" + aEndpoint.visibility()
            + (aEndpoint.stability() == null ? "" : "-" + aEndpoint.stability().wireValue())
            + "-" + aEndpoint.action().wireValue();
        String locLegacySuffix = "native_product_binaries".equals(aEndpoint.resourceKind()) && aEndpoint.stability() != null
            ? "-" + aEndpoint.technologyKind() + "-" + aEndpoint.visibility() + "-"
                + aEndpoint.stability().wireValue() + "-" + aEndpoint.action().wireValue()
            : null;
        if (!aEndpoint.id().endsWith(locSuffix)
            && !aEndpoint.id().equals("algites" + locSuffix)
            && (locLegacySuffix == null || !aEndpoint.id().endsWith(locLegacySuffix))) {
            String locCompatibilityText = locLegacySuffix == null
                ? ""
                : " (legacy native-build-output suffix '" + locLegacySuffix + "' is also accepted)";
            throw new AIxModelValidationException(
                "ResourceEndpoint id '" + aEndpoint.id() + "' must encode its dimensions and end with '"
                    + locSuffix + "'" + locCompatibilityText + "."
            );
        }
    }
}

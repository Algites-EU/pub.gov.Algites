package eu.algites.pltf.modustro.builder.resource;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointCatalog;
import eu.algites.pltf.modustro.builder.model.resource.AIcgdResourceEndpoint_1;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceEndpointAction_1;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceEndpointVisibility_1;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceStability_1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Gradle-independent bridge between normalized Algites metadata maps and generated ResourceEndpoint DTOs.
 */
public final class AIcResourceEndpointMetadataBridge {

    private final AIcResourceEndpointResolver resolver;

    /**
     * Creates a bridge using the supplied effective-model resolver.
     *
     * @param aResolver ResourceEndpoint resolver
     */
    public AIcResourceEndpointMetadataBridge(AIcResourceEndpointResolver aResolver) {
        resolver = Objects.requireNonNull(aResolver, "resolver");
    }

    /**
     * Creates a bridge using built-in ResourceKind semantics.
     *
     * @return built-in metadata bridge
     */
    public static AIcResourceEndpointMetadataBridge builtin() {
        return new AIcResourceEndpointMetadataBridge(AIcResourceEndpointResolver.builtin());
    }

    /**
     * Creates one generated declaration DTO from canonical wire values.
     *
     * @param aTechnologyKind TechnologyKind
     * @param aResourceKind ResourceKind
     * @param aVisibility visibility wire value
     * @param aAction action wire value
     * @param aId endpoint identifier
     * @param aUrl optional declared URL
     * @param aCredentialProfile optional declared credential profile
     * @param aEnabled optional declared enabled state
     * @param aStability optional stability wire value
     * @param aResourceEndpointProviderAdapter optional provider adapter
     * @return generated declaration DTO
     */
    public AIcgdResourceEndpoint_1 declaration(
        String aTechnologyKind,
        String aResourceKind,
        String aVisibility,
        String aAction,
        String aId,
        String aUrl,
        String aCredentialProfile,
        Boolean aEnabled,
        String aStability,
        String aResourceEndpointProviderAdapter
    ) {
        return new AIcgdResourceEndpoint_1(
            AIcRequireText(aTechnologyKind, "TechnologyKind"),
            AIcRequireText(aResourceKind, "ResourceKind"),
            AIcEnumValue(AIngResourceEndpointVisibility_1.values(), aVisibility, "Visibility"),
            AIcEnumValue(AIngResourceEndpointAction_1.values(), aAction, "Action"),
            AIcRequireText(aId, "Id"),
            AIcNormalize(aUrl),
            AIcNormalize(aCredentialProfile),
            aEnabled,
            aStability == null ? null : AIcEnumValue(AIngResourceStability_1.values(), aStability, "Stability"),
            AIcNormalize(aResourceEndpointProviderAdapter)
        );
    }

    /**
     * Converts normalized metadata output into generated declarations.
     *
     * <p>The expected shape is a map keyed by
     * {@code TechnologyKind.ResourceKind.Visibility.Action}; each value is an iterable of endpoint maps using
     * the normalized lower-camel property names emitted by the Algites metadata resolver.</p>
     *
     * @param aResourceEndpoints normalized ResourceEndpoint metadata
     * @return immutable generated declaration list
     */
    public List<AIcgdResourceEndpoint_1> declarations(Map<?, ?> aResourceEndpoints) {
        if (aResourceEndpoints == null || aResourceEndpoints.isEmpty()) {
            return List.of();
        }
        ArrayList<AIcgdResourceEndpoint_1> locDeclarations = new ArrayList<>();
        for (Map.Entry<?, ?> locEntry : aResourceEndpoints.entrySet()) {
            String locCell = locEntry.getKey() == null ? null : locEntry.getKey().toString();
            String[] locSegments = locCell == null ? new String[0] : locCell.split("\\.", -1);
            if (locSegments.length != 4) {
                throw new AIxModelValidationException("Invalid normalized ResourceEndpoint cell '" + locCell + "'.");
            }
            if (!(locEntry.getValue() instanceof Iterable<?> locItems)) {
                throw new AIxModelValidationException("ResourceEndpoint cell '" + locCell + "' must contain an endpoint list.");
            }
            for (Object locRawItem : locItems) {
                if (!(locRawItem instanceof Map<?, ?> locItem)) {
                    throw new AIxModelValidationException("ResourceEndpoint cell '" + locCell + "' contains a non-object endpoint item.");
                }
                locDeclarations.add(declaration(
                    locSegments[0],
                    locSegments[1],
                    locSegments[2],
                    locSegments[3],
                    AIcString(locItem.get("id")),
                    AIcString(locItem.get("url")),
                    AIcString(locItem.get("credentialProfile")),
                    AIcBoolean(locItem.get("enabled")),
                    AIcString(locItem.get("stability")),
                    AIcString(locItem.get("resourceEndpointProviderAdapter"))
                ));
            }
        }
        return List.copyOf(locDeclarations);
    }

    /**
     * Resolves normalized metadata into the effective ResourceEndpoint catalog.
     *
     * @param aResourceEndpoints normalized ResourceEndpoint metadata
     * @return effective endpoint catalog
     */
    public AIcResourceEndpointCatalog resolve(Map<?, ?> aResourceEndpoints) {
        return resolver.resolve(declarations(aResourceEndpoints));
    }

    private static String AIcString(Object aValue) {
        if (aValue == null) {
            return null;
        }
        String locValue = aValue.toString().trim();
        return locValue.isEmpty() || "null".equals(locValue) ? null : locValue;
    }

    private static Boolean AIcBoolean(Object aValue) {
        if (aValue == null) {
            return null;
        }
        if (aValue instanceof Boolean locBoolean) {
            return locBoolean;
        }
        String locValue = aValue.toString().trim();
        if ("true".equalsIgnoreCase(locValue)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(locValue)) {
            return Boolean.FALSE;
        }
        throw new AIxModelValidationException("Invalid ResourceEndpoint Enabled value '" + aValue + "'.");
    }

    private static String AIcRequireText(String aValue, String aName) {
        String locValue = AIcNormalize(aValue);
        if (locValue == null) {
            throw new AIxModelValidationException("ResourceEndpoint " + aName + " must not be blank.");
        }
        return locValue;
    }

    private static String AIcNormalize(String aValue) {
        if (aValue == null) {
            return null;
        }
        String locValue = aValue.trim();
        return locValue.isEmpty() ? null : locValue;
    }

    private static <T extends Enum<T>> T AIcEnumValue(T[] aValues, String aWireValue, String aName) {
        String locWireValue = AIcRequireText(aWireValue, aName);
        for (T locValue : aValues) {
            try {
                Object locCandidate = locValue.getClass().getMethod("wireValue").invoke(locValue);
                if (locWireValue.equals(locCandidate)) {
                    return locValue;
                }
            } catch (ReflectiveOperationException locException) {
                throw new AIxModelValidationException(
                    "Generated enum '" + locValue.getClass().getName() + "' does not expose wireValue()."
                );
            }
        }
        throw new AIxModelValidationException("Unsupported ResourceEndpoint " + aName + " '" + locWireValue + "'.");
    }
}

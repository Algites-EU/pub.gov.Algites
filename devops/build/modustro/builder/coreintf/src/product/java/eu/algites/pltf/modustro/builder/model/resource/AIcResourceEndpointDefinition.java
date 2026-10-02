package eu.algites.pltf.modustro.builder.model.resource;

import java.net.URI;
import java.util.Objects;

/**
 * Gradle-independent effective ResourceEndpoint definition.
 */
public final class AIcResourceEndpointDefinition {

    private final String technologyKind;
    private final String resourceKind;
    private final String visibility;
    private final AInResourceEndpointAction action;
    private final String id;
    private final URI url;
    private final String credentialProfile;
    private final boolean enabled;
    private final AInResourceStability stability;
    private final String resourceEndpointProviderAdapter;

    /**
     * Creates an effective ResourceEndpoint definition.
     *
     * @param aTechnologyKind TechnologyKind owning the endpoint protocol
     * @param aResourceKind ResourceKind handled by the endpoint
     * @param aVisibility endpoint visibility, normally {@code public} or {@code private}
     * @param aAction endpoint action
     * @param aId stable endpoint identifier
     * @param aUrl endpoint URI
     * @param aCredentialProfile optional credential-profile identifier
     * @param aEnabled whether the endpoint is enabled
     * @param aStability optional endpoint stability
     * @param aResourceEndpointProviderAdapter optional provider-specific adapter identifier
     */
    public AIcResourceEndpointDefinition(
        String aTechnologyKind,
        String aResourceKind,
        String aVisibility,
        AInResourceEndpointAction aAction,
        String aId,
        URI aUrl,
        String aCredentialProfile,
        boolean aEnabled,
        AInResourceStability aStability,
        String aResourceEndpointProviderAdapter
    ) {
        technologyKind = requireText(aTechnologyKind, "technologyKind");
        resourceKind = requireText(aResourceKind, "resourceKind");
        visibility = requireText(aVisibility, "visibility");
        action = Objects.requireNonNull(aAction, "action");
        id = requireText(aId, "id");
        url = Objects.requireNonNull(aUrl, "url");
        credentialProfile = normalize(aCredentialProfile);
        enabled = aEnabled;
        stability = aStability;
        resourceEndpointProviderAdapter = normalize(aResourceEndpointProviderAdapter);
    }

    private static String requireText(String aValue, String aName) {
        String locValue = normalize(aValue);
        if (locValue == null) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    private static String normalize(String aValue) {
        if (aValue == null) {
            return null;
        }
        String locValue = aValue.trim();
        return locValue.isEmpty() ? null : locValue;
    }

    /**
     * Returns the TechnologyKind.
     * @return TechnologyKind identifier
     */
    public String technologyKind() {
        return technologyKind;
    }

    /**
     * Returns the ResourceKind.
     * @return ResourceKind identifier
     */
    public String resourceKind() {
        return resourceKind;
    }

    /**
     * Returns endpoint visibility.
     * @return visibility identifier
     */
    public String visibility() {
        return visibility;
    }

    /**
     * Returns endpoint action.
     * @return endpoint action
     */
    public AInResourceEndpointAction action() {
        return action;
    }

    /**
     * Returns endpoint identifier.
     * @return endpoint identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns endpoint URI.
     * @return endpoint URI
     */
    public URI url() {
        return url;
    }

    /**
     * Returns the optional credential-profile identifier.
     * @return credential-profile identifier, or {@code null}
     */
    public String credentialProfile() {
        return credentialProfile;
    }

    /**
     * Returns whether the endpoint is enabled.
     * @return enabled state
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the optional stability discriminator.
     * @return endpoint stability, or {@code null}
     */
    public AInResourceStability stability() {
        return stability;
    }

    /**
     * Returns the optional provider-specific endpoint adapter identifier.
     * @return provider adapter identifier, or {@code null}
     */
    public String resourceEndpointProviderAdapter() {
        return resourceEndpointProviderAdapter;
    }

    /**
     * Returns the canonical four-dimensional ResourceEndpoint cell identifier.
     *
     * @return cell identifier in TechnologyKind.ResourceKind.Visibility.Action form
     */
    public String cell() {
        return technologyKind + "." + resourceKind + "." + visibility + "." + action.wireValue();
    }
}

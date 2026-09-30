package eu.algites.pltf.modustro.builder.model.capability;

import eu.algites.pltf.modustro.builder.model.AInModelScope;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Definition of a build-technology capability and its allowed configuration scopes.
 */
public final class AIcCapabilityDefinition {

    private final String capabilityId;
    private final Set<AInModelScope> allowedScopes;
    private final String configurationSchemaId;

    public AIcCapabilityDefinition(String aCapabilityId, Set<AInModelScope> aAllowedScopes, String aConfigurationSchemaId) {
        capabilityId = requireText(aCapabilityId, "capabilityId");
        allowedScopes = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aAllowedScopes, "allowedScopes")));
        if (allowedScopes.isEmpty()) {
            throw new IllegalArgumentException("Capability allowedScopes must not be empty.");
        }
        configurationSchemaId = normalize(aConfigurationSchemaId);
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

    public String capabilityId() {
        return capabilityId;
    }

    public Set<AInModelScope> allowedScopes() {
        return allowedScopes;
    }

    public String configurationSchemaId() {
        return configurationSchemaId;
    }
}

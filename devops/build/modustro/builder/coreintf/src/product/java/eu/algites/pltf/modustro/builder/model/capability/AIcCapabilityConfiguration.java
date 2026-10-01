package eu.algites.pltf.modustro.builder.model.capability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Portable effective configuration of one TechnologyKind capability.
 */
public final class AIcCapabilityConfiguration {

    private final String technologyKind;
    private final String capabilityId;
    private final boolean enabled;
    private final Map<String, String> values;

    /**
     * Creates an {@code AIcCapabilityConfiguration} instance.
     *
     * @param aTechnologyKind TechnologyKind identifier
     * @param aCapabilityId capability identifier
     * @param aEnabled whether the capability is enabled
     * @param aValues effective configuration values
     */
    public AIcCapabilityConfiguration(
        String aTechnologyKind,
        String aCapabilityId,
        boolean aEnabled,
        Map<String, String> aValues
    ) {
        technologyKind = requireText(aTechnologyKind, "technologyKind");
        capabilityId = requireText(aCapabilityId, "capabilityId");
        enabled = aEnabled;
        values = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(aValues, "values")));
    }

    private static String requireText(String aValue, String aName) {
        Objects.requireNonNull(aValue, aName);
        String locValue = aValue.trim();
        if (locValue.isEmpty()) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    /**
     * Returns the TechnologyKind identifier.
     * @return TechnologyKind identifier
     */
    public String technologyKind() {
        return technologyKind;
    }

    /**
     * Returns the capability identifier.
     * @return capability identifier
     */
    public String capabilityId() {
        return capabilityId;
    }

    /**
     * Returns whether the capability is enabled.
     * @return whether the capability is enabled
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the effective capability configuration values.
     * @return immutable configuration values
     */
    public Map<String, String> values() {
        return values;
    }
}

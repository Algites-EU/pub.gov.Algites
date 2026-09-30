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

    public String technologyKind() {
        return technologyKind;
    }

    public String capabilityId() {
        return capabilityId;
    }

    public boolean enabled() {
        return enabled;
    }

    public Map<String, String> values() {
        return values;
    }
}

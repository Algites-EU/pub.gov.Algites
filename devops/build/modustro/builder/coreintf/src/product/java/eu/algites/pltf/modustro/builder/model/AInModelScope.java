package eu.algites.pltf.modustro.builder.model;

import java.util.Arrays;

/**
 * Structural scopes at which a Modustro Builder capability may be configured.
 */
public enum AInModelScope {
    REPOSITORY("repository"),
    ARTIFACT_SET("artifact_set"),
    ARTIFACT("artifact");

    private final String wireValue;

    AInModelScope(String aWireValue) {
        wireValue = aWireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static AInModelScope fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported model scope '" + aWireValue + "'."));
    }
}

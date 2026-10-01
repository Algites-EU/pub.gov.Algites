package eu.algites.pltf.modustro.builder.model;

import java.util.Arrays;

/**
 * Structural scopes at which a Modustro Builder capability may be configured.
 */
public enum AInModelScope {
    /**
     * Repository scope.
     */
    REPOSITORY("repository"),
    /**
     * Artifact-set scope.
     */
    ARTIFACT_SET("artifact_set"),
    /**
     * Artifact scope.
     */
    ARTIFACT("artifact");

    private final String wireValue;

    AInModelScope(String aWireValue) {
        wireValue = aWireValue;
    }

    /**
     * Returns the serialized wire value.
     * @return serialized wire value
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Resolves the enum constant represented by a serialized wire value.
     *
     * @param aWireValue serialized wire value
     * @return matching enum constant
     * @throws IllegalArgumentException if the wire value is unsupported
     */
    public static AInModelScope fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported model scope '" + aWireValue + "'."));
    }
}

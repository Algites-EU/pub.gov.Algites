package eu.algites.pltf.modustro.builder.model.resource;

import java.util.Arrays;

/**
 * Stability channels that may distinguish deployed resources.
 */
public enum AInResourceStability {
    /**
     * Immutable or production release channel.
     */
    RELEASE("release"),
    /**
     * Mutable development or snapshot channel.
     */
    SNAPSHOT("snapshot");

    private final String wireValue;

    AInResourceStability(String aWireValue) {
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
     * Resolves the stability represented by a serialized wire value.
     *
     * @param aWireValue serialized wire value
     * @return matching stability
     * @throws IllegalArgumentException if the wire value is unsupported
     */
    public static AInResourceStability fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported resource stability '" + aWireValue + "'."));
    }
}

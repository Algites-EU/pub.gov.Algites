package eu.algites.pltf.modustro.builder.model.resource;

import java.util.Arrays;

/**
 * Actions that may be performed through a Modustro Builder resource endpoint.
 */
public enum AInResourceEndpointAction {
    /**
     * Consumes or resolves a resource from the endpoint.
     */
    DOWNLOAD("download"),
    /**
     * Publishes or deploys a resource to the endpoint.
     */
    UPLOAD("upload"),
    /**
     * Performs provider-specific lifecycle or maintenance operations.
     */
    MANAGE("manage");

    private final String wireValue;

    AInResourceEndpointAction(String aWireValue) {
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
     * Resolves the action represented by a serialized wire value.
     *
     * @param aWireValue serialized wire value
     * @return matching action
     * @throws IllegalArgumentException if the wire value is unsupported
     */
    public static AInResourceEndpointAction fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported resource endpoint action '" + aWireValue + "'."));
    }
}

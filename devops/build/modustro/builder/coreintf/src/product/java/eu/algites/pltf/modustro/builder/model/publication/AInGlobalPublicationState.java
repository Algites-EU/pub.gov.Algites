package eu.algites.pltf.modustro.builder.model.publication;

import java.util.Arrays;

/**
 * Lifecycle states of globally published canonical definitions.
 */
public enum AInGlobalPublicationState {
    /** Mutable draft publication. */
    DRAFT("draft"),
    /** Immutable released publication. */
    RELEASE("release");

    private final String wireValue;

    AInGlobalPublicationState(String aWireValue) {
        wireValue = aWireValue;
    }

    /**
     * Returns the serialized wire value.
     * @return serialized state value
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Resolves a publication state from its wire representation.
     *
     * @param aWireValue serialized state value
     * @return matching publication state
     */
    public static AInGlobalPublicationState fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported global publication state '" + aWireValue + "'."));
    }
}

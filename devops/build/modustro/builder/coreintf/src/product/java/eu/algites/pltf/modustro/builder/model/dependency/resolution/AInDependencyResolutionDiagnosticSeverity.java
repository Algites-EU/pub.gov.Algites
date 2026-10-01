package eu.algites.pltf.modustro.builder.model.dependency.resolution;

import java.util.Arrays;

/**
 * Severity of a non-fatal dependency-bridge diagnostic.
 */
public enum AInDependencyResolutionDiagnosticSeverity {
    /**
     * Informational diagnostic.
     */
    INFO("info"),
    /**
     * Warning diagnostic.
     */
    WARNING("warning");

    private final String wireValue;

    AInDependencyResolutionDiagnosticSeverity(String aWireValue) {
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
    public static AInDependencyResolutionDiagnosticSeverity fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported dependency resolution diagnostic severity '" + aWireValue + "'."));
    }
}

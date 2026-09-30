package eu.algites.pltf.modustro.builder.model.dependency.resolution;

import java.util.Arrays;

/**
 * Severity of a non-fatal dependency-bridge diagnostic.
 */
public enum AInDependencyResolutionDiagnosticSeverity {
    INFO("info"),
    WARNING("warning");

    private final String wireValue;

    AInDependencyResolutionDiagnosticSeverity(String aWireValue) {
        wireValue = aWireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static AInDependencyResolutionDiagnosticSeverity fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported dependency resolution diagnostic severity '" + aWireValue + "'."));
    }
}

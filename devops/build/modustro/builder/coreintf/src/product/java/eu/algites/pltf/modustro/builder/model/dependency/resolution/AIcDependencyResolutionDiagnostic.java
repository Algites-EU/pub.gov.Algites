package eu.algites.pltf.modustro.builder.model.dependency.resolution;

import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyIdentity;
import java.util.Objects;

/**
 * Diagnostic produced while translating portable dependency semantics to one technology.
 */
public final class AIcDependencyResolutionDiagnostic {

    private final String code;
    private final AInDependencyResolutionDiagnosticSeverity severity;
    private final AIcDependencyIdentity dependencyIdentity;
    private final String message;

    /**
     * Creates an {@code AIcDependencyResolutionDiagnostic} instance.
     *
     * @param aCode diagnostic code
     * @param aSeverity diagnostic severity
     * @param aDependencyIdentity dependency identity associated with the diagnostic
     * @param aMessage diagnostic message
     */
    public AIcDependencyResolutionDiagnostic(
        String aCode,
        AInDependencyResolutionDiagnosticSeverity aSeverity,
        AIcDependencyIdentity aDependencyIdentity,
        String aMessage
    ) {
        code = requireText(aCode, "code");
        severity = Objects.requireNonNull(aSeverity, "severity");
        dependencyIdentity = Objects.requireNonNull(aDependencyIdentity, "dependencyIdentity");
        message = requireText(aMessage, "message");
    }

    private static String requireText(String aValue, String aName) {
        String locValue = Objects.requireNonNull(aValue, aName).trim();
        if (locValue.isEmpty()) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    /**
     * Returns the stable diagnostic code.
     * @return diagnostic code
     */
    public String code() {
        return code;
    }

    /**
     * Returns the diagnostic severity.
     * @return diagnostic severity
     */
    public AInDependencyResolutionDiagnosticSeverity severity() {
        return severity;
    }

    /**
     * Returns the dependency identity associated with this diagnostic.
     * @return dependency identity
     */
    public AIcDependencyIdentity dependencyIdentity() {
        return dependencyIdentity;
    }

    /**
     * Returns the human-readable diagnostic message.
     * @return diagnostic message
     */
    public String message() {
        return message;
    }
}

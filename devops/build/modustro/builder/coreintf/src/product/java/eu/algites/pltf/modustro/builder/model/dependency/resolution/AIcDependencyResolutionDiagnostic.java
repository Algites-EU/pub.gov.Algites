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

    public String code() {
        return code;
    }

    public AInDependencyResolutionDiagnosticSeverity severity() {
        return severity;
    }

    public AIcDependencyIdentity dependencyIdentity() {
        return dependencyIdentity;
    }

    public String message() {
        return message;
    }
}

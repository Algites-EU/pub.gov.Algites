package eu.algites.pltf.modustro.builder.dependency;

import eu.algites.lib.common.version.AIcVersionBound;
import eu.algites.lib.common.version.AIsVersionRequirementNormalizer;
import eu.algites.lib.common.version.scheme.algites.v1.AIcAlgitesVersionTextV1;
import eu.algites.lib.common.version.scheme.conversion.algites2gradle.v1.AIcAlgitesToGradleVersionConverterV1;
import eu.algites.lib.common.version.scheme.conversion.algites2pep440.v1.AIcAlgitesToPep440VersionConverterV1;
import eu.algites.lib.common.version.scheme.conversion.algites2pep440.v1.AIcAlgitesVersionRequirementToPep440ConverterV1;
import eu.algites.lib.common.version.scheme.gradle.AIcGradleVersionScheme;
import eu.algites.lib.common.version.scheme.pep440.AIcPep440VersionScheme;
import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyIdentity;
import eu.algites.pltf.modustro.builder.model.dependency.AIcVersionRequirement;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AIcDependencyResolutionDiagnostic;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AInDependencyResolutionDiagnosticSeverity;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Converts portable Modustro dependency version metadata into the target technology's native version scheme.
 */
public final class AIcDependencyVersionTechnologyBridge {

    private AIcDependencyVersionTechnologyBridge() {
    }

    /**
     * Translates a portable version requirement into technology-specific canonical version semantics.
     *
     * @param aIdentity dependency or item identity
     * @param aRequirement portable version requirement
     * @param aTechnologyKind TechnologyKind identifier
     * @param aDiagnostics resolution diagnostics
     * @return normalized version requirement, or {@code null} when {@code aRequirement} is {@code null}
     * @throws AIxModelValidationException if the requirement cannot be represented by the target technology
     */
    public static AIcVersionRequirement normalize(
        AIcDependencyIdentity aIdentity,
        AIcVersionRequirement aRequirement,
        String aTechnologyKind,
        List<AIcDependencyResolutionDiagnostic> aDiagnostics
    ) {
        if (aRequirement == null) {
            return null;
        }
        try {
            eu.algites.lib.common.version.AIcVersionRequirement locNativeRequirement;
            if ("modustro".equals(aIdentity.dependencyKind()) && "python".equals(aTechnologyKind)) {
                var locAlgitesRequirement = new eu.algites.lib.common.version.AIcVersionRequirement(
                    aRequirement.exact(),
                    commonBound(aRequirement.minimum()),
                    commonBound(aRequirement.maximum()),
                    aRequirement.maximumStrict(),
                    List.copyOf(aRequirement.excludedVersions()),
                    aRequirement.preferred()
                );
                locNativeRequirement = AIcAlgitesVersionRequirementToPep440ConverterV1.convert(locAlgitesRequirement);
            } else {
                locNativeRequirement = new eu.algites.lib.common.version.AIcVersionRequirement(
                    nativeVersion(aIdentity.dependencyKind(), aTechnologyKind, aRequirement.exact()),
                    nativeBound(aIdentity.dependencyKind(), aTechnologyKind, aRequirement.minimum()),
                    nativeBound(aIdentity.dependencyKind(), aTechnologyKind, aRequirement.maximum()),
                    aRequirement.maximumStrict(),
                    aRequirement.excludedVersions().stream()
                        .map(locVersion -> nativeVersion(aIdentity.dependencyKind(), aTechnologyKind, locVersion))
                        .toList(),
                    nativeVersion(aIdentity.dependencyKind(), aTechnologyKind, aRequirement.preferred())
                );
            }
            var locScheme = switch (aTechnologyKind) {
                case "java" -> AIcGradleVersionScheme.INSTANCE;
                case "python" -> AIcPep440VersionScheme.INSTANCE;
                default -> throw new AIxModelValidationException(
                    "Unsupported TechnologyKind '" + aTechnologyKind + "' for dependency version normalization."
                );
            };
            var locNormalization = AIsVersionRequirementNormalizer.normalize(locNativeRequirement, locScheme);
            for (String locMessage : locNormalization.informationMessages()) {
                aDiagnostics.add(new AIcDependencyResolutionDiagnostic(
                    "VERSION_REQUIREMENT_NORMALIZED",
                    AInDependencyResolutionDiagnosticSeverity.INFO,
                    aIdentity,
                    locMessage
                ));
            }
            var locRequirement = locNormalization.requirement();
            return new AIcVersionRequirement(
                locRequirement.exactVersionText(),
                locRequirement.minimum() == null
                    ? null
                    : new eu.algites.pltf.modustro.builder.model.dependency.AIcVersionBound(
                        locRequirement.minimum().versionText(),
                        locRequirement.minimum().inclusive()
                    ),
                locRequirement.maximum() == null
                    ? null
                    : new eu.algites.pltf.modustro.builder.model.dependency.AIcVersionBound(
                        locRequirement.maximum().versionText(),
                        locRequirement.maximum().inclusive()
                    ),
                locRequirement.maximumStrict(),
                new LinkedHashSet<>(locRequirement.excludedVersionTexts()),
                locRequirement.preferredVersionText()
            );
        } catch (IllegalArgumentException locException) {
            throw new AIxModelValidationException(
                "Dependency '" + aIdentity.artifactId() + "' has incompatible effective version requirements for TechnologyKind '"
                    + aTechnologyKind + "': " + locException.getMessage()
            );
        }
    }

    private static AIcVersionBound commonBound(
        eu.algites.pltf.modustro.builder.model.dependency.AIcVersionBound aBound
    ) {
        if (aBound == null) {
            return null;
        }
        return new AIcVersionBound(aBound.version(), aBound.inclusive());
    }

    private static AIcVersionBound nativeBound(
        String aDependencyKind,
        String aTechnologyKind,
        eu.algites.pltf.modustro.builder.model.dependency.AIcVersionBound aBound
    ) {
        if (aBound == null) {
            return null;
        }
        return new AIcVersionBound(
            nativeVersion(aDependencyKind, aTechnologyKind, aBound.version()),
            aBound.inclusive()
        );
    }

    private static String nativeVersion(String aDependencyKind, String aTechnologyKind, String aVersion) {
        if (aVersion == null || !"modustro".equals(aDependencyKind)) {
            return aVersion;
        }
        return switch (aTechnologyKind) {
            case "java" -> {
                var locResult = new AIcAlgitesToGradleVersionConverterV1().convert(AIcAlgitesVersionTextV1.parse(aVersion)).value();
                if (locResult == null) {
                    throw new IllegalArgumentException("Modustro version '" + aVersion + "' cannot be converted to Gradle");
                }
                String locVersion = locResult.strictly() != null ? locResult.strictly() : locResult.require();
                if (locVersion == null || locVersion.isBlank()) {
                    throw new IllegalArgumentException("Modustro version '" + aVersion + "' did not produce an exact Gradle version");
                }
                yield locVersion;
            }
            case "python" -> {
                var locResult = new AIcAlgitesToPep440VersionConverterV1().convert(AIcAlgitesVersionTextV1.parse(aVersion)).value();
                if (locResult == null || locResult.text().isBlank()) {
                    throw new IllegalArgumentException("Modustro version '" + aVersion + "' cannot be converted to PEP 440");
                }
                yield locResult.text();
            }
            default -> throw new IllegalArgumentException(
                "Unsupported TechnologyKind '" + aTechnologyKind + "' for Modustro version conversion"
            );
        };
    }
}

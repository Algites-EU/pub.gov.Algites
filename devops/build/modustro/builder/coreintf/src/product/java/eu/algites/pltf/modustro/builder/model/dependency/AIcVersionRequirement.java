package eu.algites.pltf.modustro.builder.model.dependency;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Effective portable version requirement.
 *
 * Preferred versions are intentionally retained even when a harder effective
 * exact or range rule supersedes them so resolution can emit diagnostics.
 */
public final class AIcVersionRequirement {

    private final String exact;
    private final AIcVersionBound minimum;
    private final AIcVersionBound maximum;
    private final Boolean maximumStrict;
    private final Set<String> excludedVersions;
    private final String preferred;

    /**
     * Creates an {@code AIcVersionRequirement} instance.
     *
     * @param aExact optional exact version
     * @param aMinimum optional minimum version bound
     * @param aMaximum optional maximum version bound
     * @param aMaximumStrict whether the maximum bound is strict for staged resolution
     * @param aExcludedVersions explicitly excluded versions
     * @param aPreferred optional preferred version
     */
    public AIcVersionRequirement(
        String aExact,
        AIcVersionBound aMinimum,
        AIcVersionBound aMaximum,
        Boolean aMaximumStrict,
        Set<String> aExcludedVersions,
        String aPreferred
    ) {
        exact = normalize(aExact);
        minimum = aMinimum;
        maximum = aMaximum;
        maximumStrict = aMaximumStrict;
        excludedVersions = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aExcludedVersions, "excludedVersions")));
        preferred = normalize(aPreferred);
    }

    private static String normalize(String aValue) {
        if (aValue == null) {
            return null;
        }
        String locValue = aValue.trim();
        return locValue.isEmpty() ? null : locValue;
    }

    /**
     * Returns the optional exact version.
     * @return exact version, or {@code null}
     */
    public String exact() {
        return exact;
    }

    /**
     * Returns the optional minimum version bound.
     * @return minimum bound, or {@code null}
     */
    public AIcVersionBound minimum() {
        return minimum;
    }

    /**
     * Returns the optional maximum version bound.
     * @return maximum bound, or {@code null}
     */
    public AIcVersionBound maximum() {
        return maximum;
    }

    /**
     * Returns the optional strictness flag for the maximum bound.
     * @return maximum strictness flag, or {@code null}
     */
    public Boolean maximumStrict() {
        return maximumStrict;
    }

    /**
     * Returns explicitly excluded versions.
     * @return immutable set of excluded versions
     */
    public Set<String> excludedVersions() {
        return excludedVersions;
    }

    /**
     * Returns the optional preferred version.
     * @return preferred version, or {@code null}
     */
    public String preferred() {
        return preferred;
    }
}

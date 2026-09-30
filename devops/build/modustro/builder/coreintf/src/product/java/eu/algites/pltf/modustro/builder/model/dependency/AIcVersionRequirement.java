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

    public String exact() {
        return exact;
    }

    public AIcVersionBound minimum() {
        return minimum;
    }

    public AIcVersionBound maximum() {
        return maximum;
    }

    public Boolean maximumStrict() {
        return maximumStrict;
    }

    public Set<String> excludedVersions() {
        return excludedVersions;
    }

    public String preferred() {
        return preferred;
    }
}

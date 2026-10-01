package eu.algites.pltf.modustro.builder.model.dependency;

import java.util.Objects;

/**
 * Inclusive or exclusive version bound.
 */
public final class AIcVersionBound {

    private final String version;
    private final boolean inclusive;

    /**
     * Creates an {@code AIcVersionBound} instance.
     *
     * @param aVersion version text
     * @param aInclusive whether the bound is inclusive
     */
    public AIcVersionBound(String aVersion, boolean aInclusive) {
        version = Objects.requireNonNull(aVersion, "version");
        if (version.isBlank()) {
            throw new IllegalArgumentException("Version bound must not be blank.");
        }
        inclusive = aInclusive;
    }

    /**
     * Returns the version text of the bound.
     * @return version text
     */
    public String version() {
        return version;
    }

    /**
     * Returns whether the bound includes its version.
     * @return whether the bound is inclusive
     */
    public boolean inclusive() {
        return inclusive;
    }
}

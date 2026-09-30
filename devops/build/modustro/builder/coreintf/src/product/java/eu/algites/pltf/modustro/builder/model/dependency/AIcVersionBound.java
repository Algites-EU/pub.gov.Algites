package eu.algites.pltf.modustro.builder.model.dependency;

import java.util.Objects;

/**
 * Inclusive or exclusive version bound.
 */
public final class AIcVersionBound {

    private final String version;
    private final boolean inclusive;

    public AIcVersionBound(String aVersion, boolean aInclusive) {
        version = Objects.requireNonNull(aVersion, "version");
        if (version.isBlank()) {
            throw new IllegalArgumentException("Version bound must not be blank.");
        }
        inclusive = aInclusive;
    }

    public String version() {
        return version;
    }

    public boolean inclusive() {
        return inclusive;
    }
}

package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationStabilityConfiguration;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationInvocationOverride;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationStability;

import java.util.Objects;

/** Applies invocation-time policy to an already inherited publication stability configuration. */
public final class AIcPublicationPlanResolver {
    /** Resolves output-level publication enablement while leaving endpoint ExecutionEnabled values untouched. */
    public AIcPublicationStabilityConfiguration resolve(
            AIcPublicationStabilityConfiguration aConfigured,
            AInPublicationStability aStability,
            AInPublicationInvocationOverride aOverride) {
        Objects.requireNonNull(aConfigured, "configured");
        Objects.requireNonNull(aStability, "stability");
        Objects.requireNonNull(aOverride, "override");

        if (aStability == AInPublicationStability.RELEASE && aOverride != AInPublicationInvocationOverride.DEFAULT) {
            throw new IllegalArgumentException("Release publication does not permit invocation-time publication overrides.");
        }

        boolean locEnabled = switch (aOverride) {
            case DEFAULT -> aConfigured.publicationEnabled();
            case FORCE_ON -> true;
            case FORCE_OFF -> false;
        };
        return new AIcPublicationStabilityConfiguration(locEnabled, aConfigured.publicationEndpoints());
    }
}

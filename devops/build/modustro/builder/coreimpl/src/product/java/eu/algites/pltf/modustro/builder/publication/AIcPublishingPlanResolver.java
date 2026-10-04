package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingStabilityConfiguration;
import eu.algites.pltf.modustro.builder.model.publication.AInPublishingInvocationOverride;
import eu.algites.pltf.modustro.builder.model.publication.AInPublishingStability;

import java.util.Objects;

/** Applies invocation-time policy to an already inherited publishing stability configuration. */
public final class AIcPublishingPlanResolver {
    /** Resolves output-level publishing enablement while leaving endpoint Enabled values untouched. */
    public AIcPublishingStabilityConfiguration resolve(
            AIcPublishingStabilityConfiguration aConfigured,
            AInPublishingStability aStability,
            AInPublishingInvocationOverride aOverride) {
        Objects.requireNonNull(aConfigured, "configured");
        Objects.requireNonNull(aStability, "stability");
        Objects.requireNonNull(aOverride, "override");

        if (aStability == AInPublishingStability.RELEASE && aOverride != AInPublishingInvocationOverride.DEFAULT) {
            throw new IllegalArgumentException("Release publishing does not permit invocation-time publishing overrides.");
        }

        boolean locEnabled = switch (aOverride) {
            case DEFAULT -> aConfigured.publishingEnabled();
            case FORCE_ON -> true;
            case FORCE_OFF -> false;
        };
        return new AIcPublishingStabilityConfiguration(locEnabled, aConfigured.endpointPublications());
    }
}

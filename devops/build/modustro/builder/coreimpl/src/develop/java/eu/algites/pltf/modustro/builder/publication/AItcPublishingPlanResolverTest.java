package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingStabilityConfiguration;
import eu.algites.pltf.modustro.builder.model.publication.AInPublishingFailurePolicy;
import eu.algites.pltf.modustro.builder.model.publication.AInPublishingInvocationOverride;
import eu.algites.pltf.modustro.builder.model.publication.AInPublishingStability;
import java.net.URI;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests snapshot publishing invocation overrides and release reproducibility rules. */
public class AItcPublishingPlanResolverTest {

    private static AIcPublishingEndpoint endpoint(boolean aEnabled) {
        return new AIcPublishingEndpoint(
            "primary",
            aEnabled,
            URI.create("https://example.invalid/repository"),
            "maven-repository",
            null,
            0,
            AInPublishingFailurePolicy.FAIL_BUILD_ON_PUBLISHING_FAILURE,
            0,
            1000L,
            30000L,
            true
        );
    }

    /** Verifies that snapshot FORCE_ON changes only output-level enablement. */
    @Test
    public void testSnapshotForceOnDoesNotEnableDisabledEndpoint() {
        AIcPublishingStabilityConfiguration locConfigured =
            new AIcPublishingStabilityConfiguration(false, List.of(endpoint(false)));
        AIcPublishingStabilityConfiguration locResolved = new AIcPublishingPlanResolver().resolve(
            locConfigured,
            AInPublishingStability.SNAPSHOT,
            AInPublishingInvocationOverride.FORCE_ON
        );
        Assert.assertTrue(locResolved.publishingEnabled());
        Assert.assertFalse(locResolved.publishingEndpoints().get(0).enabled());
    }

    /** Verifies that snapshot FORCE_OFF disables publishing without modifying endpoint configuration. */
    @Test
    public void testSnapshotForceOffPreservesEndpointConfiguration() {
        AIcPublishingStabilityConfiguration locConfigured =
            new AIcPublishingStabilityConfiguration(true, List.of(endpoint(true)));
        AIcPublishingStabilityConfiguration locResolved = new AIcPublishingPlanResolver().resolve(
            locConfigured,
            AInPublishingStability.SNAPSHOT,
            AInPublishingInvocationOverride.FORCE_OFF
        );
        Assert.assertFalse(locResolved.publishingEnabled());
        Assert.assertTrue(locResolved.publishingEndpoints().get(0).enabled());
    }

    /** Verifies that release publishing rejects invocation-time publishing overrides. */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testReleaseRejectsInvocationOverride() {
        AIcPublishingStabilityConfiguration locConfigured =
            new AIcPublishingStabilityConfiguration(true, List.of(endpoint(true)));
        new AIcPublishingPlanResolver().resolve(
            locConfigured,
            AInPublishingStability.RELEASE,
            AInPublishingInvocationOverride.FORCE_OFF
        );
    }
}

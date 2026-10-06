package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationStabilityConfiguration;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationFailurePolicy;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationInvocationOverride;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationStability;
import java.net.URI;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests snapshot publication invocation overrides and release reproducibility rules. */
public class AItcPublicationPlanResolverTest {

    private static AIcPublicationEndpoint endpoint(boolean aExecutionEnabled) {
        return new AIcPublicationEndpoint(
            "primary",
            aExecutionEnabled,
            URI.create("https://example.invalid/repository"),
            "maven-repository",
            null,
            0,
            AInPublicationFailurePolicy.FAIL_BUILD_ON_PUBLICATION_FAILURE,
            0,
            1000L,
            30000L,
            true
        );
    }

    /** Verifies that snapshot FORCE_ON changes only output-level enablement. */
    @Test
    public void testSnapshotForceOnDoesNotEnableDisabledEndpoint() {
        AIcPublicationStabilityConfiguration locConfigured =
            new AIcPublicationStabilityConfiguration(false, List.of(endpoint(false)));
        AIcPublicationStabilityConfiguration locResolved = new AIcPublicationPlanResolver().resolve(
            locConfigured,
            AInPublicationStability.SNAPSHOT,
            AInPublicationInvocationOverride.FORCE_ON
        );
        Assert.assertTrue(locResolved.publicationEnabled());
        Assert.assertFalse(locResolved.publicationEndpoints().get(0).executionEnabled());
    }

    /** Verifies that snapshot FORCE_OFF disables publishing without modifying endpoint configuration. */
    @Test
    public void testSnapshotForceOffPreservesEndpointConfiguration() {
        AIcPublicationStabilityConfiguration locConfigured =
            new AIcPublicationStabilityConfiguration(true, List.of(endpoint(true)));
        AIcPublicationStabilityConfiguration locResolved = new AIcPublicationPlanResolver().resolve(
            locConfigured,
            AInPublicationStability.SNAPSHOT,
            AInPublicationInvocationOverride.FORCE_OFF
        );
        Assert.assertFalse(locResolved.publicationEnabled());
        Assert.assertTrue(locResolved.publicationEndpoints().get(0).executionEnabled());
    }

    /** Verifies that release publishing rejects invocation-time publishing overrides. */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testReleaseRejectsInvocationOverride() {
        AIcPublicationStabilityConfiguration locConfigured =
            new AIcPublicationStabilityConfiguration(true, List.of(endpoint(true)));
        new AIcPublicationPlanResolver().resolve(
            locConfigured,
            AInPublicationStability.RELEASE,
            AInPublicationInvocationOverride.FORCE_OFF
        );
    }
}

package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointCatalog;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointDefinition;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceEndpointAction;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceStability;
import java.net.URI;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests publication destination selection against the effective ResourceEndpoint catalog.
 */
public final class AItcPublicationDestinationResolverTest {

    /** Verifies default and explicit publication destination selection. */
    @Test
    public void AIcSelectsMatchingPublicationEndpoints() {
        AIcResourceEndpointDefinition locSnapshot = endpoint(
            "algites-modustro-docs-site-public-snapshot-upload",
            "docs_site",
            AInResourceStability.SNAPSHOT,
            true
        );
        AIcResourceEndpointDefinition locRelease = endpoint(
            "algites-modustro-docs-site-public-release-upload",
            "docs_site",
            AInResourceStability.RELEASE,
            true
        );
        AIcResourceEndpointCatalog locCatalog = new AIcResourceEndpointCatalog(List.of(locSnapshot, locRelease));
        AIcPublicationDestinationResolver locResolver = new AIcPublicationDestinationResolver();

        Assert.assertEquals(
            locResolver.selectUploadDestinations(locCatalog, "docs_site", "public", AInResourceStability.SNAPSHOT, List.of())
                .endpoints(),
            List.of(locSnapshot)
        );
        Assert.assertEquals(
            locResolver.selectUploadDestinations(
                locCatalog,
                "docs_site",
                "public",
                AInResourceStability.RELEASE,
                List.of(locRelease.id())
            ).endpoints(),
            List.of(locRelease)
        );
    }

    /** Verifies that PublicationDestinations cannot escape the requested publication context. */
    @Test(expectedExceptions = AIxModelValidationException.class)
    public void AIcRejectsMismatchedExplicitDestination() {
        AIcResourceEndpointDefinition locRelease = endpoint(
            "algites-modustro-docs-site-public-release-upload",
            "docs_site",
            AInResourceStability.RELEASE,
            true
        );
        AIcPublicationDestinationResolver locResolver = new AIcPublicationDestinationResolver();
        locResolver.selectUploadDestinations(
            new AIcResourceEndpointCatalog(List.of(locRelease)),
            "docs_site",
            "public",
            AInResourceStability.SNAPSHOT,
            List.of(locRelease.id())
        );
    }

    private static AIcResourceEndpointDefinition endpoint(
        String aId,
        String aResourceKind,
        AInResourceStability aStability,
        boolean aEnabled
    ) {
        return new AIcResourceEndpointDefinition(
            "modustro",
            aResourceKind,
            "public",
            AInResourceEndpointAction.UPLOAD,
            aId,
            URI.create("https://example.invalid/" + aId + "/"),
            null,
            aEnabled,
            aStability,
            null
        );
    }
}

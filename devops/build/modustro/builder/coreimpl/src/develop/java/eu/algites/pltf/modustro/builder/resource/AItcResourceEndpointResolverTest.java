package eu.algites.pltf.modustro.builder.resource;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointCatalog;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointDefinition;
import eu.algites.pltf.modustro.builder.model.resource.AIcgdResourceEndpoint_1;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceEndpointAction;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceStability;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceEndpointAction_1;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceEndpointVisibility_1;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceStability_1;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests ResourceEndpoint declaration inheritance and effective-model resolution.
 */
public final class AItcResourceEndpointResolverTest {

    /**
     * Creates the test fixture.
     */
    public AItcResourceEndpointResolverTest() {
    }

    /**
     * Verifies amendment inheritance, defaulting, semantic validation, and effective selection.
     */
    @Test
    public void AIcResolvesInheritedEndpointAmendments() {
        AIcResourceEndpointResolver locResolver = AIcResourceEndpointResolver.builtin();
        AIcgdResourceEndpoint_1 locBase = new AIcgdResourceEndpoint_1(
            "java",
            "native_binary_output",
            AIngResourceEndpointVisibility_1.PUBLIC,
            AIngResourceEndpointAction_1.DOWNLOAD,
            "algites-java-native-build-output-public-snapshot-download",
            "https://example.invalid/maven/",
            "example-download",
            null,
            AIngResourceStability_1.SNAPSHOT,
            null
        );
        AIcgdResourceEndpoint_1 locOverride = new AIcgdResourceEndpoint_1(
            "java",
            "native_binary_output",
            AIngResourceEndpointVisibility_1.PUBLIC,
            AIngResourceEndpointAction_1.DOWNLOAD,
            "algites-java-native-build-output-public-snapshot-download",
            null,
            null,
            false,
            null,
            "cloudsmith"
        );

        AIcResourceEndpointCatalog locCatalog = locResolver.resolve(List.of(locBase, locOverride));
        Assert.assertEquals(locCatalog.all().size(), 1);
        AIcResourceEndpointDefinition locEndpoint = locCatalog.all().get(0);
        Assert.assertEquals(locEndpoint.url().toString(), "https://example.invalid/maven/");
        Assert.assertEquals(locEndpoint.credentialProfile(), "example-download");
        Assert.assertFalse(locEndpoint.enabled());
        Assert.assertEquals(locEndpoint.stability(), AInResourceStability.SNAPSHOT);
        Assert.assertEquals(locEndpoint.resourceEndpointProviderAdapter(), "cloudsmith");
        Assert.assertEquals(
            locCatalog.select(
                "java",
                "native_binary_output",
                "public",
                AInResourceEndpointAction.DOWNLOAD,
                AInResourceStability.SNAPSHOT,
                true
            ).size(),
            0
        );
    }

    /**
     * Verifies that an incomplete effective endpoint is rejected after inheritance.
     */
    @Test
    public void AIcRejectsMissingEffectiveUrl() {
        AIcResourceEndpointResolver locResolver = AIcResourceEndpointResolver.builtin();
        AIcgdResourceEndpoint_1 locDeclaration = new AIcgdResourceEndpoint_1(
            "java",
            "native_binary_output",
            AIngResourceEndpointVisibility_1.PUBLIC,
            AIngResourceEndpointAction_1.UPLOAD,
            "algites-java-native-build-output-public-snapshot-upload",
            null,
            null,
            true,
            AIngResourceStability_1.SNAPSHOT,
            null
        );
        Assert.expectThrows(AIxModelValidationException.class, () -> locResolver.resolve(List.of(locDeclaration)));
    }

    /**
     * Verifies that globally duplicated endpoint ids across different cells are rejected.
     */
    @Test
    public void AIcRejectsEndpointIdReuseAcrossCells() {
        AIcResourceEndpointResolver locResolver = AIcResourceEndpointResolver.builtin();
        String locId = "algites-java-native-build-output-public-snapshot-download";
        AIcgdResourceEndpoint_1 locDownload = new AIcgdResourceEndpoint_1(
            "java",
            "native_binary_output",
            AIngResourceEndpointVisibility_1.PUBLIC,
            AIngResourceEndpointAction_1.DOWNLOAD,
            locId,
            "https://example.invalid/download/",
            null,
            true,
            AIngResourceStability_1.SNAPSHOT,
            null
        );
        AIcgdResourceEndpoint_1 locUpload = new AIcgdResourceEndpoint_1(
            "java",
            "native_binary_output",
            AIngResourceEndpointVisibility_1.PUBLIC,
            AIngResourceEndpointAction_1.UPLOAD,
            locId,
            "https://example.invalid/upload/",
            null,
            true,
            AIngResourceStability_1.SNAPSHOT,
            null
        );
        Assert.expectThrows(
            AIxModelValidationException.class,
            () -> locResolver.resolve(List.of(locDownload, locUpload))
        );
    }
    /**
     * Verifies compatibility with the established native-build-output endpoint ids used by repository defaults.
     */
    @Test
    public void AIcAcceptsLegacyNativeBuildOutputEndpointId() {
        AIcResourceEndpointResolver locResolver = AIcResourceEndpointResolver.builtin();
        AIcgdResourceEndpoint_1 locDeclaration = new AIcgdResourceEndpoint_1(
            "java",
            "native_binary_output",
            AIngResourceEndpointVisibility_1.PUBLIC,
            AIngResourceEndpointAction_1.DOWNLOAD,
            "algites-java-public-release-download",
            "https://repo1.maven.org/maven2/",
            null,
            null,
            AIngResourceStability_1.RELEASE,
            null
        );

        AIcResourceEndpointCatalog locCatalog = locResolver.resolve(List.of(locDeclaration));
        Assert.assertEquals(locCatalog.all().size(), 1);
        Assert.assertTrue(locCatalog.all().get(0).enabled());
    }

}

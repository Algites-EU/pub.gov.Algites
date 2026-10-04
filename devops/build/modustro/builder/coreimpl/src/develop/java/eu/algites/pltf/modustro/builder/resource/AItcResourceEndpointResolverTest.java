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
            "algites-java-native-binary-output-public-snapshot-download",
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
            "algites-java-native-binary-output-public-snapshot-download",
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
            "algites-java-native-binary-output-public-snapshot-upload",
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
        String locId = "algites-java-native-binary-output-public-snapshot-download";
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

    /** Covers the legacy source-upload ID reported by CI without weakening dimensional validation. */
    @Test
    public void AIcMigratesLegacySourceUploadBeforeInheritance() {
        var bridge = AIcResourceEndpointMetadataBridge.builtin();
        var resolver = AIcResourceEndpointResolver.builtin();
        var source = bridge.declaration("java", "native_source_output", "public", "upload",
            "algites-java-public-release-upload", "https://example.invalid/maven/", "source-upload", true, "release", "cloudsmith");
        var binary = bridge.declaration("java", "native_binary_output", "public", "upload",
            "algites-java-public-release-upload", "https://example.invalid/maven/", "binary-upload", true, "release", "cloudsmith");
        var amendment = bridge.declaration("java", "native_source_output", "public", "upload",
            "algites-java-native-source-output-public-release-upload", null, null, false, null, null);
        var catalog = resolver.resolve(resolver.mergeDeclarations(List.of(source, binary), List.of(amendment)));
        Assert.assertEquals(catalog.all().size(), 2);
        var effective = catalog.all().stream().filter(e -> e.resourceKind().equals("native_source_output")).findFirst().orElseThrow();
        Assert.assertEquals(effective.id(), "algites-java-native-source-output-public-release-upload");
        Assert.assertEquals(effective.credentialProfile(), "source-upload");
        Assert.assertEquals(effective.resourceEndpointProviderAdapter(), "cloudsmith");
        Assert.assertFalse(effective.enabled());
        Assert.assertEquals(effective.url().toString(), "https://example.invalid/maven/");
        var oldAmendment = bridge.declaration("java", "native_source_output", "public", "upload",
            "algites-java-public-release-upload", null, null, false, null, null);
        Assert.assertEquals(resolver.mergeDeclarations(List.of(source), List.of(oldAmendment)).size(), 1);
    }

    /** Legacy source IDs still have to agree with visibility and stability dimensions. */
    @Test
    public void AIcRejectsMismatchedLegacySourceUploadDimensions() {
        var bridge = AIcResourceEndpointMetadataBridge.builtin();
        var resolver = AIcResourceEndpointResolver.builtin();
        var wrongLane = bridge.declaration("java", "native_source_output", "public", "upload",
            "algites-java-public-release-upload", "https://example.invalid/", null, true, "snapshot", null);
        Assert.expectThrows(AIxModelValidationException.class, () -> resolver.resolve(List.of(wrongLane)));
        var wrongVisibility = bridge.declaration("java", "native_source_output", "private", "upload",
            "algites-java-public-release-upload", "https://example.invalid/", null, true, "release", null);
        Assert.expectThrows(AIxModelValidationException.class, () -> resolver.resolve(List.of(wrongVisibility)));
    }

    /** Verifies all legacy documentation endpoint dimensions migrate to the current canonical ID. */
    @Test
    public void AIcMigratesLegacyDocsSiteDimensions() {
        var locBridge = AIcResourceEndpointMetadataBridge.builtin();
        var locResolver = AIcResourceEndpointResolver.builtin();
        for (String locVisibility : List.of("public", "private")) {
            for (String locStability : List.of("release", "snapshot")) {
                for (String locAction : List.of("download", "upload", "manage")) {
                    String locSuffix = locVisibility + "-" + locStability + "-" + locAction;
                    var locDeclaration = locBridge.declaration("modustro", "modustro_docs_site", locVisibility,
                        locAction, "algites-modustro-docs-site-" + locSuffix, "https://example.invalid/docs/",
                        "docs-credentials", true, locStability, "github-pages");
                    var locEndpoint = locResolver.resolve(List.of(locDeclaration)).all().get(0);
                    Assert.assertEquals(locEndpoint.id(), "algites-modustro-modustro-docs-site-" + locSuffix);
                    Assert.assertEquals(locEndpoint.credentialProfile(), "docs-credentials");
                    Assert.assertEquals(locEndpoint.resourceEndpointProviderAdapter(), "github-pages");
                }
            }
        }
    }

    /** Verifies old and current documentation IDs amend one inherited endpoint without losing metadata. */
    @Test
    public void AIcMergesLegacyDocsSiteAmendments() {
        var locBridge = AIcResourceEndpointMetadataBridge.builtin();
        var locResolver = AIcResourceEndpointResolver.builtin();
        var locBase = locBridge.declaration("modustro", "modustro_docs_site", "public", "upload",
            "algites-modustro-docs-site-public-snapshot-upload", "https://example.invalid/docs/",
            "docs-credentials", true, "snapshot", "github-pages");
        for (String locId : List.of("algites-modustro-docs-site-public-snapshot-upload",
            "algites-modustro-modustro-docs-site-public-snapshot-upload")) {
            var locAmendment = locBridge.declaration("modustro", "modustro_docs_site", "public", "upload",
                locId, null, null, false, null, null);
            var locEndpoints = locResolver.resolve(locResolver.mergeDeclarations(List.of(locBase), List.of(locAmendment))).all();
            Assert.assertEquals(locEndpoints.size(), 1);
            Assert.assertFalse(locEndpoints.get(0).enabled());
            Assert.assertEquals(locEndpoints.get(0).url().toString(), "https://example.invalid/docs/");
            Assert.assertEquals(locEndpoints.get(0).credentialProfile(), "docs-credentials");
        }
    }

    /** Verifies legacy documentation IDs still reject conflicting visibility, stability and action. */
    @Test
    public void AIcRejectsMismatchedLegacyDocsSiteDimensions() {
        var locBridge = AIcResourceEndpointMetadataBridge.builtin();
        var locResolver = AIcResourceEndpointResolver.builtin();
        for (List<String> locDimensions : List.of(List.of("private", "release", "upload"),
            List.of("public", "snapshot", "upload"), List.of("public", "release", "download"))) {
            var locDeclaration = locBridge.declaration("modustro", "modustro_docs_site", locDimensions.get(0),
                locDimensions.get(2), "algites-modustro-docs-site-public-release-upload",
                "https://example.invalid/docs/", null, true, locDimensions.get(1), null);
            Assert.expectThrows(AIxModelValidationException.class, () -> locResolver.resolve(List.of(locDeclaration)));
        }
    }

}

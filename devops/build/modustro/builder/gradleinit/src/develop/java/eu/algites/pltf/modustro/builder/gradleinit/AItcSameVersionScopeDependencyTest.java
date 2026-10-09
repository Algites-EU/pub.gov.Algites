package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Verifies implicit version requirements against actual repository Version Scope membership. */
public final class AItcSameVersionScopeDependencyTest {
    private static final String REPOSITORY_ID = "pub.test.scope";

    private static Path AIcFixture() throws IOException {
        Path locRoot = Files.createTempDirectory("modustro-dependency-scope-");
        Files.writeString(locRoot.resolve("modustro-source-repository.yml"), """
                SourceRepository:
                  Id: pub.test.scope
                GroupId: eu.algites.test
                Version:
                  ReleaseLineVersion: '1'
                  Revision: 2
                  QualifierKind: snapshot
                """);
        AIcArtifact(locRoot, "api", "");
        AIcArtifact(locRoot, "client", "");
        Path locOtherScope = Files.createDirectories(locRoot.resolve("separate"));
        Files.writeString(locOtherScope.resolve("modustro-artifact-set.yml"), """
                ArtifactSet:
                  Name: Independently versioned subtree
                Version:
                  ReleaseLineVersion: '3'
                  Revision: 4
                  QualifierKind: snapshot
                """);
        AIcArtifact(locRoot, "separate/other", "");
        AIcArtifact(locRoot, "separate/consumer", "");
        return locRoot;
    }

    private static void AIcArtifact(Path aRoot, String aPath, String aExtra) throws IOException {
        Path locDirectory = Files.createDirectories(aRoot.resolve(aPath));
        Files.writeString(locDirectory.resolve("modustro-artifact.yml"), """
                Artifact:
                  Name: Scope test artifact
                  TechnologyKinds: [java]
                """ + aExtra);
    }

    private static String AIcDependency(String aArtifactId, String aVersionRequirement) {
        return """
                Dependencies:
                  - DependencyKind: modustro
                    Items:
                      - GroupId: eu.algites.test
                        ArtifactId: %s
                        Usages: [product_api]
                """.formatted(aArtifactId) + aVersionRequirement;
    }

    private static AIcdModustroArtifactDirectoryMetadata AIcResolved(Path aRoot, String aArtifactPath,
            String aResolutionKind) {
        var locResult = AIcArtifactDirectoryMetadataResolverKt.AIcResolveModustroArtifactDirectoryMetadata(
                aRoot.toFile(), aArtifactPath, aResolutionKind, null, null);
        return locResult.getArtifactDirectories().stream()
                .filter(aArtifact -> aArtifact.getPath().equals(aArtifactPath))
                .findFirst().orElseThrow();
    }

    @Test
    public void AIcSameScopeInheritsOwnerVersion() throws IOException {
        Path locRoot = AIcFixture();
        AIcArtifact(locRoot, "client", AIcDependency(REPOSITORY_ID + "_api", ""));
        var locClient = AIcResolved(locRoot, "client", "current-only");
        Assert.assertEquals(locClient.getDependencies().get(0).getVersionRequirement().getExact(), "1.2-SNAPSHOT");
        Assert.assertEquals(locClient.getDependencies().get(0).getVersionRequirement().getSpecifiedProperties(),
                java.util.Set.of("Exact"));
    }

    @Test
    public void AIcNestedVersionScopeInheritsNestedVersion() throws IOException {
        Path locRoot = AIcFixture();
        AIcArtifact(locRoot, "separate/consumer", AIcDependency(REPOSITORY_ID + "_separate.other", ""));
        var locClient = AIcResolved(locRoot, "separate/consumer", "current-with-subdirs");
        Assert.assertEquals(locClient.getVersionScopePath(), "separate");
        Assert.assertEquals(locClient.getDependencies().get(0).getVersionRequirement().getExact(), "3.4-SNAPSHOT");
    }

    @Test
    public void AIcExplicitVersionIsNeverReplaced() throws IOException {
        Path locRoot = AIcFixture();
        AIcArtifact(locRoot, "client", AIcDependency(REPOSITORY_ID + "_api", """
                        VersionRequirement:
                          Exact: 7.8-SNAPSHOT
                """));
        var locClient = AIcResolved(locRoot, "client", "current-only");
        Assert.assertEquals(locClient.getDependencies().get(0).getVersionRequirement().getExact(), "7.8-SNAPSHOT");
    }

    @Test
    public void AIcCrossScopeDependencyRequiresExplicitVersion() throws IOException {
        Path locRoot = AIcFixture();
        AIcArtifact(locRoot, "client", AIcDependency(REPOSITORY_ID + "_separate.other", ""));
        IllegalStateException locError = Assert.expectThrows(IllegalStateException.class,
                () -> AIcResolved(locRoot, "client", "current-only"));
        Assert.assertTrue(locError.getMessage().contains("another Version Scope"));
    }

    @Test
    public void AIcCrossScopeExplicitVersionIsPreserved() throws IOException {
        Path locRoot = AIcFixture();
        AIcArtifact(locRoot, "client", AIcDependency(REPOSITORY_ID + "_separate.other", """
                        VersionRequirement:
                          Exact: 3.4-SNAPSHOT
                """));
        Assert.assertEquals(AIcResolved(locRoot, "client", "current-only")
                .getDependencies().get(0).getVersionRequirement().getExact(), "3.4-SNAPSHOT");
    }

    @Test
    public void AIcExternalUnversionedDependencyIsNotGuessed() throws IOException {
        Path locRoot = AIcFixture();
        AIcArtifact(locRoot, "client", AIcDependency("pub.external_other", ""));
        var locClient = AIcResolved(locRoot, "client", "current-only");
        Assert.assertNull(locClient.getDependencies().get(0).getVersionRequirement());
    }

    @Test
    public void AIcAllDirectoryResolutionAlsoAppliesImplicitVersions() throws IOException {
        Path locRoot = AIcFixture();
        AIcArtifact(locRoot, "client", AIcDependency(REPOSITORY_ID + "_api", ""));
        var locResult = AIcArtifactDirectoryMetadataResolverKt.AIcResolveModustroArtifactDirectoryMetadata(
                locRoot.toFile(), null, "current-with-subdirs", null, null);
        var locClient = locResult.getArtifactDirectories().stream().filter(a -> a.getPath().equals("client"))
                .findFirst().orElseThrow();
        Assert.assertEquals(locClient.getDependencies().get(0).getVersionRequirement().getExact(), "1.2-SNAPSHOT");
    }

    @Test
    public void AIcExplicitNullIsNotInterpretedAsOmitted() throws IOException {
        Path locRoot = AIcFixture();
        AIcArtifact(locRoot, "client", AIcDependency(REPOSITORY_ID + "_api", """
                        VersionRequirement:
                          Exact: null
                """));
        var locRequirement = AIcResolved(locRoot, "client", "current-only")
                .getDependencies().get(0).getVersionRequirement();
        Assert.assertNull(locRequirement.getExact());
        Assert.assertTrue(locRequirement.getSpecifiedProperties().contains("Exact"));
    }

    @Test
    public void AIcVariantMustMatchTheLocalTarget() throws IOException {
        Path locRoot = AIcFixture();
        Files.writeString(locRoot.resolve("api/modustro-artifact.yml"), """
                Artifact:
                  Name: Variant API
                  TechnologyKinds: [java]
                  VariantId: extended
                """);
        AIcArtifact(locRoot, "client", AIcDependency(REPOSITORY_ID + "_api", ""));
        Assert.assertNull(AIcResolved(locRoot, "client", "current-only")
                .getDependencies().get(0).getVersionRequirement());
        AIcArtifact(locRoot, "client", """
                Dependencies:
                  - DependencyKind: modustro
                    Items:
                      - GroupId: eu.algites.test
                        ArtifactId: pub.test.scope_api
                        VariantId: extended
                """);
        Assert.assertEquals(AIcResolved(locRoot, "client", "current-only")
                .getDependencies().get(0).getVersionRequirement().getExact(), "1.2-SNAPSHOT");
    }

    @Test
    public void AIcGroupMustMatchTheLocalTarget() throws IOException {
        Path locRoot = AIcFixture();
        AIcArtifact(locRoot, "client", """
                Dependencies:
                  - DependencyKind: modustro
                    Items:
                      - GroupId: eu.algites.another
                        ArtifactId: pub.test.scope_api
                """);
        Assert.assertNull(AIcResolved(locRoot, "client", "current-only")
                .getDependencies().get(0).getVersionRequirement());
    }

    @Test
    public void AIcInheritedDependencyGetsEffectiveOwnerScopeVersion() throws IOException {
        Path locRoot = AIcFixture();
        Path locGroup = Files.createDirectories(locRoot.resolve("nested"));
        Files.writeString(locGroup.resolve("modustro-artifact-set.yml"), """
                ArtifactSet:
                  Name: Group inheriting dependencies
                """ + AIcDependency(REPOSITORY_ID + "_api", ""));
        AIcArtifact(locRoot, "nested/consumer", "");
        var locClient = AIcResolved(locRoot, "nested/consumer", "current-only");
        Assert.assertEquals(locClient.getVersionScopePath(), ".");
        Assert.assertEquals(locClient.getDependencies().get(0).getVersionRequirement().getExact(), "1.2-SNAPSHOT");
    }
}

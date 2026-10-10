package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.execution.AIngBuildExecutionFailurePolicy_1;
import eu.algites.pltf.modustro.builder.model.publication.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Regression tests for one physical Maven package from independently configured outputs. */
public class AItcMavenPublicationBatchPlannerTest {
    private static AIcPublicationEndpoint AIcEndpoint(String aId, String aRepository, int aOrder) {
        return new AIcPublicationEndpoint(aId, true, URI.create(aRepository), "maven-repository", "cred",
                aOrder, AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE, 0, 1000L, null, true);
    }

    private static AIcPublicationJob AIcJob(String aId, AIcPublicationEndpoint aEndpoint,
            AInPublicationOutputKind aKind, Path aDirectory, String... aNames) throws Exception {
        List<AIcPublicationPayloadFile> locFiles = new ArrayList<>();
        for (String locName : aNames) {
            Path locFile = aDirectory.resolve(locName);
            Files.writeString(locFile, "content:" + locName);
            locFiles.add(new AIcPublicationPayloadFile(locFile, locName));
        }
        AIcPublicationPayload locPayload = new AIcPublicationPayload(aKind, AInPublicationStability.SNAPSHOT,
                "group:artifact", "1.0-20261010.123456-1", locFiles,
                Map.of("groupId", "group", "artifactId", "artifact", "version", "1.0-SNAPSHOT",
                        "technologyKind", "java", "snapshotTimestamp", "20261010.123456", "snapshotBuildNumber", "1"));
        return new AIcPublicationJob(aId, aEndpoint, locPayload, Map.of(), List.of());
    }

    @Test
    public void testSameDestinationMergesAndPreparesRecordWithoutPublicationResults() throws Exception {
        Path locRoot = Files.createTempDirectory("maven-bundle-test-");
        String locVersion = "artifact-1.0-20261010.123456-1";
        AIcPublicationJob locBinary = AIcJob("bin", AIcEndpoint("bin", "https://example.org/maven", 0),
                AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, locRoot,
                locVersion + ".jar", locVersion + ".pom", locVersion + ".module");
        AIcPublicationJob locSources = AIcJob("sources", AIcEndpoint("sources", "https://example.org/maven/", 0),
                AInPublicationOutputKind.NATIVE_PRODUCT_SOURCES, locRoot, locVersion + "-sources.jar");
        AIcMavenPublicationBatchPlanner.AIcdBatch locBatch =
                new AIcMavenPublicationBatchPlanner().plan(List.of(locBinary, locSources));
        Assert.assertEquals(locBatch.physicalJobs().size(), 1);
        Assert.assertEquals(locBatch.physicalIdByLogicalId().get("bin"), locBatch.physicalIdByLogicalId().get("sources"));
        List<AIcPublicationPayloadFile> locFiles = locBatch.physicalJobs().get(0).payload().files();
        Assert.assertEquals(locFiles.size(), 5);
        AIcPublicationPayloadFile locBuildRecord = locFiles.stream()
                .filter(aFile -> aFile.logicalName().endsWith(".modustro-build-record.yml"))
                .findFirst().orElseThrow();
        String locText = Files.readString(locBuildRecord.path());
        Assert.assertFalse(locText.contains("ExecutionLineage"));
        Assert.assertTrue(locText.contains("Sha256"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*ExecutionOrder.*")
    public void testDifferentExecutionOrderCannotPublishTheSameMavenPackage() throws Exception {
        Path locRoot = Files.createTempDirectory("maven-order-test-");
        String locVersion = "artifact-1.0-20261010.123456-1";
        new AIcMavenPublicationBatchPlanner().plan(List.of(
                AIcJob("binary", AIcEndpoint("binary", "https://example.org/maven/", 0),
                        AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, locRoot,
                        locVersion + ".jar", locVersion + ".pom", locVersion + ".module"),
                AIcJob("sources", AIcEndpoint("sources", "https://example.org/maven/", 10),
                        AInPublicationOutputKind.NATIVE_PRODUCT_SOURCES, locRoot, locVersion + "-sources.jar")));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*must include the main JAR.*")
    public void testSourcesOnlyMavenSnapshotDoesNotCreateInvalidPackage() throws Exception {
        Path locRoot = Files.createTempDirectory("maven-sources-only-");
        new AIcMavenPublicationBatchPlanner().plan(List.of(
                AIcJob("sources", AIcEndpoint("sources", "https://example.org/other-repo", 0),
                        AInPublicationOutputKind.NATIVE_PRODUCT_SOURCES, locRoot,
                        "artifact-1.0-20261010.123456-1-sources.jar")));
    }
    @Test
    public void testDifferentRepositoriesRemainIndependentAndRetainTheirOrder() throws Exception {
        Path locRoot = Files.createTempDirectory("maven-two-repositories-");
        String locVersion = "artifact-1.0-20261010.123456-1";
        AIcMavenPublicationBatchPlanner.AIcdBatch locBatch = new AIcMavenPublicationBatchPlanner().plan(List.of(
                AIcJob("first", AIcEndpoint("first", "https://example.org/a/", -10),
                        AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, locRoot,
                        locVersion + ".jar", locVersion + ".pom", locVersion + ".module"),
                AIcJob("second", AIcEndpoint("second", "https://example.org/b/", 20),
                        AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, locRoot,
                        locVersion + ".jar", locVersion + ".pom", locVersion + ".module")));
        Assert.assertEquals(locBatch.physicalJobs().size(), 2);
        Assert.assertEquals(locBatch.physicalJobs().get(0).endpoint().executionOrder(), -10);
        Assert.assertEquals(locBatch.physicalJobs().get(1).endpoint().executionOrder(), 20);
    }

}

package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Guards the publication preflight against being scheduled after compilation or upload. */
public final class AItcModustroPublicationPreflightWiringTest {
    private static Path AIcRepository() throws IOException {
        Path locRepository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locRepository != null && !Files.isRegularFile(locRepository.resolve("modustro-source-repository.yml"))) {
            locRepository = locRepository.getParent();
        }
        if (locRepository == null) throw new IOException("Cannot locate governance repository sources.");
        return locRepository;
    }

    /** The preflight must verify the actual serialized task inputs, not only inherited descriptors. */
    @Test
    public void AIcValidatesFinalPublishingTaskPlans() throws IOException {
        String locSource = Files.readString(AIcRepository().resolve(
            "gradle/tool/repository/modustro-root-build.gradle.kts"));
        Assert.assertTrue(locSource.contains("locProject.tasks.withType(AIcModustroPublishFilesTask::class.java)"));
        Assert.assertTrue(locSource.contains("locTask.publicationPlanJson.get()"));
        Assert.assertTrue(locSource.contains("locTask.credentialProfilesJson.get()"));
        Assert.assertTrue(locSource.contains("locPlan[\"publicationEnabled\"] != true"));
        Assert.assertTrue(locSource.contains("references undefined or invalid credential profile"));
        Assert.assertTrue(locSource.contains("locPublicationCredentialPreflightProblems + locActualPublicationProblems"));
    }

    /** Publishing validates in resolve, before prepare/compile/package; direct publishing uses a gate. */
    @Test
    public void AIcValidatesBeforePublishingWork() throws IOException {
        Path locRepository = AIcRepository();
        String locPhases = Files.readString(locRepository.resolve(
            "gradle/tool/repository/modustro-build-phase-tasks.gradle.kts"));
        String locController = Files.readString(locRepository.resolve(
            "gradle/tool/repository/modustro-phase-controller.sh"));
        String locRoot = Files.readString(locRepository.resolve(
            "gradle/tool/repository/modustro-root-build.gradle.kts"));
        Assert.assertTrue(locController.contains("export MODUSTRO_PUBLISHING_INTENT=true"));
        Assert.assertTrue(locPhases.contains("setOf(\"modustroDependencyPreflight\", \"validateModustroPublicationProfiles\")"));
        Assert.assertTrue(locPhases.contains("locModustroPackagePhase.configure"));
        Assert.assertTrue(locPhases.contains("dependsOn(rootProject.tasks.named(\"validateModustroPublicationProfiles\"))"));
        Assert.assertTrue(locRoot.contains("dependsOn(modustroBuild, \"validateModustroPublicationProfiles\")"));
    }
}

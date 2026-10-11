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
        Assert.assertTrue(locSource.contains("allTasks.filterIsInstance<AIcModustroPublishFilesTask>()"),
                "Publication preflight must only inspect publishers selected in the effective Gradle task graph.");
        Assert.assertFalse(locSource.contains("locProject.tasks.withType(AIcModustroPublishFilesTask::class.java)"),
                "Publication preflight must not scan every configured publication task.");
        Assert.assertTrue(locSource.contains("locTask.publicationPlanJson.get()"));
        Assert.assertTrue(locSource.contains("locTask.credentialProfilesJson.get()"));
        Assert.assertTrue(locSource.contains("locPlan[\"publicationEnabled\"] != true"));
        Assert.assertTrue(locSource.contains("references undefined or invalid credential profile"));
        Assert.assertTrue(locSource.contains("validationProblems.set(locActualPublicationProblems.distinct().sorted())"));
    }

    /** A package-only operation must not require configured publishing endpoints or secrets. */
    @Test
    public void AIcPackagingDoesNotRequirePublicationPreflight() throws IOException {
        String locPhases = Files.readString(AIcRepository().resolve(
            "gradle/tool/repository/modustro-build-phase-tasks.gradle.kts"));
        Assert.assertTrue(locPhases.contains("setOf(\"assemble\", \"buildPython\")"),
                "Packaging must remain a separate phase from publishing.");
        Assert.assertFalse(locPhases.contains("locModustroPackagePhase.configure"),
                "Publication preflight must never be an unconditional package dependency.");
        Assert.assertTrue(locPhases.contains("locTask.name.startsWith(\"publishModustro\")"),
                "The publication-only preflight must cover individual publishing tasks.");
        Assert.assertTrue(locPhases.contains("dependsOn(locModustroPublicationPreflight)"),
                "Publishing must retain publication-preflight dependencies.");
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
        Assert.assertTrue(locPhases.contains("setOf(\"modustroDependencyPreflight\")"));
        Assert.assertTrue(locPhases.contains("val locModustroPublicationPreflight = rootProject.tasks.named(\"validateModustroPublicationProfiles\")"));
        Assert.assertTrue(locPhases.contains("dependsOn(locModustroPublicationPreflight)"),
                "Only publishing tasks must require the publication preflight on direct invocation.");
        Assert.assertFalse(locPhases.contains("locModustroPackagePhase.configure"),
                "Packaging must not depend on publication configuration or secrets.");
        Assert.assertTrue(locRoot.contains("dependsOn(modustroBuild, \"validateModustroPublicationProfiles\")"));
        Assert.assertTrue(locRoot.contains("gradle.taskGraph.whenReady"));
        Assert.assertTrue(locRoot.contains("allTasks.filterIsInstance<AIcModustroPublishFilesTask>()"));
    }
}

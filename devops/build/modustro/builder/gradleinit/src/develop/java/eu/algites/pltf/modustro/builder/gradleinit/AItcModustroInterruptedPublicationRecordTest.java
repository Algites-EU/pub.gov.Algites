package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Prevents regressions in early, locally persisted publication-failure checkpoints. */
public final class AItcModustroInterruptedPublicationRecordTest {
    private static Path AIcRepositoryRoot() throws IOException {
        Path locPath = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locPath != null && !Files.isRegularFile(locPath.resolve("modustro-source-repository.yml"))) {
            locPath = locPath.getParent();
        }
        if (locPath == null) throw new IOException("No Modustro repository root found.");
        return locPath;
    }

    @Test
    public void AIcRequiredFailureWritesPartialScopeBeforeThrowing() throws IOException {
        String locSource = Files.readString(AIcRepositoryRoot().resolve(
            "devops/build/modustro/builder/gradleinit/src/product/kotlin/eu/algites/pltf/modustro/builder/gradleinit/AIcModustroPublicationService.kt"));
        Assert.assertTrue(locSource.contains("AIcWriteInterruptedVersionScopeRecord(aOutputId)"));
        Assert.assertTrue(locSource.contains("InterruptedRequiredPublication:"));
        Assert.assertTrue(locSource.contains("NotYetTerminal:"));
        Assert.assertTrue(locSource.contains("AInVersionScopePublicationAttemptState.FAILED"));
        Assert.assertTrue(locSource.contains("locFuture?.isDone == true"));
    }

    @Test
    public void AIcRootVersionScopeRecordIsNotHidden() throws IOException {
        String locSource = Files.readString(AIcRepositoryRoot().resolve(
            "devops/build/modustro/builder/gradleinit/src/product/kotlin/eu/algites/pltf/modustro/builder/gradleinit/AIcModustroPublicationService.kt"));
        Assert.assertTrue(locSource.contains("val locSafeScope = if (aResult.versionScopeId() == \".\") \"root\" else aResult.versionScopeId()"));
        Assert.assertTrue(locSource.contains("val locSafe = (locSafeScope +"));
    }

    @Test
    public void AIcFailureCheckpointCannotTriggerRemotePublication() throws IOException {
        String locSource = Files.readString(AIcRepositoryRoot().resolve(
            "devops/build/modustro/builder/gradleinit/src/product/kotlin/eu/algites/pltf/modustro/builder/gradleinit/AIcModustroPublicationService.kt"));
        Assert.assertTrue(locSource.contains("aPublishSystemRecord: Boolean = true"));
        Assert.assertTrue(locSource.contains("if (!aPublishSystemRecord) return"));
        Assert.assertTrue(locSource.contains("locPendingIds.sorted().map"));
    }
}

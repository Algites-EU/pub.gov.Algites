package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Regression checks for publication preflight, Kotlin source archives and fixtures. */
public final class AItcModustroPhaseZeroHardeningTest {
    private static Path AIcRepositoryRoot() throws IOException {
        Path locPath = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locPath != null && !Files.isRegularFile(locPath.resolve("modustro-source-repository.yml"))) {
            locPath = locPath.getParent();
        }
        if (locPath == null) throw new IOException("No Modustro repository root found.");
        return locPath;
    }

    @Test
    public void AIcSourceArchivesHaveSingleEffectiveProducer() throws IOException {
        String locRoot = Files.readString(AIcRepositoryRoot().resolve(
            "gradle/tool/repository/modustro-root-build.gradle.kts"));
        Assert.assertTrue(locRoot.contains("duplicate sourcesJar disabled"));
        Assert.assertTrue(locRoot.contains("tasks.named<Jar>(\"sourcesJar\").configure { enabled = false }"));
        Assert.assertFalse(locRoot.contains("Ambiguous source archive producers in"));
    }

    @Test
    public void AIcNativePublicationFixturesCarryJarPomAndModule() throws IOException {
        String locTest = Files.readString(AIcRepositoryRoot().resolve(
            "devops/build/modustro/builder/gradleinit/src/develop/java/eu/algites/pltf/modustro/builder/gradleinit/AItcModustroGradleInitTest.java"));
        Assert.assertTrue(locTest.contains("fixture.jar"));
        Assert.assertTrue(locTest.contains("fixture.pom"));
        Assert.assertTrue(locTest.contains("fixture.module"));
        Assert.assertTrue(locTest.contains("fixture-sources.jar"));
    }

    @Test
    public void AIcPhasePreflightProtectsIncludedBuilds() throws IOException {
        Path locRoot = AIcRepositoryRoot();
        String locPhases = Files.readString(locRoot.resolve("gradle/tool/repository/modustro-build-phase-tasks.gradle.kts"));
        String locController = Files.readString(locRoot.resolve("gradle/tool/repository/modustro-phase-controller.sh"));
        String locMain = Files.readString(locRoot.resolve("gradle/tool/repository/modustro-root-build.gradle.kts"));
        Assert.assertTrue(locPhases.contains("gradle.includedBuilds.sortedBy { it.name }"));
        Assert.assertTrue(locPhases.contains("MODUSTRO_OPERATION_INTENT"));
        Assert.assertTrue(locController.contains("export MODUSTRO_OPERATION_INTENT=\"$through\""));
        Assert.assertTrue(locMain.contains("requiredProfilesJson"));
        Assert.assertTrue(locMain.contains("locSecrets = AIcObject"));
    }
}

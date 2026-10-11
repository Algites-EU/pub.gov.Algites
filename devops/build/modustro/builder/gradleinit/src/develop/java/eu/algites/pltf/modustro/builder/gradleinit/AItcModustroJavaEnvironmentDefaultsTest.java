package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Guards the shared Java baseline for Modustro descriptors and Gradle variants. */
public final class AItcModustroJavaEnvironmentDefaultsTest {
    private static Path AIcRepository() throws IOException {
        Path locRepository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locRepository != null && !Files.isRegularFile(locRepository.resolve("modustro-source-repository.yml"))) {
            locRepository = locRepository.getParent();
        }
        if (locRepository == null) throw new IOException("Cannot locate governance repository sources.");
        return locRepository;
    }

    /** Java target must not silently follow the local Gradle daemon JDK. */
    @Test
    public void AIcHasSharedJava17Baseline() throws IOException {
        Path locRepository = AIcRepository();
        String locDefaults = Files.readString(locRepository.resolve("repository/defaults/algites-repository-defaults-public.yml"));
        String locRepositoryDescriptor = Files.readString(locRepository.resolve("modustro-source-repository.yml"));
        String locDescriptor = Files.readString(locRepository.resolve(
                "devops/build/modustro/builder/gradleinit/modustro-artifact.yml"));
        String locStandalone = Files.readString(locRepository.resolve(
                "devops/build/modustro/builder/gradleinit/build.gradle.kts"));
        String locAdapter = Files.readString(locRepository.resolve(
                "gradle/tool/repository/modustro-root-build.gradle.kts"));
        for (String locConfig : new String[] {locDefaults, locRepositoryDescriptor, locDescriptor}) {
            Assert.assertTrue(locConfig.contains("EnvironmentRequirements:\n  Java:\n    Minimum: \">=17\"\n    Prefer: \"17\""),
                    "A Java 17 environment requirement must be present.");
        }
        Assert.assertTrue(locStandalone.contains("jvmToolchain(17)"),
                "The standalone bootstrap must use Java 17.");
        Assert.assertTrue(locAdapter.contains("JavaLanguageVersion.of(locMajor)"),
                "The common Java adapter must apply resolved environment requirements to Gradle toolchains.");
    }
}

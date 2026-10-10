package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Guards the direct runtime dependency of the Settings plugin against omissions in either build mode. */
public final class AItcModustroBootstrapClasspathDependencyTest {
    private static Path AIcRepository() throws IOException {
        Path locRepository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locRepository != null && !Files.isRegularFile(locRepository.resolve("modustro-source-repository.yml"))) {
            locRepository = locRepository.getParent();
        }
        if (locRepository == null) throw new IOException("Cannot locate governance repository sources.");
        return locRepository;
    }

    /** Both the Modustro descriptor and the standalone bootstrap must export the referenced API type. */
    @Test
    public void AIcDeclaresDirectCoreInterfacesDependency() throws IOException {
        Path locRepository = AIcRepository();
        Path locGradleinit = locRepository.resolve("devops/build/modustro/builder/gradleinit");
        String locDescriptor = Files.readString(locGradleinit.resolve("modustro-artifact.yml"));
        String locStandalone = Files.readString(locGradleinit.resolve("build.gradle.kts"));
        String locRuntime = Files.readString(locGradleinit.resolve(
                "src/product/kotlin/eu/algites/pltf/modustro/builder/gradleinit/AIcModustroGradleRuntime.kt"));
        String locGroup = "eu.algites.pltf.modustro.builder";
        String locArtifact = "pub.gov.Algites_devops.build.modustro.builder.coreintf";
        String locVersion = "1.0-SNAPSHOT";
        String locCoordinates = locGroup + ":" + locArtifact + ":" + locVersion;

        Assert.assertTrue(locRuntime.contains("model.subscription.AIcInputSubscription::class.java"),
                "The test models the Settings runtime's direct reference to the core interface.");
        Pattern locCanonicalDependency = Pattern.compile(
                "(?m)^\\s*- GroupId: " + Pattern.quote(locGroup) +
                "\\s*\\R\\s*ArtifactId: " + Pattern.quote(locArtifact) +
                "\\s*\\R\\s*Usages: \\[product_api\\]" +
                "\\s*\\R\\s*VersionRequirement:\\s*\\R\\s*Exact: \"" + locVersion + "\"");
        Assert.assertTrue(locCanonicalDependency.matcher(locDescriptor).find(),
                "The canonical Modustro descriptor must declare coreintf as product_api.");
        Assert.assertTrue(locStandalone.contains("api(\"" + locCoordinates + "\")"),
                "The standalone gradleinit build must publish coreintf as an API dependency.");
        Assert.assertTrue(locStandalone.contains("from(components[\"java\"])"),
                "The standalone Maven publication must expose the Java component dependency metadata.");
    }
}

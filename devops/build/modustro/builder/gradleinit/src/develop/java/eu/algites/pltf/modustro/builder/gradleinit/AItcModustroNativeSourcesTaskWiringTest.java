package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Guards the native source publication conventions against duplicate Java/Kotlin archive producers. */
public final class AItcModustroNativeSourcesTaskWiringTest {
    private static Path AIcRepository() throws IOException {
        Path locRepository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locRepository != null && !Files.isRegularFile(locRepository.resolve("modustro-source-repository.yml"))) {
            locRepository = locRepository.getParent();
        }
        if (locRepository == null) throw new IOException("Cannot locate governance repository sources.");
        return locRepository;
    }

    /** Resolves source archive producers lazily and rejects duplicate output writers. */
    @Test
    public void AIcUsesSingleProducerForNativeSources() throws IOException {
        Path locRepository = AIcRepository();
        String locSource = Files.readString(locRepository.resolve("gradle/tool/repository/modustro-root-build.gradle.kts"));
        String locGradleInit = Files.readString(locRepository.resolve("devops/build/modustro/builder/gradleinit/build.gradle.kts"));
        Assert.assertTrue(locGradleInit.contains("`kotlin-dsl`"), "Kotlin DSL must remain enabled.");
        Assert.assertTrue(locSource.contains("plugins.hasPlugin(\"org.jetbrains.kotlin.jvm\")"),
                "The Kotlin source convention must detect the Kotlin JVM plugin.");
        Assert.assertTrue(locSource.contains("locJavaSourcesArchiveTaskName() == \"sourcesJar\")"),
                "The Java sources plugin must not create a duplicate producer in Kotlin projects.");
        Assert.assertTrue(locSource.contains("tasks.matching { locTask -> locTask.name in setOf(\"sourcesJar\", \"kotlinSourcesJar\") }"),
                "The build must depend on actual producers rather than naming an absent sourcesJar task.");
        Assert.assertTrue(locSource.contains("tasks.findByName(\"sourcesJar\") == null && tasks.findByName(\"kotlinSourcesJar\") == null"),
                "Java fallback source archive is only registered when neither producer exists.");
        Assert.assertTrue(locSource.contains("tasks.named<Jar>(\"sourcesJar\").configure { enabled = false }"),
                "When Kotlin owns the archive, the Java sourcesJar task must be disabled.");
        Assert.assertTrue(locSource.contains("tasks.named<Jar>(\"kotlinSourcesJar\").configure { enabled = false }"),
                "When Java owns the archive, the Kotlin sourcesJar task must be disabled.");
        Assert.assertFalse(locSource.contains("Ambiguous source archive producers"),
                "The build should resolve duplicate source producers, not fail configuration.");
        Assert.assertTrue(locSource.contains("layout.buildDirectory.file(\"libs/$locAlgitesEffectiveArtifactId-$locAlgitesProjectVersion-sources.jar\")"),
                "The source payload must reference its canonical output file.");
        Assert.assertTrue(locSource.contains("dependsOn(tasks.matching { locTask -> locTask.name in setOf(\"sourcesJar\", \"kotlinSourcesJar\") })"),
                "Publishing must wait for the actual Java or Kotlin archive producer.");
    }
}

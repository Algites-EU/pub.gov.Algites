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

    /** Kotlin's source archive must replace, not accompany, Java's identically named output. */
    @Test
    public void AIcUsesSingleProducerForNativeSources() throws IOException {
        Path locRepository = AIcRepository();
        String locSource = Files.readString(locRepository.resolve("gradle/tool/repository/modustro-root-build.gradle.kts"));
        String locGradleInit = Files.readString(locRepository.resolve("devops/build/modustro/builder/gradleinit/build.gradle.kts"));
        Assert.assertTrue(locGradleInit.contains("`kotlin-dsl`"), "The Kotlin fixture must exercise Kotlin JVM conventions.");
        Assert.assertTrue(locSource.contains("plugins.hasPlugin(\"org.jetbrains.kotlin.jvm\")"),
            "The source archive must recognize the Kotlin JVM plugin.");
        Assert.assertTrue(locSource.contains("file(\"src/product/kotlin\").isDirectory"),
            "Kotlin source roots must be detected even during early Java plugin application.");
        Assert.assertTrue(locSource.contains("\"kotlinSourcesJar\" else \"sourcesJar\""),
            "The build must select the Kotlin or Java source archive, not both.");
        Assert.assertTrue(locSource.contains("locJavaSourcesArchiveTaskName() == \"sourcesJar\") {"),
            "Java withSourcesJar() must not be called for Kotlin modules.");
        Assert.assertTrue(locSource.contains("Triple(AInBuildOutputProductionKind.JAVA_SOURCES_JAR, " +
            "\"publishModustroJavaNativeSources\", locSourcesTaskName)"),
            "Native source publishing must use the selected archive task.");
        Assert.assertTrue(locSource.contains("dependsOn(modustroPublicationBuildGate, tasks.named(locArchiveTaskName))"),
            "Publishing tasks must depend explicitly on their archive producer.");
        Assert.assertTrue(locSource.contains("val locArchive = tasks.named<Jar>(locArchiveTaskName).flatMap { it.archiveFile }"),
            "The archive file must be obtained through the producing task provider.");
        Assert.assertTrue(locSource.contains("modustroBuild.configure { dependsOn(locJavaSourcesArchiveTaskName()) }"),
            "The aggregate build must use the same archive producer as publishing.");
        Assert.assertTrue(locSource.contains("\"sourcesJar\", \"kotlinSourcesJar\" -> \"native_product_sources\""),
            "Both supported task names must map to the native source output kind.");
    }
}

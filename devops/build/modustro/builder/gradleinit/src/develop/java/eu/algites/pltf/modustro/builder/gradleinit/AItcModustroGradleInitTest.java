package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.BuildResult;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Integration checks for the Settings classloader boundary and compiled discovery. */
public final class AItcModustroGradleInitTest {
    private static String AIcBootstrap() throws IOException {
        Properties locMetadata = new Properties();
        try (var locInput = AItcModustroGradleInitTest.class.getClassLoader()
                .getResourceAsStream("plugin-under-test-metadata.properties")) {
            if (locInput == null) throw new IOException("Plugin test classpath metadata is missing.");
            locMetadata.load(locInput);
        }
        String[] locFiles = locMetadata.getProperty("implementation-classpath")
                .split(java.util.regex.Pattern.quote(java.io.File.pathSeparator));
        String locClasspath = java.util.Arrays.stream(locFiles)
                .map(aFile -> "\"" + aFile.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "buildscript { dependencies { classpath(files(" + locClasspath + ")) } }\n"
                + "apply(plugin = \"eu.algites.pltf.modustro.builder.settings\")\n";
    }

    private static Path AIcFixture() throws IOException {
        Path locDirectory = Files.createTempDirectory("modustro-gradleinit-test-");
        Files.writeString(locDirectory.resolve("modustro-source-repository.yml"), """
                SourceRepository:
                  Id: pub.test.gradleinit
                GroupId: eu.algites.test
                Version:
                  ReleaseLineVersion: "1"
                  Revision: 0
                  QualifierKind: snapshot
                ResourceEndpoints:
                  java:
                    native_binary_output:
                      public:
                        download:
                          - Id: algites-test-java-native-binary-output-public-release-download
                            Url: https://repo.example.test/releases/
                            Stability: release
                            Enabled: false
                """);
        Path locChild = Files.createDirectories(locDirectory.resolve("child"));
        Files.writeString(locChild.resolve("modustro-artifact.yml"), """
                Artifact:
                  Name: Gradle init child
                  TechnologyKinds: [java]
                ResourceEndpoints:
                  java:
                    native_binary_output:
                      public:
                        download:
                          - Id: algites-test-java-native-binary-output-public-release-download
                            Enabled: true
                """);
        Files.writeString(locChild.resolve("build.gradle.kts"), "");
        return locDirectory;
    }

    @Test
    public void AIcSettingsProjectAndAppliedProjectShareBuilderClass() throws IOException {
        Path locFixture = AIcFixture();
        Files.writeString(locFixture.resolve("settings.gradle.kts"), AIcBootstrap());
        Files.writeString(locFixture.resolve("applied.gradle.kts"), """
                import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroGradleRuntime
                import eu.algites.pltf.modustro.builder.model.resource.AIcgdResourceEndpoint_1
                val locRuntime = gradle.extra["modustroGradleRuntime"] as AIcModustroGradleRuntime
                check(locRuntime.endpointDeclarationClass === AIcgdResourceEndpoint_1::class.java)
                """);
        Files.writeString(locFixture.resolve("build.gradle.kts"), """
                import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroGradleRuntime
                import eu.algites.pltf.modustro.builder.model.resource.AIcgdResourceEndpoint_1
                val locRuntime = gradle.extra["modustroGradleRuntime"] as AIcModustroGradleRuntime
                check(locRuntime.endpointDeclarationClass === AIcgdResourceEndpoint_1::class.java)
                apply(from = "applied.gradle.kts")
                apply(plugin = "eu.algites.pltf.modustro.builder.repository")
                check(project(":child").projectDir == file("child"))
                @Suppress("UNCHECKED_CAST")
                val locMetadata = extra["modustroResolvedArtifactDirectoryMetadata"] as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val locArtifacts = locMetadata["artifactDirectories"] as List<Map<String, Any?>>
                @Suppress("UNCHECKED_CAST")
                val locEndpoints = locArtifacts.single { it["path"] == "child" }["resourceEndpoints"] as Map<String, List<Map<String, Any?>>>
                val locEndpoint = locEndpoints["java.native_binary_output.public.download"]!!.single { it["id"] == "algites-test-java-native-binary-output-public-release-download" }
                check(locEndpoint["enabled"] == true)
                check(locEndpoint["url"] == "https://repo.example.test/releases/")
                println("SETTINGS_PROJECT_IDENTITY_AND_INHERITANCE_OK")
                """);
        BuildResult locResult = GradleRunner.create().withProjectDir(locFixture.toFile())
                .withArguments("help", "resolveModustroArtifactDirectoryMetadata", "--offline", "--stacktrace")
                .build();
        Assert.assertTrue(locResult.getOutput().contains("SETTINGS_PROJECT_IDENTITY_AND_INHERITANCE_OK"));
    }

    @Test
    public void AIcMetadataOnlyNeedsNoSourceRepositoryInHelperBuild() throws IOException {
        Path locFixture = Files.createTempDirectory("modustro-gradleinit-metadata-");
        Files.writeString(locFixture.resolve("settings.gradle.kts"), AIcBootstrap());
        Files.writeString(locFixture.resolve("gradle.properties"), "modustro.gradleinit.metadataOnly=true\n");
        Files.writeString(locFixture.resolve("build.gradle.kts"), """
                import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroGradleRuntime
                import eu.algites.pltf.modustro.builder.model.resource.AIcgdResourceEndpoint_1
                val locRuntime = gradle.extra["modustroGradleRuntime"] as AIcModustroGradleRuntime
                check(locRuntime.endpointDeclarationClass === AIcgdResourceEndpoint_1::class.java)
                check(extra.has("modustroResolveArtifactDirectoryMetadataMap"))
                check(extra.has("modustroResolveCredentialValue"))
                println("METADATA_HELPER_OK")
                """);
        BuildResult locResult = GradleRunner.create().withProjectDir(locFixture.toFile())
                .withArguments("help", "--offline", "--stacktrace").build();
        Assert.assertTrue(locResult.getOutput().contains("METADATA_HELPER_OK"));
    }
    @Test
    public void AIcIsolatedBuildUsesLocalGradlePathsAndInheritedMetadata() throws IOException {
        Path locFixture = AIcFixture();
        Files.writeString(locFixture.resolve("modustro-source-repository.yml"),
                Files.readString(locFixture.resolve("modustro-source-repository.yml"))
                + "\nNestedGradleSettingsBuildPolicy: USE_ISOLATED_BUILD_ON_NESTED_SETTINGS\n");
        Files.writeString(locFixture.resolve("settings.gradle.kts"), AIcBootstrap());
        Files.writeString(locFixture.resolve("build.gradle.kts"), "");
        Files.writeString(locFixture.resolve("child/settings.gradle.kts"), AIcBootstrap());
        Files.writeString(locFixture.resolve("child/build.gradle.kts"), """
                import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroGradleRuntime
                import eu.algites.pltf.modustro.builder.model.resource.AIcgdResourceEndpoint_1
                val locRuntime = gradle.extra["modustroGradleRuntime"] as AIcModustroGradleRuntime
                check(locRuntime.endpointDeclarationClass === AIcgdResourceEndpoint_1::class.java)
                apply(plugin = "eu.algites.pltf.modustro.builder.repository")
                @Suppress("UNCHECKED_CAST")
                val locByProject = extra["modustroResolvedArtifactDirectoriesByGradleProjectPath"] as Map<String, Map<String, Any?>>
                check(locByProject.containsKey(":"))
                check(!locByProject.containsKey(":child"))
                check(locByProject[":"]!!["path"] == "child")
                check(locByProject[":"]!!["groupId"] == "eu.algites.test")
                println("ISOLATED_DOMAIN_OK")
                """);
        BuildResult locResult = GradleRunner.create().withProjectDir(locFixture.toFile())
                .withArguments("help", ":child:help", "--offline", "--stacktrace").build();
        Assert.assertTrue(locResult.getOutput().contains("ISOLATED_DOMAIN_OK"));
    }

}

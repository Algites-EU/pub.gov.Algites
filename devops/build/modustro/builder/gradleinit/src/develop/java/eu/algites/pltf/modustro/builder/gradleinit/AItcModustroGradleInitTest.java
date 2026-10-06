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
    /** Test invocations must not inherit a production CI publication identity. */
    private static java.util.Map<String, String> AIcTestEnvironment() {
        var locEnvironment = new java.util.HashMap<>(System.getenv());
        locEnvironment.remove("MODUSTRO_BUILD_INVOCATION_ID");
        return locEnvironment;
    }

    private static java.util.Map<String, String> AIcCompositeEnvironment() {
        var locEnvironment = AIcTestEnvironment();
        locEnvironment.put("MODUSTRO_BUILD_INVOCATION_ID", "test:" + java.util.UUID.randomUUID());
        return locEnvironment;
    }
    private static String AIcLatestPublicationRecord(Path aFixture) throws IOException {
        try (var locPaths = Files.walk(aFixture.resolve("build/run/publication-records"))) {
            var locRecords = locPaths.filter(aPath -> aPath.toString().endsWith(".json")).toList();
            Assert.assertFalse(locRecords.isEmpty());
            Path locNewest = locRecords.get(0);
            for (Path locRecord : locRecords) {
                if (Files.getLastModifiedTime(locRecord).compareTo(Files.getLastModifiedTime(locNewest)) > 0) locNewest = locRecord;
            }
            return Files.readString(locNewest);
        }
    }
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
                InputSubscriptions:
                  - InputSelector: native_product_binaries
                    TechnologyKind: java
                    Subscriptions:
                      - Id: algites-test-java-native-product-binaries-public-release-subscription
                        Visibility: public
                        SubscriptionUri: https://repo.example.test/releases/
                        SubscriptionAdapter: maven-repository
                        Stability: release
                        Enabled: false
                """);
        Path locChild = Files.createDirectories(locDirectory.resolve("child"));
        Files.writeString(locChild.resolve("modustro-artifact.yml"), """
                Artifact:
                  Name: Gradle init child
                  TechnologyKinds: [java]
                InputSubscriptions:
                  - InputSelector: native_product_binaries
                    TechnologyKind: java
                    Subscriptions:
                      - Id: algites-test-java-native-product-binaries-public-release-subscription
                        Enabled: true
                """);

        return locDirectory;
    }

    @Test
    public void AIcSettingsProjectAndAppliedProjectShareBuilderClass() throws IOException {
        Path locFixture = AIcFixture();
        Files.writeString(locFixture.resolve("settings.gradle.kts"), AIcBootstrap());
        Files.writeString(locFixture.resolve("applied.gradle.kts"), """
                import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroGradleRuntime
                import eu.algites.pltf.modustro.builder.model.subscription.AIcInputSubscription
                val locRuntime = gradle.extra["modustroGradleRuntime"] as AIcModustroGradleRuntime
                check(locRuntime.inputSubscriptionClass === AIcInputSubscription::class.java)
                """);
        Files.writeString(locFixture.resolve("build.gradle.kts"), """
                import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroGradleRuntime
                import eu.algites.pltf.modustro.builder.model.subscription.AIcInputSubscription
                val locRuntime = gradle.extra["modustroGradleRuntime"] as AIcModustroGradleRuntime
                check(locRuntime.inputSubscriptionClass === AIcInputSubscription::class.java)
                apply(from = "applied.gradle.kts")
                apply(plugin = "eu.algites.pltf.modustro.builder.repository")
                check(project(":child").projectDir == file("child"))
                @Suppress("UNCHECKED_CAST")
                val locMetadata = extra["modustroResolvedArtifactDirectoryMetadata"] as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val locArtifacts = locMetadata["artifactDirectories"] as List<Map<String, Any?>>
                @Suppress("UNCHECKED_CAST")
                val locSubscriptions = locArtifacts.single { it["path"] == "child" }["inputSubscriptions"] as Map<String, List<Map<String, Any?>>>
                val locSubscription = locSubscriptions["java.native_product_binaries"]!!.single { it["id"] == "algites-test-java-native-product-binaries-public-release-subscription" }
                check(locSubscription["enabled"] == true)
                check(locSubscription["subscriptionUri"] == "https://repo.example.test/releases/")
                check(locSubscription["subscriptionAdapter"] == "maven-repository")
                println("SETTINGS_PROJECT_IDENTITY_AND_INHERITANCE_OK")
                """);
        BuildResult locResult = GradleRunner.create().withEnvironment(AIcTestEnvironment()).withProjectDir(locFixture.toFile())
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
                import eu.algites.pltf.modustro.builder.model.subscription.AIcInputSubscription
                val locRuntime = gradle.extra["modustroGradleRuntime"] as AIcModustroGradleRuntime
                check(locRuntime.inputSubscriptionClass === AIcInputSubscription::class.java)
                check(extra.has("modustroResolveArtifactDirectoryMetadataMap"))
                check(extra.has("modustroResolveCredentialValue"))
                println("METADATA_HELPER_OK")
                """);
        BuildResult locResult = GradleRunner.create().withEnvironment(AIcTestEnvironment()).withProjectDir(locFixture.toFile())
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
                import eu.algites.pltf.modustro.builder.model.subscription.AIcInputSubscription
                val locRuntime = gradle.extra["modustroGradleRuntime"] as AIcModustroGradleRuntime
                check(locRuntime.inputSubscriptionClass === AIcInputSubscription::class.java)
                apply(plugin = "eu.algites.pltf.modustro.builder.repository")
                @Suppress("UNCHECKED_CAST")
                val locByProject = extra["modustroResolvedArtifactDirectoriesByGradleProjectPath"] as Map<String, Map<String, Any?>>
                check(locByProject.containsKey(":"))
                check(!locByProject.containsKey(":child"))
                check(locByProject[":"]!!["path"] == "child")
                check(locByProject[":"]!!["groupId"] == "eu.algites.test")
                println("ISOLATED_DOMAIN_OK")
                """);
        BuildResult locResult = GradleRunner.create().withEnvironment(AIcTestEnvironment()).withProjectDir(locFixture.toFile())
                .withArguments("help", ":child:help", "--offline", "--stacktrace").build();
        Assert.assertTrue(locResult.getOutput().contains("ISOLATED_DOMAIN_OK"));
    }

    /** Publishes through a runtime BuildService twice and proves that configuration cache is reusable. */
    @Test
    public void AIcPublicationReusesConfigurationCache() throws IOException {
        Path locFixture = AIcFixture();
        Files.writeString(locFixture.resolve("settings.gradle.kts"), AIcBootstrap());
        Files.writeString(locFixture.resolve("gradle.properties"), "modustro.gradleinit.metadataOnly=true\n");
        Files.writeString(locFixture.resolve("payload.txt"), "first payload");
        Files.createDirectories(locFixture.resolve("published/nested"));
        Files.writeString(locFixture.resolve("published/nested/payload.txt"), "initial destination");
        Files.writeString(locFixture.resolve("build.gradle.kts"), """
                import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroPublicationService
                import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroPublishFilesTask
                import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroAwaitPublicationsTask
                import groovy.json.JsonOutput
                val locService = gradle.sharedServices.registerIfAbsent("modustroPublications", AIcModustroPublicationService::class.java) {
                    parameters.credentialBaseDirectory.set(layout.projectDirectory)
                }
                val locPlan = JsonOutput.toJson(mapOf("publicationEnabled" to true, "publicationEndpoints" to listOf(
                    mapOf("id" to "local", "publicationAdapter" to "local-copy", "publicationUri" to file("published").toURI().toString())
                )))
                val locAwait = tasks.register<AIcModustroAwaitPublicationsTask>("awaitPublications") {
                    publicationService.set(locService)
                    usesService(locService)
                }
                tasks.register<AIcModustroPublishFilesTask>("publishFixture") {
                    publicationPlanJson.set(locPlan)
                    outputKind.set("NATIVE_PRODUCT_BINARIES")
                    stability.set("snapshot")
                    coordinates.set(mapOf("groupId" to "eu.algites.test", "artifactId" to "fixture", "technologyKind" to "java"))
                    artifactIdentity.set("fixture")
                    publicationVersion.set("1.0-SNAPSHOT")
                    payloadFiles.from(layout.projectDirectory.file("payload.txt"))
                    publishedFileNames.put("payload.txt", "nested/payload.txt")
                    publicationService.set(locService)
                    usesService(locService)
                    finalizedBy(locAwait)
                }
                """);
        var locRunner = GradleRunner.create().withEnvironment(AIcTestEnvironment()).withProjectDir(locFixture.toFile())
                .withArguments("publishFixture", "--configuration-cache", "--offline", "--stacktrace");
        Assert.assertTrue(locRunner.build().getOutput().contains("Configuration cache entry stored"));
        Assert.assertEquals(Files.readString(locFixture.resolve("published/nested/payload.txt")), "first payload");
        Assert.assertTrue(Files.isRegularFile(locFixture.resolve("published/nested/payload.txt.modustro-build-record.yml")));
        Files.writeString(locFixture.resolve("payload.txt"), "second payload");
        String locSecondOutput = locRunner.build().getOutput();
        Assert.assertTrue(locSecondOutput.contains("Configuration cache entry reused"), locSecondOutput);
        Assert.assertEquals(Files.readString(locFixture.resolve("published/nested/payload.txt")), "second payload");
    }

    @Test
    public void AIcMissingEffectiveGroupIdFailsBeforeBuild() throws IOException {
        Path fixture=AIcFixture();
        Path descriptor=fixture.resolve("modustro-source-repository.yml");
        Files.writeString(descriptor,Files.readString(descriptor).replace("GroupId: eu.algites.test\n", ""));
        Files.writeString(fixture.resolve("settings.gradle.kts"),AIcBootstrap());
        Files.writeString(fixture.resolve("build.gradle.kts"),"tasks.register(\"mustNotRun\") { doLast { error(\"TASK_EXECUTED\") } }\n");
        String output=GradleRunner.create().withEnvironment(AIcTestEnvironment()).withProjectDir(fixture.toFile()).withArguments("mustNotRun","--offline","--stacktrace").buildAndFail().getOutput();
        Assert.assertTrue(output.contains("requires an effective GroupId"),output);
        Assert.assertFalse(output.contains("TASK_EXECUTED"),output);
    }

    /** An omitted output must remain incomplete; the complete phase requests one repository refresh. */
    @Test
    public void AIcExpectedOutputsAndRepositoryRefreshSurviveConfigurationCache() throws IOException {
        Path locFixture = AIcFixture();
        Files.writeString(locFixture.resolve("modustro-source-repository.yml"),
                Files.readString(locFixture.resolve("modustro-source-repository.yml")) + """
                OutputPublications:
                  - OutputSelector: native_outputs
                    TechnologyKind: java
                    Snapshot:
                      PublicationEnabled: false
                  - OutputSelector: native_product_binaries
                    TechnologyKind: java
                    Snapshot:
                      PublicationEnabled: true
                  - OutputSelector: native_product_sources
                    TechnologyKind: java
                    Snapshot:
                      PublicationEnabled: true
                """);
        Files.writeString(locFixture.resolve("settings.gradle.kts"), AIcBootstrap());
        Files.writeString(locFixture.resolve("gradle.properties"), "modustro.gradleinit.metadataOnly=true\n");
        Files.writeString(locFixture.resolve("payload.txt"), "scope payload");
        Files.writeString(locFixture.resolve("build.gradle.kts"), """
                import eu.algites.pltf.modustro.builder.gradleinit.*
                import groovy.json.JsonOutput
                apply(plugin = "eu.algites.pltf.modustro.builder.repository")
                @Suppress("UNCHECKED_CAST")
                val locService = extra["modustroPublicationService"] as org.gradle.api.provider.Provider<AIcModustroPublicationService>
                val locAwait = tasks.register<AIcModustroAwaitPublicationsTask>("awaitPublications") {
                    publicationService.set(locService)
                    usesService(locService)
                }
                fun publishFixture(aName: String, aKind: String) = tasks.register<AIcModustroPublishFilesTask>(aName) {
                    val locPlan = mapOf("publicationEnabled" to true, "artifactPath" to "child", "versionScopePath" to ".",
                        "publicationEndpoints" to listOf(mapOf("id" to "local", "publicationAdapter" to "local-copy",
                            "publicationUri" to file("published/$aName").toURI().toString())),
                        "versionScopePublicationFinalizationActions" to listOf(mapOf("Id" to "refresh",
                            "VersionScopePublicationFinalizationActionAdapter" to "modustro-refresh-docs-site")))
                    publicationPlanJson.set(JsonOutput.toJson(locPlan))
                    outputKind.set(aKind)
                    stability.set("snapshot")
                    artifactIdentity.set("fixture")
                    publicationVersion.set("1.0-SNAPSHOT")
                    coordinates.set(mapOf("groupId" to "eu.algites.test", "artifactId" to "fixture",
                        "technologyKind" to if (aKind == "MODUSTRO_DOCS_SITE") "modustro" else "java"))
                    payloadFiles.from(layout.projectDirectory.file("payload.txt"))
                    publishedFileNames.put("payload.txt", "payload.txt")
                    publicationService.set(locService)
                    usesService(locService)
                }
                val locBinary = publishFixture("publishBinary", "NATIVE_PRODUCT_BINARIES")
                val locSources = publishFixture("publishSources", "NATIVE_PRODUCT_SOURCES")
                val locDocs = publishFixture("refreshDocs", "MODUSTRO_DOCS_SITE")
                locDocs.configure { requiresRepositoryRefreshRequest.set(true) }
                locAwait.configure { mustRunAfter(locBinary, locSources); finalizedBy(locDocs) }
                locBinary.configure { finalizedBy(locAwait) }
                tasks.register("publishAll") { dependsOn(locBinary, locSources); finalizedBy(locAwait) }
                """);
        var locRunner = GradleRunner.create().withEnvironment(AIcTestEnvironment()).withProjectDir(locFixture.toFile())
                .withArguments("publishBinary", "--configuration-cache", "--offline", "--stacktrace");
        Assert.assertTrue(locRunner.build().getOutput().contains("Configuration cache entry stored"));
        String locPartial = AIcLatestPublicationRecord(locFixture);
        Assert.assertTrue(locPartial.contains("\"State\":\"FAILED\""), locPartial);
        Assert.assertTrue(locPartial.contains("\"Completed\":false"), locPartial);
        Assert.assertFalse(Files.exists(locFixture.resolve("published/refreshDocs/payload.txt")));
        Assert.assertTrue(locRunner.build().getOutput().contains("Configuration cache entry reused"));
        locRunner.withArguments("publishAll", "--configuration-cache", "--offline", "--stacktrace").build();
        String locComplete = AIcLatestPublicationRecord(locFixture);
        Assert.assertTrue(locComplete.contains("\"State\":\"COMPLETE\""), locComplete);
        Assert.assertEquals(Files.readString(locFixture.resolve("published/refreshDocs/payload.txt")), "scope payload");
    }

    private static String AIcCompositePublicationScript(String aArtifactPath, boolean aRepositoryDocs) {
        String locScript = """
                import eu.algites.pltf.modustro.builder.gradleinit.*
                import groovy.json.JsonOutput
                apply(plugin = "eu.algites.pltf.modustro.builder.repository")
                @Suppress("UNCHECKED_CAST")
                val locService = extra["modustroPublicationService"] as org.gradle.api.provider.Provider<AIcModustroPublicationService>
                fun publishFixture(aName: String, aKind: String, aArtifactPath: String, aTechnology: String = "java") = tasks.register<AIcModustroPublishFilesTask>(aName) {
                    publicationPlanJson.set(JsonOutput.toJson(mapOf("publicationEnabled" to true,
                        "artifactPath" to aArtifactPath, "versionScopePath" to ".",
                        "publicationEndpoints" to listOf(mapOf("id" to "local", "publicationAdapter" to "local-copy",
                            "publicationUri" to file("published/$aName").toURI().toString())),
                        "versionScopePublicationFinalizationActions" to listOf(mapOf("Id" to "refresh",
                            "VersionScopePublicationFinalizationActionAdapter" to "modustro-refresh-docs-site")))))
                    outputKind.set(aKind)
                    stability.set("snapshot")
                    artifactIdentity.set("eu.algites.test:$aArtifactPath" + if (aKind == "NATIVE_DEVELOP_BINARIES") ":develop" else "")
                    publicationVersion.set("1.0-SNAPSHOT")
                    coordinates.set(mapOf("groupId" to "eu.algites.test", "artifactId" to aArtifactPath,
                        "technologyKind" to if (aKind == "MODUSTRO_DOCS_SITE") "modustro" else aTechnology))
                    payloadFiles.from(layout.projectDirectory.file("payload.txt"))
                    publishedFileNames.put("payload.txt", "payload.txt")
                    publicationService.set(locService)
                    usesService(locService)
                }
                val locNative = publishFixture("publishFixture", "NATIVE_PRODUCT_BINARIES", "%s")
                """.formatted(aArtifactPath);
        if (aRepositoryDocs) locScript += """
                val locDevelop = publishFixture("publishDevelop", "NATIVE_DEVELOP_BINARIES", "local", "python")
                tasks.register("modustroPublish") { dependsOn(locNative, locDevelop) }
                publishFixture("refreshModustroDocsSite", "MODUSTRO_DOCS_SITE", ".").configure {
                    requiresRepositoryRefreshRequest.set(true)
                }
                """;
        else locScript += "tasks.register(\"modustroPublish\") { dependsOn(locNative) }\n";
        return locScript;
    }

    /** Shared scopes cross real included-build classloaders and must reject absent producers. */
    @Test
    public void AIcCompositePublicationFinalizesOnceAndReusesConfigurationCache() throws IOException {
        Path locFixture = AIcFixture();
        Path locDescriptor = locFixture.resolve("modustro-source-repository.yml");
        Files.writeString(locDescriptor, Files.readString(locDescriptor) + """
                NestedGradleSettingsBuildPolicy: USE_ISOLATED_BUILD_ON_NESTED_SETTINGS
                OutputPublications:
                  - OutputSelector: native_outputs
                    TechnologyKind: java
                    Snapshot:
                      PublicationEnabled: false
                """);
        Path locLocal = Files.createDirectories(locFixture.resolve("local"));
        Files.writeString(locLocal.resolve("modustro-artifact.yml"), "Artifact:\n  Name: Local artifact\n  TechnologyKinds: [java]\n");
        Files.writeString(locFixture.resolve("settings.gradle.kts"), AIcBootstrap());
        Files.writeString(locFixture.resolve("child/settings.gradle.kts"), AIcBootstrap());
        Files.writeString(locFixture.resolve("payload.txt"), "root payload");
        Files.writeString(locFixture.resolve("child/payload.txt"), "child payload");
        Files.writeString(locFixture.resolve("build.gradle.kts"), AIcCompositePublicationScript("local", true));
        Files.writeString(locFixture.resolve("child/build.gradle.kts"), AIcCompositePublicationScript("child", false));
        var locRunner = GradleRunner.create().withProjectDir(locFixture.toFile())
                .withArguments("modustroPublish", "--configuration-cache", "--offline", "--stacktrace");
        String locWithoutIdentity = locRunner.withEnvironment(AIcTestEnvironment()).buildAndFail().getOutput();
        Assert.assertTrue(locWithoutIdentity.contains("MODUSTRO_BUILD_INVOCATION_ID"), locWithoutIdentity);
        Assert.assertFalse(Files.exists(locFixture.resolve("published/publishFixture/payload.txt")));
        Assert.assertFalse(Files.exists(locFixture.resolve("child/published/publishFixture/payload.txt")));
        String locFirst = locRunner.withEnvironment(AIcCompositeEnvironment()).build().getOutput();
        Assert.assertTrue(locFirst.contains("Configuration cache entry stored") || locFirst.contains("Configuration cache entry reused"), locFirst);
        String locRecord = AIcLatestPublicationRecord(locFixture);
        Assert.assertTrue(locRecord.contains("\"State\":\"COMPLETE\""), locRecord);
        Assert.assertTrue(locRecord.contains("\"ArtifactPath\":\"local\""), locRecord);
        Assert.assertTrue(locRecord.contains("\"ArtifactPath\":\"child\""), locRecord);
        Assert.assertEquals(locRecord.split("\"ArtifactPath\":\"local\"", -1).length - 1, 1, locRecord);
        Assert.assertTrue(locRecord.contains("\"TechnologyKind\":\"python\""), locRecord);
        Assert.assertTrue(locRecord.contains("eu.algites.test:local:develop"), locRecord);
        Assert.assertEquals(Files.readString(locFixture.resolve("published/refreshModustroDocsSite/payload.txt")), "root payload");
        String locSecond = locRunner.withEnvironment(AIcCompositeEnvironment()).build().getOutput();
        Assert.assertTrue(locSecond.contains("Configuration cache entry reused"), locSecond);
        String locPropertyId = "test:" + java.util.UUID.randomUUID();
        locRunner.withEnvironment(AIcTestEnvironment()).withArguments("modustroPublish",
                "-Pmodustro.build.invocationId=" + locPropertyId, "--configuration-cache", "--offline", "--stacktrace").build();
        String locCommitted = AIcLatestPublicationRecord(locFixture);
        Assert.assertTrue(locCommitted.contains("\"State\":\"COMPLETE\""), locCommitted);
        Files.writeString(locFixture.resolve("payload.txt"), "must not overwrite committed native output");
        String locRepeated = locRunner.buildAndFail().getOutput();
        Assert.assertTrue(locRepeated.contains("finalized") || locRepeated.contains("committed"), locRepeated);
        Assert.assertEquals(AIcLatestPublicationRecord(locFixture), locCommitted);
        Assert.assertEquals(Files.readString(locFixture.resolve("published/publishFixture/payload.txt")), "root payload");
        Files.writeString(locFixture.resolve("payload.txt"), "root payload");
        locRunner.withArguments("modustroPublish", "--configuration-cache", "--offline", "--stacktrace");
        Files.delete(locFixture.resolve("published/refreshModustroDocsSite/payload.txt"));
        Path locChildBuild = locFixture.resolve("child/build.gradle.kts");
        Files.writeString(locChildBuild, Files.readString(locChildBuild)
                .replace("tasks.register(\"modustroPublish\") { dependsOn(locNative) }", "tasks.register(\"modustroPublish\")"));
        String locMissing = locRunner.withEnvironment(AIcCompositeEnvironment()).buildAndFail().getOutput();
        Assert.assertTrue(locMissing.contains("incomplete"), locMissing);
        Assert.assertTrue(AIcLatestPublicationRecord(locFixture).contains("\"State\":\"FAILED\""));
        Assert.assertFalse(Files.exists(locFixture.resolve("published/refreshModustroDocsSite/payload.txt")));
    }
}

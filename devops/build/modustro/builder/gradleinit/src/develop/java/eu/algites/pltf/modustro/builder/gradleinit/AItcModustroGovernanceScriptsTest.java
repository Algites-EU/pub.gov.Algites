package eu.algites.pltf.modustro.builder.gradleinit;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.gradle.testkit.runner.GradleRunner;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests publication bridge initialization in repositories without local governance copies. */
public final class AItcModustroGovernanceScriptsTest {
    private static final String OVERRIDES_PATH = "gradle/tool/repository/modustro-publication-overrides.gradle.kts";
    private static final String OVERRIDES_URL = "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/" + OVERRIDES_PATH;

    /** Supplies both publication entry points and both local and downloaded initialization paths. */
    @DataProvider(name = "entryPoints")
    public Object[][] AIcEntryPoints() {
        return new Object[][] {{false, false}, {false, true}, {true, false}, {true, true}};
    }

    /** Verifies real Gradle script initialization, ancestor discovery and invocation overrides. */
    @Test(dataProvider = "entryPoints")
    public void AIcInitializesPublicationOverrides(boolean aRootBuild, boolean aLocalCopy) throws IOException {
        Path locRepository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locRepository != null && !Files.isRegularFile(locRepository.resolve("modustro-source-repository.yml"))) {
            locRepository = locRepository.getParent();
        }
        if (locRepository == null) throw new IOException("Cannot locate governance repository sources.");
        String locOverrides = Files.readString(locRepository.resolve(OVERRIDES_PATH));
        String locEntry = Files.readString(locRepository.resolve(aRootBuild
            ? "gradle/tool/repository/modustro-root-build.gradle.kts"
            : "gradle/tool/publication/modustro-publication.gradle.kts"));
        if (aRootBuild) {
            locEntry = locEntry.substring(locEntry.indexOf("/* Publication invocation overrides must be available"),
                locEntry.indexOf("val locAlgitesRequiredCredentialsPlan = run"));
        }
        AtomicInteger locDownloads = new AtomicInteger();
        HttpServer locServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        locServer.createContext("/overrides.gradle.kts", aExchange -> {
            locDownloads.incrementAndGet();
            byte[] locBytes = locOverrides.getBytes(StandardCharsets.UTF_8);
            aExchange.sendResponseHeaders(200, locBytes.length);
            try (var locResponse = aExchange.getResponseBody()) { locResponse.write(locBytes); }
            aExchange.close();
        });
        locServer.start();
        try {
            Path locFixture = Files.createTempDirectory("modustro-governance-fallback-");
            Files.writeString(locFixture.resolve("modustro-source-repository.yml"), "SourceRepository:\n  Id: pub.test.fallback\n");
            if (aLocalCopy) {
                Path locLocal = locFixture.resolve(OVERRIDES_PATH);
                Files.createDirectories(locLocal.getParent());
                Files.writeString(locLocal, locOverrides);
            }
            Path locBuild = Files.createDirectories(locFixture.resolve("nested"));
            Files.writeString(locBuild.resolve("settings.gradle.kts"), "rootProject.name = \"fallback-test\"\n");
            locEntry = locEntry.replace("${System.getenv(\"MODUSTRO_PUBLIC_GOVERNANCE_REVISION\") ?: \"main\"}", "main");
            String locTestUrl = "http://127.0.0.1:" + locServer.getAddress().getPort() + "/overrides.gradle.kts";
            Files.writeString(locBuild.resolve("bridge.gradle.kts"), locEntry.replace(OVERRIDES_URL, locTestUrl));
            Files.writeString(locBuild.resolve("build.gradle.kts"), """
                extra["modustroResolvedArtifactDirectoryMetadata"] = emptyList<Map<String, Any?>>()
                apply(from = "bridge.gradle.kts")
                apply(from = "bridge.gradle.kts")
                @Suppress("UNCHECKED_CAST")
                val locResolve = extra["modustroEffectivePublicationPlan"] as (Map<String, Any?>, String, String) -> Map<String, Any?>
                val locMetadata = mapOf<String, Any?>("outputPublications" to mapOf(
                    "native_product_binaries" to mapOf("snapshot" to mapOf("publicationEnabled" to true))
                ))
                check(locResolve(locMetadata, "native_product_binaries", "snapshot")["publicationEnabled"] == false)
                println("PUBLICATION_FALLBACK_OK")
                """);
            var locResult = GradleRunner.create().withProjectDir(locBuild.toFile())
                .withArguments("printModustroPublicationOverrides", "-Pmodustro.publication.nativeProductBinaries=FORCE_OFF",
                    "--stacktrace").build();
            Assert.assertTrue(locResult.getOutput().contains("PUBLICATION_FALLBACK_OK"));
            Assert.assertEquals(locDownloads.get(), aLocalCopy ? 0 : 1);
        } finally {
            locServer.stop(0);
        }
    }

    /** Covers local phase overrides and repositories relying entirely on shared governance. */
    @DataProvider(name = "phaseScripts")
    public Object[][] AIcPhaseScripts() {
        return new Object[][] {{false}, {true}};
    }

    /** Initializes all six phases from the real root bridge and verifies the prepare task graph. */
    @Test(dataProvider = "phaseScripts")
    public void AIcInitializesPhaseScript(boolean aLocalCopy) throws IOException {
        Path locRepository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locRepository != null && !Files.isRegularFile(locRepository.resolve("modustro-source-repository.yml"))) {
            locRepository = locRepository.getParent();
        }
        if (locRepository == null) throw new IOException("Cannot locate governance repository sources.");
        String locRelative = "gradle/tool/repository/modustro-build-phase-tasks.gradle.kts";
        String locPhaseScript = Files.readString(locRepository.resolve(locRelative));
        String locRoot = Files.readString(locRepository.resolve("gradle/tool/repository/modustro-root-build.gradle.kts"));
        String locBridge = locRoot.substring(locRoot.indexOf("/* Modustro Builder 5.2 phase and publishing invocation adapters. */"));
        AtomicInteger locDownloads = new AtomicInteger();
        HttpServer locServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        locServer.createContext("/modustro-build-phase-tasks.gradle.kts", aExchange -> {
            byte[] locBytes = locPhaseScript.getBytes(StandardCharsets.UTF_8);
            aExchange.getResponseHeaders().set("ETag", "\"phase-script-v1\"");
            aExchange.getResponseHeaders().set("Last-Modified", "Fri, 02 Oct 2026 00:00:00 GMT");
            aExchange.getResponseHeaders().set("Content-Length", Integer.toString(locBytes.length));
            if (aExchange.getRequestMethod().equals("HEAD")) {
                aExchange.sendResponseHeaders(200, -1);
            } else {
                locDownloads.incrementAndGet();
                aExchange.sendResponseHeaders(200, locBytes.length);
                try (var locResponse = aExchange.getResponseBody()) { locResponse.write(locBytes); }
            }
            aExchange.close();
        });
        locServer.start();
        try {
            Path locFixture = Files.createTempDirectory("modustro-phase-fallback-");
            Files.writeString(locFixture.resolve("modustro-source-repository.yml"), "SourceRepository:\n  Id: pub.test.phases\n");
            if (aLocalCopy) {
                Path locLocal = locFixture.resolve(locRelative);
                Files.createDirectories(locLocal.getParent());
                Files.writeString(locLocal, locPhaseScript);
            }
            Path locBuild = Files.createDirectories(locFixture.resolve("nested"));
            Files.writeString(locBuild.resolve("settings.gradle.kts"), "rootProject.name = \"phase-test\"\n");
            String locRemotePrefix = "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/";
            String locTestPrefix = "http://127.0.0.1:" + locServer.getAddress().getPort() + "/";
            Files.writeString(locBuild.resolve("bridge.gradle.kts"), locBridge.replace("${System.getenv(\"MODUSTRO_PUBLIC_GOVERNANCE_REVISION\") ?: \"main\"}", "main").replace(locRemotePrefix, locTestPrefix));
            Files.writeString(locBuild.resolve("build.gradle.kts"), """
                tasks.register("modustroDependencyPreflight")
                tasks.register("processModustroJavaNativeSources") {
                    doLast { println("REMOTE_PHASE_PREPARED") }
                }
                tasks.register("unrelated") { error("Unrelated task was realized") }
                apply(from = "bridge.gradle.kts")
                val locPhases = listOf("Resolve", "Prepare", "Compile", "Verify", "Package", "Publish")
                check(locPhases.all { "modustro${it}Phase" in tasks.names })
                """);
            var locRunner = GradleRunner.create().withProjectDir(locBuild.toFile())
                .withArguments("modustroPreparePhase", "--configuration-cache", "--stacktrace");
            Assert.assertTrue(locRunner.build().getOutput().contains("REMOTE_PHASE_PREPARED"));
            int locDownloadsAfterFirst = locDownloads.get();
            Assert.assertEquals(locDownloadsAfterFirst, aLocalCopy ? 0 : 1);
            String locSecondOutput = locRunner.build().getOutput();
            Assert.assertTrue(locSecondOutput.contains("Reusing configuration cache"), locSecondOutput);
            Assert.assertEquals(locDownloads.get(), locDownloadsAfterFirst);
        } finally {
            locServer.stop(0);
        }
    }

}

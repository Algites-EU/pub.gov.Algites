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

/** Tests publishing bridge initialization in repositories without local governance copies. */
public final class AItcModustroGovernanceScriptsTest {
    private static final String OVERRIDES_PATH = "gradle/tool/repository/modustro-publishing-overrides.gradle.kts";
    private static final String OVERRIDES_URL = "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/" + OVERRIDES_PATH;

    /** Supplies both publishing entry points and both local and downloaded initialization paths. */
    @DataProvider(name = "entryPoints")
    public Object[][] AIcEntryPoints() {
        return new Object[][] {{false, false}, {false, true}, {true, false}, {true, true}};
    }

    /** Verifies real Gradle script initialization, ancestor discovery and invocation overrides. */
    @Test(dataProvider = "entryPoints")
    public void AIcInitializesPublishingOverrides(boolean aRootBuild, boolean aLocalCopy) throws IOException {
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
            locEntry = locEntry.substring(locEntry.indexOf("/* Publishing invocation overrides must be available"),
                locEntry.indexOf("@Suppress(\"UNCHECKED_CAST\")\nval modustroPublishingService"));
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
            String locTestUrl = "http://127.0.0.1:" + locServer.getAddress().getPort() + "/overrides.gradle.kts";
            Files.writeString(locBuild.resolve("bridge.gradle.kts"), locEntry.replace(OVERRIDES_URL, locTestUrl));
            Files.writeString(locBuild.resolve("build.gradle.kts"), """
                extra["modustroResolvedArtifactDirectoryMetadata"] = emptyList<Map<String, Any?>>()
                apply(from = "bridge.gradle.kts")
                apply(from = "bridge.gradle.kts")
                @Suppress("UNCHECKED_CAST")
                val locResolve = extra["modustroEffectivePublishingPlan"] as (Map<String, Any?>, String, String) -> Map<String, Any?>
                val locMetadata = mapOf<String, Any?>("outputPublishing" to mapOf(
                    "native_binary_output" to mapOf("snapshot" to mapOf("publishingEnabled" to true))
                ))
                check(locResolve(locMetadata, "native_binary_output", "snapshot")["publishingEnabled"] == false)
                println("PUBLISHING_FALLBACK_OK")
                """);
            var locResult = GradleRunner.create().withProjectDir(locBuild.toFile())
                .withArguments("printModustroPublishingOverrides", "-Pmodustro.publishing.nativeBinaryOutput=FORCE_OFF",
                    "--stacktrace").build();
            Assert.assertTrue(locResult.getOutput().contains("PUBLISHING_FALLBACK_OK"));
            Assert.assertEquals(locDownloads.get(), aLocalCopy ? 0 : 1);
        } finally {
            locServer.stop(0);
        }
    }
}

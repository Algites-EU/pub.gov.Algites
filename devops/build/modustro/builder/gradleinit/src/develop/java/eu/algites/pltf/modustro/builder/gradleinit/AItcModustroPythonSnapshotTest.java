package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.gradle.testkit.runner.GradleRunner;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Exercises shared snapshot conversion through real Gradle, including cached configurations. */
public final class AItcModustroPythonSnapshotTest {
    private Path AIcFixture() throws IOException {
        Path locRepository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locRepository != null && !Files.isRegularFile(locRepository.resolve("modustro-source-repository.yml"))) {
            locRepository = locRepository.getParent();
        }
        if (locRepository == null) throw new IOException("Cannot locate governance sources.");
        String locSource = Files.readString(locRepository.resolve("gradle/tool/repository/modustro-root-build.gradle.kts"));
        String locFunctions = locSource.substring(locSource.indexOf("/** Provides a fresh UTC timestamp"),
            locSource.indexOf("fun AIcAlgitesCanonicalArtifactId"));
        Path locFixture = Files.createTempDirectory("modustro-python-snapshot-");
        Files.writeString(locFixture.resolve("settings.gradle.kts"), "rootProject.name = \"snapshot-fixture\"\n");
        Files.writeString(locFixture.resolve("build.gradle.kts"), locFunctions + """
            val locId = AIcAlgitesSnapshotInstanceId()
            val locVersion = AIcAlgitesPythonVersion("1.0-SNAPSHOT", locId)
            check(AIcAlgitesPythonVersion("1.0", locId) == "1.0")
            check(AIcAlgitesPythonSnapshotVersionPrefix("1.0") == "1.0.dev")
            tasks.register("snapshotVersion") {
                inputs.property("snapshotVersion", locVersion)
                doLast { println("SNAPSHOT_VERSION=" + inputs.properties["snapshotVersion"]) }
            }
            """);
        return locFixture;
    }

    private GradleRunner AIcRunner(Path aFixture, String... aExtra) {
        Map<String, String> locEnvironment = new HashMap<>(System.getenv());
        locEnvironment.remove("ALGITES_SNAPSHOT_INSTANCE_ID");
        var locArgs = new java.util.ArrayList<>(java.util.List.of("snapshotVersion", "--configuration-cache", "--stacktrace"));
        locArgs.addAll(java.util.List.of(aExtra));
        return GradleRunner.create().withProjectDir(aFixture.toFile()).withEnvironment(locEnvironment).withArguments(locArgs);
    }

    /** A changed explicit instance invalidates cached metadata; fixed dev0 is refused. */
    @Test
    public void AIcUsesConcreteInstanceAndInvalidatesCache() throws IOException {
        Path locFixture = AIcFixture();
        String locFirst = AIcRunner(locFixture, "-Palgites.snapshot.instanceId=20261004081500123").build().getOutput();
        Assert.assertTrue(locFirst.contains("SNAPSHOT_VERSION=1.0.dev20261004081500123"));
        String locSecond = AIcRunner(locFixture, "-Palgites.snapshot.instanceId=20261004081600456").build().getOutput();
        Assert.assertTrue(locSecond.contains("SNAPSHOT_VERSION=1.0.dev20261004081600456"));
        Assert.assertFalse(locSecond.contains("SNAPSHOT_VERSION=1.0.dev20261004081500123"));
        Assert.assertTrue(AIcRunner(locFixture, "-Palgites.snapshot.instanceId=0").buildAndFail().getOutput()
            .contains("must be a UTC timestamp"));
        Map<String, String> locEnv = new HashMap<>(System.getenv());
        locEnv.put("ALGITES_SNAPSHOT_INSTANCE_ID", "20261004081700789");
        Assert.assertTrue(AIcRunner(locFixture).withEnvironment(locEnv).build().getOutput()
            .contains("SNAPSHOT_VERSION=1.0.dev20261004081700789"));
    }

    /** Independent local builds get fresh timestamps instead of reusing a cached dev0. */
    @Test
    public void AIcGeneratesFreshTimestampWithConfigurationCache() throws IOException {
        Path locFixture = AIcFixture();
        Pattern locPattern = Pattern.compile("SNAPSHOT_VERSION=(1\\.0\\.dev[0-9]{17})");
        var locFirst = locPattern.matcher(AIcRunner(locFixture).build().getOutput());
        Assert.assertTrue(locFirst.find());
        var locSecond = locPattern.matcher(AIcRunner(locFixture).build().getOutput());
        Assert.assertTrue(locSecond.find());
        Assert.assertNotEquals(locFirst.group(1), locSecond.group(1));
    }
}

package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Verifies that historical documentation cannot make governance convention checking fail. */
public final class AItcAlgitesGovernanceConventionsTest {
    /** Keeps legacy references in documentation non-fatal while enforcing active descriptors. */
    @Test
    public void AIcDistinguishesDocumentationWarningsFromSourceFailures() throws IOException {
        Path locRepository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locRepository != null && !Files.isRegularFile(locRepository.resolve("modustro-source-repository.yml"))) {
            locRepository = locRepository.getParent();
        }
        if (locRepository == null) throw new IOException("Cannot locate governance repository sources.");

        Path locFixture = Files.createTempDirectory("algites-governance-conventions-");
        Path locValidationScript = Path.of("gradle/tool/validation/algites-governance-conventions.gradle.kts");
        Files.createDirectories(locFixture.resolve(locValidationScript).getParent());
        Files.copy(locRepository.resolve(locValidationScript), locFixture.resolve(locValidationScript));
        Files.writeString(locFixture.resolve("settings.gradle.kts"), "rootProject.name = \"governance-conventions-test\"\n");
        Files.writeString(locFixture.resolve("build.gradle.kts"),
            "apply(from = file(\"gradle/tool/validation/algites-governance-conventions.gradle.kts\"))\n");
        Files.writeString(locFixture.resolve("MODUSTRO-MIGRATION.md"),
            "# Historical migration\nPreviously: algites-java-public-release-upload\n"
                + "Previously: algites-java-native-product-sources-public-release-upload\n",
            StandardCharsets.UTF_8);
        Files.writeString(locFixture.resolve("HISTORY.txt"),
            "Earlier endpoint: https://dummy.invalid/service/upload/legacy\n",
            StandardCharsets.UTF_8);
        Files.createDirectories(locFixture.resolve("docs/examples"));
        Files.writeString(locFixture.resolve("docs/examples/historical.yml"),
            "credential_profile: algites-java-public-release-upload\n",
            StandardCharsets.UTF_8);

        GradleRunner locRunner = GradleRunner.create()
            .withProjectDir(locFixture.toFile())
            .withArguments("checkAlgitesGovernanceConventions", "--stacktrace");
        String locDocumentationOutput = locRunner.build().getOutput();
        Assert.assertTrue(locDocumentationOutput.contains("BUILD SUCCESSFUL"), locDocumentationOutput);
        Assert.assertTrue(locDocumentationOutput.contains("Algites governance documentation warning (non-fatal):"),
            locDocumentationOutput);
        Assert.assertTrue(locDocumentationOutput.contains("MODUSTRO-MIGRATION.md:2"), locDocumentationOutput);
        Assert.assertTrue(locDocumentationOutput.contains("MODUSTRO-MIGRATION.md:3"), locDocumentationOutput);
        Assert.assertTrue(locDocumentationOutput.contains("HISTORY.txt:1"), locDocumentationOutput);
        Assert.assertTrue(locDocumentationOutput.contains("docs/examples/historical.yml:1"), locDocumentationOutput);

        Files.writeString(locFixture.resolve("active-config.yml"),
            "credential_profile: algites-java-public-release-upload\n",
            StandardCharsets.UTF_8);
        String locActiveSourceOutput = locRunner.buildAndFail().getOutput();
        Assert.assertTrue(locActiveSourceOutput.contains("Algites governance convention check failed:"),
            locActiveSourceOutput);
        Assert.assertTrue(locActiveSourceOutput.contains("active-config.yml:1 obsolete credential profile id"),
            locActiveSourceOutput);
    }
}

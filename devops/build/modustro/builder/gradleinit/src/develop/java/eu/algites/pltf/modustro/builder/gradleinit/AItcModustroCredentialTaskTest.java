package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Exercises canonical and existing workflow task names against the shared registration. */
public final class AItcModustroCredentialTaskTest {
    private static final String CANONICAL = "resolveModustroRequiredCredentials";
    private static final String LEGACY = "resolveAlgitesRequiredCredentials";
    private static final String PLAN = "{\"resourceEndpoints\":[],\"credentials\":[]}";

    /** Covers both individual entry points and their simultaneous invocation. */
    @DataProvider(name = "taskNames")
    public Object[][] AIcTaskNames() {
        return new Object[][] {{List.of(CANONICAL)}, {List.of(LEGACY)}, {List.of(LEGACY, CANONICAL)}};
    }

    /** Both names write the same plan; requesting both executes the resolver once. */
    @Test(dataProvider = "taskNames")
    public void AIcPreservesWorkflowCredentialPlan(List<String> aTasks) throws IOException {
        Path locRepository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locRepository != null && !Files.isRegularFile(locRepository.resolve("modustro-source-repository.yml"))) {
            locRepository = locRepository.getParent();
        }
        if (locRepository == null) throw new IOException("Cannot locate governance sources.");
        String locSource = Files.readString(locRepository.resolve("gradle/tool/repository/modustro-root-build.gradle.kts"));
        String locTask = locSource.substring(locSource.indexOf("abstract class AIcResolveModustroRequiredCredentialsTask"),
            locSource.indexOf("val locAlgitesRequiredCredentialsPlan = run"));
        String locRegistration = locSource.substring(locSource.indexOf("val modustroResolveRequiredCredentials = tasks.register"),
            locSource.indexOf("/* Publishing invocation overrides must be available"));
        Path locFixture = Files.createTempDirectory("modustro-credential-entry-");
        Files.writeString(locFixture.resolve("settings.gradle.kts"), "rootProject.name = \"credential-entry-test\"\n");
        Files.writeString(locFixture.resolve("build.gradle.kts"), """
            import org.gradle.api.provider.Property
            import org.gradle.api.provider.ListProperty
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.*
            fun modustroGradleOrEnvironmentValue(aName: String): String? = providers.gradleProperty(aName).orNull
            val locAlgitesRequiredCredentialsPlan = Triple("{\\\"resourceEndpoints\\\":[],\\\"credentials\\\":[]}", 0, listOf("java"))
            """ + locTask + locRegistration);
        Path locPlan = locFixture.resolve("output with spaces/credentials.json");
        var locArgs = new java.util.ArrayList<>(aTasks);
        locArgs.addAll(List.of("-Palgites.credential.output=" + locPlan, "--configuration-cache", "--stacktrace"));
        var locRunner = GradleRunner.create().withProjectDir(locFixture.toFile()).withArguments(locArgs);
        var locFirst = locRunner.build();
        Assert.assertEquals(locFirst.task(":" + CANONICAL).getOutcome(), TaskOutcome.SUCCESS);
        Assert.assertEquals(Files.readString(locPlan).trim(), PLAN);
        Assert.assertEquals(locFirst.getOutput().split("Algites credential preflight wrote", -1).length - 1, 1);
        if (aTasks.contains(LEGACY)) Assert.assertNotNull(locFirst.task(":" + LEGACY));
        var locSecond = locRunner.build();
        Assert.assertEquals(locSecond.task(":" + CANONICAL).getOutcome(), TaskOutcome.UP_TO_DATE);
        Assert.assertTrue(locSecond.getOutput().contains("Reusing configuration cache"));
        Assert.assertEquals(Files.readString(locPlan).trim(), PLAN);
    }
}

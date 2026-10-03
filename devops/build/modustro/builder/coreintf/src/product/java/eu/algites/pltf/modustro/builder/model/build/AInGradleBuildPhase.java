package eu.algites.pltf.modustro.builder.model.build;

/** Ordered repository-wide Gradle execution phases used by Modustro Builder. */
public enum AInGradleBuildPhase {
    RESOLVE("modustroResolvePhase"),
    PREPARE("modustroPreparePhase"),
    COMPILE("modustroCompilePhase"),
    VERIFY("modustroVerifyPhase"),
    PACKAGE("modustroPackagePhase"),
    PUBLISH("modustroPublishPhase");

    private final String gradleTaskName;

    AInGradleBuildPhase(String aGradleTaskName) {
        gradleTaskName = aGradleTaskName;
    }

    /** Returns the Gradle task implementing this phase inside one build domain. */
    public String gradleTaskName() {
        return gradleTaskName;
    }
}

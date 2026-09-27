plugins {
    `java-library`
    `maven-publish`
}

val locPubLibGeneralVersion = providers.gradleProperty("algites.build.pubLibGeneral.version")
    .orElse("1.0-SNAPSHOT")

val locPubLibGeneralGroupId = "eu.algites.lib.common"
val locPubLibGeneralArtifactIdPrefix = "pub.lib.General_"

dependencies {
    api("$locPubLibGeneralGroupId:${locPubLibGeneralArtifactIdPrefix}version.core:${locPubLibGeneralVersion.get()}")

    api("$locPubLibGeneralGroupId:${locPubLibGeneralArtifactIdPrefix}version.scheme.algites.v1:${locPubLibGeneralVersion.get()}")
    api("$locPubLibGeneralGroupId:${locPubLibGeneralArtifactIdPrefix}version.scheme.maven:${locPubLibGeneralVersion.get()}")
    api("$locPubLibGeneralGroupId:${locPubLibGeneralArtifactIdPrefix}version.scheme.gradle:${locPubLibGeneralVersion.get()}")
    api("$locPubLibGeneralGroupId:${locPubLibGeneralArtifactIdPrefix}version.scheme.pep440:${locPubLibGeneralVersion.get()}")

    api("$locPubLibGeneralGroupId:${locPubLibGeneralArtifactIdPrefix}version.scheme.conversion.common:${locPubLibGeneralVersion.get()}")
    api("$locPubLibGeneralGroupId:${locPubLibGeneralArtifactIdPrefix}version.scheme.conversion.maven2gradle:${locPubLibGeneralVersion.get()}")
    api("$locPubLibGeneralGroupId:${locPubLibGeneralArtifactIdPrefix}version.scheme.conversion.algites2maven.v1:${locPubLibGeneralVersion.get()}")
    api("$locPubLibGeneralGroupId:${locPubLibGeneralArtifactIdPrefix}version.scheme.conversion.algites2gradle.v1:${locPubLibGeneralVersion.get()}")
    api("$locPubLibGeneralGroupId:${locPubLibGeneralArtifactIdPrefix}version.scheme.conversion.algites2pep440.v1:${locPubLibGeneralVersion.get()}")
}

pluginManagement {
    repositories {
        val locUseMavenLocalForResolution =
            providers.gradleProperty("modustro.useMavenLocalForResolution")
                .orElse(providers.environmentVariable("MODUSTRO_USE_MAVEN_LOCAL_FOR_RESOLUTION"))
                .map { it.equals("true", ignoreCase = true) }
                .orElse(false)
                .get()

        gradlePluginPortal()
        if (locUseMavenLocalForResolution) {
            mavenLocal()
        }
        mavenCentral()
        maven {
            name = "algites-public-releases"
            url = uri("https://repo1.maven.org/maven2")
            mavenContent {
                releasesOnly()
            }
        }
        maven {
            name = "algites-public-snapshots"
            url = uri("https://dl.cloudsmith.io/public/algites/java-snapshots-pub/maven/")
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

buildscript {
    repositories {
        mavenCentral()
        maven {
            name = "algites_public_snapshots_bootstrap"
            url = uri("https://dl.cloudsmith.io/public/algites/java-snapshots-pub/maven/")
            mavenContent {
                snapshotsOnly()
            }
        }
    }
    dependencies {
        /*
         * Load the Modustro Builder build-support bundle once at Settings scope. Gradle propagates classes
         * loaded by the settings script to project build scripts, so all Modustro scripts in this build
         * domain observe one shared Builder API/class identity.
         */
        classpath("eu.algites.tool.build:pub.gov.Algites_devops.build.modustrobuild:1.0-SNAPSHOT")
    }
}


dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val locUseMavenLocalForResolution =
            providers.gradleProperty("modustro.useMavenLocalForResolution")
                .orElse(providers.environmentVariable("MODUSTRO_USE_MAVEN_LOCAL_FOR_RESOLUTION"))
                .map { it.equals("true", ignoreCase = true) }
                .orElse(false)
                .get()

        if (locUseMavenLocalForResolution) {
            mavenLocal()
        }
        mavenCentral()
    }
}

val locAlgitesSettingsDiscoveryScript = file("gradle/tool/repository/modustro-root-settings-discovery.gradle.kts")
if (locAlgitesSettingsDiscoveryScript.isFile) {
    apply(from = locAlgitesSettingsDiscoveryScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/modustro-root-settings-discovery.gradle.kts"))
}


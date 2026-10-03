plugins {
    `kotlin-dsl`
    `java-library`
    `maven-publish`
}

group = "eu.algites.pltf.modustro.builder"
version = providers.gradleProperty("modustro.gradleinit.version").orElse("1.0-SNAPSHOT").get()

kotlin {
    jvmToolchain(17)
    sourceSets.main { kotlin.setSrcDirs(listOf("src/product/kotlin")) }
}

sourceSets.test { java.setSrcDirs(listOf("src/develop/java")) }

sourceSets.main {
    resources.srcDir("../../../../../repository/defaults")
    resources.include("algites-repository-download-defaults-public.yml")
}

dependencies {
    api("eu.algites.tool.build:pub.gov.Algites_devops.build.modustrobuild:1.0-SNAPSHOT")
    testImplementation("org.testng:testng:7.11.0")
}

gradlePlugin {
    isAutomatedPublishing = false
    plugins {
        create("modustroSettings") {
            id = "eu.algites.pltf.modustro.builder.settings"
            implementationClass = "eu.algites.pltf.modustro.builder.gradleinit.AIcModustroSettingsPlugin"
        }
        create("modustroRepository") {
            id = "eu.algites.pltf.modustro.builder.repository"
            implementationClass = "eu.algites.pltf.modustro.builder.gradleinit.AIcModustroRepositoryPlugin"
        }
    }
}

publishing {
    publications {
        if (findByName("mavenJava") == null) {
            create<MavenPublication>("mavenJava") { from(components["java"]) }
        }
        withType<MavenPublication>().configureEach {
            artifactId = "pub.gov.Algites_devops.build.modustro.builder.gradleinit"
        }
    }
}

tasks.test { useTestNG() }

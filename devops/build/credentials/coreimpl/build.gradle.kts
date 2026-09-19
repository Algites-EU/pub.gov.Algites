plugins {
    `java-library`
    `maven-publish`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceSets {
        val main by getting {
            java.setSrcDirs(listOf("src/product/java"))
            resources.setSrcDirs(listOf("src/product/resources"))
        }
        val test by getting {
            java.setSrcDirs(listOf("src/develop/java"))
            resources.setSrcDirs(listOf("src/develop/resources"))
        }
    }
}

dependencies {
    api(project(":devops:build:credentials:coreintf"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    runtimeOnly(project(":devops:build:credentials:winstore"))
    runtimeOnly(project(":devops:build:credentials:macstore"))
    runtimeOnly(project(":devops:build:credentials:secretservicestore"))

    testImplementation("org.testng:testng:7.11.0")
}

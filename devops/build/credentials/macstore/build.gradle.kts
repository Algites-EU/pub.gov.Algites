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
    implementation(project(":devops:build:credentials:coreintf"))
    implementation("net.java.dev.jna:jna:5.19.1")
    testImplementation("org.testng:testng:7.11.0")
}

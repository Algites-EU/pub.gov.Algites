plugins {
    application
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
    implementation(project(":devops:build:credentials:coreimpl"))
    testImplementation("org.testng:testng:7.11.0")
}

application {
    mainClass.set("eu.algites.tool.build.credentials.cli.AIcCredentialCli")
}

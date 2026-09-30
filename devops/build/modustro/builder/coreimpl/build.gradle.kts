import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
    `maven-publish`
}

java {
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src/product/java"))
        }
        named("test") {
            java.setSrcDirs(listOf("src/develop/java"))
        }
    }
}

tasks.withType<Test>().configureEach {
    useTestNG()
}

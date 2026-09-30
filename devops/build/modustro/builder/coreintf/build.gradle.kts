plugins {
    `java-library`
    `maven-publish`
}

java {
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src/product/java"))
            resources.setSrcDirs(
                listOf(
                    "src/product/yamldefs",
                    "src/product/jsondefs",
                    "src/product/xmldefs"
                )
            )
        }
    }
}

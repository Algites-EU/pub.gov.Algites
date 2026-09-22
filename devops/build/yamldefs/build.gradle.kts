plugins {
    `java-library`
    `maven-publish`
}

java {
    sourceSets {
        val main by getting {
            resources.setSrcDirs(listOf("src/product/yamldefs"))
        }
    }
}

val locPythonGeneratedSourceDirectory = layout.projectDirectory.dir(
    "src/product/python.gen/algites/pub/gov/algites/devops/build/yamldefs"
)

val generatePythonYamlDefinitionsPackage = tasks.register<Sync>("generatePythonYamlDefinitionsPackage") {
    group = "algites"
    description = "Stages Algites YAML definition schemas as Python package data."

    from(layout.projectDirectory.dir("src/product/yamldefs"))
    into(locPythonGeneratedSourceDirectory)

    doLast {
        locPythonGeneratedSourceDirectory.file("__init__.py").asFile.writeText(
            "\"\"\"Versioned Algites YAML definition schemas.\"\"\"\n",
            Charsets.UTF_8
        )
    }
}

tasks.named("preparePythonBuildProject") {
    dependsOn(generatePythonYamlDefinitionsPackage)
}

tasks.named("buildPython") {
    dependsOn(generatePythonYamlDefinitionsPackage)
}

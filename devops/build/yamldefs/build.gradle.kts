import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

abstract class AIcGeneratePythonYamlDefinitionsPackageTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun AIcGenerate() {
        val locSourceDirectory = sourceDirectory.get().asFile.toPath()
        val locOutputDirectory = outputDirectory.get().asFile.toPath()

        if (Files.exists(locOutputDirectory)) {
            locOutputDirectory.toFile().deleteRecursively()
        }
        Files.createDirectories(locOutputDirectory)

        val locPaths = Files.walk(locSourceDirectory)
        try {
            locPaths.forEach { locSourcePath ->
                val locRelativePath = locSourceDirectory.relativize(locSourcePath)
                val locTargetPath = locOutputDirectory.resolve(locRelativePath)
                if (Files.isDirectory(locSourcePath)) {
                    Files.createDirectories(locTargetPath)
                } else {
                    Files.createDirectories(locTargetPath.parent)
                    Files.copy(locSourcePath, locTargetPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally {
            locPaths.close()
        }

        Files.writeString(
            locOutputDirectory.resolve("__init__.py"),
            "\"\"\"Versioned Algites YAML definition schemas.\"\"\"\n",
            Charsets.UTF_8
        )
    }
}

val locPythonGeneratedSourceDirectory = layout.projectDirectory.dir(
    "src/product/python.gen/algites/pub/gov/algites/devops/build/yamldefs"
)

val generatePythonYamlDefinitionsPackage = tasks.register<AIcGeneratePythonYamlDefinitionsPackageTask>("generatePythonYamlDefinitionsPackage") {
    group = "algites"
    description = "Stages Algites YAML definition schemas as Python package data."
    sourceDirectory.set(layout.projectDirectory.dir("src/product/yamldefs"))
    outputDirectory.set(locPythonGeneratedSourceDirectory)
}

tasks.named("preparePythonBuildProject") {
    dependsOn(generatePythonYamlDefinitionsPackage)
}

tasks.named("buildPython") {
    dependsOn(generatePythonYamlDefinitionsPackage)
}

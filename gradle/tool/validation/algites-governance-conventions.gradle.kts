import java.io.File
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/*
 * Algites governance source-convention checks.
 *
 * These checks intentionally cover only repository-local governance sources.
 * They protect naming/transport conventions that are easy to break by a
 * mechanical rename and are not substitutes for normal compilation/tests.
 */

val AIcGovernanceConventionIgnoredDirectoryNames = setOf(
    ".git", ".gradle", ".idea", ".kotlin", "_obsolete", "run", "build", "target", "out", "output"
)

abstract class AIcCheckAlgitesGovernanceConventionsTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val ignoredDirectoryNames: SetProperty<String>

    @TaskAction
    fun AIcCheck() {
        val locRepositoryDirectory = repositoryDirectory.get().asFile
        val locIgnoredDirectoryNames = ignoredDirectoryNames.get()
        val locProblems = mutableListOf<String>()

        val locDataClassPattern = Regex(
            "(?m)^\\s*(?:(?:private|internal|public|protected)\\s+)?data\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)\\b"
        )
        locRepositoryDirectory.walkTopDown()
            .onEnter { locDirectory ->
                locDirectory == locRepositoryDirectory || locDirectory.name !in locIgnoredDirectoryNames
            }
            .filter { locFile -> locFile.isFile && locFile.extension.lowercase() in setOf("kt", "kts") }
            .forEach { locFile ->
                val locText = locFile.readText(Charsets.UTF_8)
                locDataClassPattern.findAll(locText).forEach { locMatch ->
                    val locName = locMatch.groupValues[1]
                    if (!locName.startsWith("AIcd")) {
                        val locLine = locText.substring(0, locMatch.range.first).count { it == '\n' } + 1
                        val locPath = locRepositoryDirectory.toPath()
                            .relativize(locFile.toPath())
                            .toString()
                            .replace(File.separatorChar, '/')
                        locProblems.add("$locPath:$locLine Kotlin data class '$locName' must use the AIcd prefix.")
                    }
                }
            }

        /*
         * Algites structured-data wire naming.
         *
         * JSON Schema keywords retain their external spelling. Keys below a
         * "properties" object are Algites wire names, except for the explicit
         * symbolic map dimensions listed here. String enum values use the
         * lower_snake_case symbolic-value convention.
         */
        val locUpperCamelPattern = Regex("^[A-Z][A-Za-z0-9]*$")
        val locLowerSnakePattern = Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")
        val locSymbolicPropertyNames = setOf(
            "basic", "bearer", "api_key", "certificate",
            "java", "python", "mps",
            "public", "private",
            "release", "snapshot",
            "download", "upload", "manage"
        )
        val locSchemaDirectory = File(
            locRepositoryDirectory,
            "devops/build/yamldefs/src/product/yamldefs"
        )
        if (locSchemaDirectory.isDirectory) {
            locSchemaDirectory.listFiles()
                ?.filter { locFile -> locFile.isFile && locFile.name.endsWith(".schema.json") }
                ?.sortedBy { locFile -> locFile.name }
                ?.forEach { locFile ->
                    val locRoot = JsonSlurper().parse(locFile)

                    fun AIcVisitJson(aValue: Any?, aPath: String) {
                        when (aValue) {
                            is Map<*, *> -> {
                                val locProperties = aValue["properties"]
                                if (locProperties is Map<*, *>) {
                                    locProperties.keys.filterIsInstance<String>().forEach { locPropertyName ->
                                        if (
                                            !locUpperCamelPattern.matches(locPropertyName) &&
                                            locPropertyName !in locSymbolicPropertyNames
                                        ) {
                                            locProblems.add(
                                                "${locRepositoryDirectory.toPath().relativize(locFile.toPath()).toString().replace(File.separatorChar, '/')}: " +
                                                    "schema property '$locPropertyName' at $aPath.properties must use UpperCamelCase " +
                                                    "or be an explicitly governed symbolic map key."
                                            )
                                        }
                                    }
                                }

                                val locEnum = aValue["enum"]
                                if (locEnum is List<*>) {
                                    locEnum.filterIsInstance<String>().forEach { locEnumValue ->
                                        if (!locLowerSnakePattern.matches(locEnumValue)) {
                                            locProblems.add(
                                                "${locRepositoryDirectory.toPath().relativize(locFile.toPath()).toString().replace(File.separatorChar, '/')}: " +
                                                    "enum value '$locEnumValue' at $aPath.enum must use lower_snake_case."
                                            )
                                        }
                                    }
                                }

                                aValue.forEach { (locKey, locChild) ->
                                    AIcVisitJson(locChild, "$aPath.${locKey ?: "?"}")
                                }
                            }
                            is List<*> -> aValue.forEachIndexed { locIndex, locChild ->
                                AIcVisitJson(locChild, "$aPath[$locIndex]")
                            }
                        }
                    }

                    AIcVisitJson(locRoot, "\$")
                }
        }

        val locTempDefinitionPattern = Regex(
            "(?m)(?:\\b(_TMP_ALGITES_([A-Z0-9_]+))\\s*=|^\\s*(_TMP_ALGITES_([A-Z0-9_]+))\\s*:)"
        )
        val locWorkflowDirectory = File(locRepositoryDirectory, ".github/workflows")
        if (locWorkflowDirectory.isDirectory) {
            locWorkflowDirectory.walkTopDown()
                .filter { locFile -> locFile.isFile && locFile.extension.lowercase() in setOf("yml", "yaml") }
                .forEach { locFile ->
                    val locText = locFile.readText(Charsets.UTF_8)
                    val locDefinitions = locTempDefinitionPattern.findAll(locText)
                        .map { locMatch ->
                            val locFullName = locMatch.groupValues[1].ifBlank { locMatch.groupValues[3] }
                            val locSuffix = locMatch.groupValues[2].ifBlank { locMatch.groupValues[4] }
                            locFullName to locSuffix
                        }
                        .distinct()
                        .toList()

                    locDefinitions.forEach { (locFullName, locSuffix) ->
                        val locStaleReferencePattern = Regex("\\$(?:\\{$locSuffix\\}|$locSuffix\\b)")
                        locStaleReferencePattern.findAll(locText).forEach { locMatch ->
                            val locLine = locText.substring(0, locMatch.range.first).count { it == '\n' } + 1
                            val locPath = locRepositoryDirectory.toPath()
                                .relativize(locFile.toPath())
                                .toString()
                                .replace(File.separatorChar, '/')
                            locProblems.add(
                                "$locPath:$locLine stale pre-prefix reference '${locMatch.value}' found after defining '$locFullName'."
                            )
                        }
                    }
                }
        }

        if (locProblems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Algites governance convention check failed:")
                    locProblems.sorted().forEach { locProblem -> appendLine(" - $locProblem") }
                }.trimEnd()
            )
        }

        println("Algites governance source conventions are consistent.")
    }
}

val checkAlgitesGovernanceConventions = tasks.register<AIcCheckAlgitesGovernanceConventionsTask>(
    "checkAlgitesGovernanceConventions"
) {
    group = "verification"
    description = "Checks Algites governance source conventions, structured-data wire names, and internal _TMP_ALGITES_* transport references."
    repositoryDirectory.set(rootProject.layout.projectDirectory)
    ignoredDirectoryNames.set(AIcGovernanceConventionIgnoredDirectoryNames)
}

tasks.matching { locTask ->
    locTask.name == "algitesBuild" || locTask.name == "build" || locTask.name == "check"
}.configureEach {
    dependsOn(checkAlgitesGovernanceConventions)
}

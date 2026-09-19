import java.io.File

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

fun AIcGovernanceConventionFiles(aExtensions: Set<String>): Sequence<File> =
    rootProject.projectDir.walkTopDown()
        .onEnter { locDirectory ->
            locDirectory == rootProject.projectDir || locDirectory.name !in AIcGovernanceConventionIgnoredDirectoryNames
        }
        .filter { locFile -> locFile.isFile && locFile.extension.lowercase() in aExtensions }

val checkAlgitesGovernanceConventions = tasks.register("checkAlgitesGovernanceConventions") {
    group = "verification"
    description = "Checks Algites governance Kotlin data-class naming and internal _TMP_ALGITES_* transport references."

    doLast {
        val locProblems = mutableListOf<String>()

        val locDataClassPattern = Regex(
            "(?m)^\\s*(?:(?:private|internal|public|protected)\\s+)?data\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)\\b"
        )
        AIcGovernanceConventionFiles(setOf("kt", "kts")).forEach { locFile ->
            val locText = locFile.readText(Charsets.UTF_8)
            locDataClassPattern.findAll(locText).forEach { locMatch ->
                val locName = locMatch.groupValues[1]
                if (!locName.startsWith("AIcd")) {
                    val locLine = locText.substring(0, locMatch.range.first).count { it == '\n' } + 1
                    val locPath = rootProject.projectDir.toPath().relativize(locFile.toPath()).toString().replace(File.separatorChar, '/')
                    locProblems.add("$locPath:$locLine Kotlin data class '$locName' must use the AIcd prefix.")
                }
            }
        }

        val locTempDefinitionPattern = Regex(
            "(?m)(?:\\b(_TMP_ALGITES_([A-Z0-9_]+))\\s*=|^\\s*(_TMP_ALGITES_([A-Z0-9_]+))\\s*:)"
        )
        val locWorkflowDirectory = rootProject.file(".github/workflows")
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
                            val locPath = rootProject.projectDir.toPath().relativize(locFile.toPath()).toString().replace(File.separatorChar, '/')
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

tasks.matching { locTask ->
    locTask.name == "algitesBuild" || locTask.name == "build" || locTask.name == "check"
}.configureEach {
    dependsOn(checkAlgitesGovernanceConventions)
}

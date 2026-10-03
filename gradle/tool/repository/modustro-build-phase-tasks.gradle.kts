/*
 * Modustro Builder repository-wide phase tasks.
 *
 * Each task represents exactly one phase for the current Gradle build domain
 * and every directly included isolated child build. An outer phase controller
 * invokes these tasks in separate Gradle invocations, providing a strict
 * repository-wide barrier before the next phase starts.
 */

import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

fun AIcModustroExistingTasks(aNames: Set<String>): List<Task> =
    allprojects.flatMap { locProject ->
        aNames.mapNotNull { locName -> locProject.tasks.findByName(locName) }
    }.distinct()

fun AIcRegisterModustroPhase(
    aTaskName: String,
    aDescription: String,
    aLocalTaskNames: Set<String>
): TaskProvider<Task> {
    val locPhaseTask = tasks.register(aTaskName) {
        group = "modustro"
        description = aDescription
    }
    gradle.projectsEvaluated {
        locPhaseTask.configure {
            dependsOn(AIcModustroExistingTasks(aLocalTaskNames))
            gradle.includedBuilds.sortedBy { it.name }.forEach { locIncludedBuild ->
                dependsOn(locIncludedBuild.task(":$aTaskName"))
            }
        }
    }
    return locPhaseTask
}

AIcRegisterModustroPhase(
    "modustroResolvePhase",
    "Resolves all mandatory dependency graphs in this build domain and isolated child build domains.",
    setOf("modustroDependencyPreflight")
)

AIcRegisterModustroPhase(
    "modustroPreparePhase",
    "Generates and prepares build inputs in this build domain and isolated child build domains.",
    setOf(
        "generateModustroDefinitionSources",
        "processModustroJavaNativeSources",
        "processModustroPythonNativeSources",
        "preparePythonBuildProject"
    )
)

AIcRegisterModustroPhase(
    "modustroCompilePhase",
    "Compiles product and test sources in this build domain and isolated child build domains without publishing.",
    setOf("classes", "testClasses")
)

AIcRegisterModustroPhase(
    "modustroVerifyPhase",
    "Runs verification tasks in this build domain and isolated child build domains without packaging side effects beyond task requirements.",
    setOf("check")
)

AIcRegisterModustroPhase(
    "modustroPackagePhase",
    "Produces configured local packages in this build domain and isolated child build domains without publishing.",
    setOf("assemble", "buildPython")
)

AIcRegisterModustroPhase(
    "modustroPublishPhase",
    "Publishes configured outputs after all preceding repository-wide phase barriers succeeded.",
    setOf("modustroPublish")
)

/* Direct publishing must preserve the same global package-before-publish invariant as the outer phase controller. */
val locModustroPackagePhase = tasks.named("modustroPackagePhase")
allprojects.forEach { locProject ->
    locProject.tasks.matching { locTask ->
        locTask.name == "modustroPublish" || locTask.name.startsWith("publishModustro")
    }.configureEach {
        dependsOn(locModustroPackagePhase)
    }
}

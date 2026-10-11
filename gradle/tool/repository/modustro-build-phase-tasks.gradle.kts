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

fun AIcModustroExistingTaskProviders(aNames: Set<String>): List<TaskProvider<Task>> =
    allprojects.flatMap { locProject ->
        aNames.filter { locName -> locName in locProject.tasks.names }
            .map { locName -> locProject.tasks.named(locName) }
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
        // Collect providers after registration finishes, without realizing other
        // tasks inside a phase task's configuration action.
        val locLocalPhaseTasks = AIcModustroExistingTaskProviders(aLocalTaskNames)
        val locIncludedPhaseTasks = gradle.includedBuilds.sortedBy { it.name }
            .map { locIncludedBuild -> locIncludedBuild.task(":$aTaskName") }
        locPhaseTask.configure {
            dependsOn(locLocalPhaseTasks)
            dependsOn(locIncludedPhaseTasks)
        }
    }
    return locPhaseTask
}

AIcRegisterModustroPhase(
    "modustroResolvePhase",
    "Resolves all mandatory dependency graphs in this build domain and isolated child build domains.",
    /* Included isolated builds also execute modustroResolvePhase through Gradle
     * composite build task references; no domain may build before all preflights pass. */
    if (System.getenv("MODUSTRO_OPERATION_INTENT") == "publish" ||
        System.getenv("MODUSTRO_PUBLISHING_INTENT") == "true")
        setOf("modustroDependencyPreflight", "validateModustroPublicationProfiles")
    else setOf("modustroDependencyPreflight")
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

/* Package production must not require publication endpoint configuration or secrets.
 * For direct publishing, gate the individual publishing tasks after package
 * production and publication validation; phase-controller publishing still
 * validates every included build domain during the initial resolve phase. */
val locModustroPackagePhase = tasks.named("modustroPackagePhase")
val locModustroPublicationPreflight = rootProject.tasks.named("validateModustroPublicationProfiles")
allprojects.forEach { locProject ->
    locProject.tasks.matching { locTask ->
        locTask.name == "modustroPublish" || locTask.name.startsWith("publishModustro")
    }.configureEach {
        dependsOn(locModustroPackagePhase, locModustroPublicationPreflight)
    }
}

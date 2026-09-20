package eu.algites.tool.build

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Algites build base Gradle policy (public baseline).
 *
 * Naming: AIc = Algites class
 */
// @TODO - class from this one, also Id here and then have static list of all policies applied, where the descendat will register own lopicy, this will verify the policy id
object AIoAlgitesBuildBasePublicPolicy {
    const val VERSION = "@algites.policy.version@";
}

protected data class AIdAlgitesPropertiesFile(
    val file: File,
    val required: Boolean
)

/**
 * Algites build base Gradle convention plugin (public baseline).
 *
 * Naming: AIc = Algites class
 */
class AIcAlgitesBuildBasePublicPlugin : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        // Base plugins for all projects
        pluginManager.apply("base")
        pluginManager.apply("maven-publish")

        // Common layout conventions
        layout.buildDirectory.set(file("run/bld/gradle"))

        // Load Algites versions
        loadAlgitesProperties(this)
        // Register aggregated tasks
        registerAggregatedTasks(this)
        // Register repositories
        registerRepositories(this)
    };

	protected open fun registerRepositories(project: Project) {
        // Common repositories (PUBLIC baseline)
        project.repositories.apply {
            // Releases
			mavenCentral()

            // Snapshots (Cloudsmith public)
			maven {
                name = "algites-public-snapshots"
				url = uri("https://dl.cloudsmith.io/public/algites/maven-snapshots-pub/maven/")
			}
        }
    	}

	protected open fun registerAggregatedTasks(project: Project) {
		aggregateTask(project, "check", "verification")
		aggregateTask(project, "build", "build")
		aggregateTask(project, "publish", "publishing")
	}

	protected open fun registerAggregatedTask(project: Project, taskName: String, group: String) {

		val aggregate = project.tasks.findByName(taskName)
			?: project.tasks.register(taskName) {
				this.group = group
				description = "Aggregated $taskName for ${project.path}"
			}.get()

		project.subprojects.forEach { sub ->
			sub.tasks.matching { it.name == taskName }.configureEach {
				aggregate.dependsOn(this)
			}
		}
	}

	protected open fun algitesPropertiesFiles(project: Project): List<AlgitesPropertiesFile> =
		listOf(
			AlgitesPropertiesFile(
				file = project.rootProject.file("algites-versions.properties"),
				required = true
			),
			AlgitesPropertiesFile(
				file = project.rootProject.file("version.properties"), // legacy
				required = false
			)
		)

	protected open fun loadAlgitesProperties(project: Project) {

		val definitions = algitesPropertiesFiles(project)

		val existing = definitions
			.filter { it.file.exists() && it.file.isFile }

		val missingRequired = definitions
			.filter { it.required }
			.filterNot { it.file.exists() && it.file.isFile }

		val missingOptional = definitions
			.filterNot { it.required }
			.filterNot { it.file.exists() && it.file.isFile }

		// 1) Hard fail if any required properties file is missing
		if (missingRequired.isNotEmpty()) {
			error(
				buildString {
					appendLine("Missing REQUIRED Algites properties files:")
					missingRequired.forEach {
						appendLine("  - ${it.file.relativeTo(project.rootDir)}")
					}
					appendLine()
					appendLine("Optional Algites properties files:")
					definitions.filterNot { it.required }.forEach {
						appendLine("  - ${it.file.relativeTo(project.rootDir)}")
					}
				}
			)
		}

		// 2) Log missing optional properties files
		if (missingOptional.isNotEmpty()) {
			project.logger.info(
				"Optional Algites properties files not found:\n" +
					missingOptional.joinToString("\n") {
						"  - ${it.file.relativeTo(project.rootDir)}"
					}
			)
		}

		// 3) If no properties file exists at all (and none is required), do nothing
		if (existing.isEmpty()) {
			project.logger.info(
				"No Algites properties files found. Defined files are:\n" +
					definitions.joinToString("\n") {
						val kind = if (it.required) "REQUIRED" else "optional"
						"  - ${it.file.relativeTo(project.rootDir)} ($kind)"
					}
			)
			return
		}

		// 4) Load and merge properties (later files override earlier ones)
		val merged = Properties()

		existing.forEach { def ->
			project.logger.info(
				"Loading Algites properties from ${def.file.relativeTo(project.rootDir)}"
			)
			def.file.inputStream().use { merged.load(it) }
		}

		// 5) Expose properties via Gradle extra properties
		merged.forEach { (k, v) ->
			project.extensions.extraProperties.set(
				k.toString(),
				v.toString()
			)
		}
	}
}

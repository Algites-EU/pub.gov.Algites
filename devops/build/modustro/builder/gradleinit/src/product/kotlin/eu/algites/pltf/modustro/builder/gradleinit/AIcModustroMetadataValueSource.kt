package eu.algites.pltf.modustro.builder.gradleinit

import groovy.json.JsonOutput
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/** Rechecks effective metadata without making generated directory listings configuration-cache inputs. */
abstract class AIcModustroMetadataValueSource : ValueSource<String, AIcModustroMetadataValueSource.AIiParameters> {
    interface AIiParameters : ValueSourceParameters {
        val repositoryDirectory: DirectoryProperty
        val artifactDirectoryPath: Property<String>
        val resolutionKind: Property<String>
        val repositoryName: Property<String>
        val repositoryVisibility: Property<String>
    }

    override fun obtain(): String = JsonOutput.toJson(AIcToMap(AIcResolveModustroArtifactDirectoryMetadata(
        parameters.repositoryDirectory.get().asFile,
        parameters.artifactDirectoryPath.get(), parameters.resolutionKind.get(),
        parameters.repositoryName.get().takeIf { it.isNotBlank() },
        parameters.repositoryVisibility.get().takeIf { it.isNotBlank() }
    )))
}

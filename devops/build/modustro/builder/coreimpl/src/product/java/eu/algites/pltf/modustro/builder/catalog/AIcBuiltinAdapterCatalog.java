package eu.algites.pltf.modustro.builder.catalog;

import eu.algites.pltf.modustro.builder.publication.AIcBuildRecordPublicationFinalizationActionAdapter;
import eu.algites.pltf.modustro.builder.publication.AIcRemoveCorrespondingSnapshotsVersionScopePublicationFinalizationActionAdapter;
import eu.algites.pltf.modustro.builder.publication.AIcRefreshDocsSiteVersionScopePublicationFinalizationActionAdapter;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcGitBranchPublicationAdapter;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcHttpDirectoryPublicationAdapter;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcLocalCopyPublicationAdapter;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcMavenLocalRepositoryPublicationAdapter;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcMavenRepositoryPublicationAdapter;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcPythonRepositoryPublicationAdapter;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcS3ObjectStoragePublicationAdapter;
import eu.algites.pltf.modustro.builder.subscription.adapters.AIcBuiltinSubscriptionAdapter;
import java.util.List;

/** Creates the built-in bootstrap adapter catalog until external plugin discovery replaces explicit registration. */
public final class AIcBuiltinAdapterCatalog {
    private AIcBuiltinAdapterCatalog() { }

    public static AIcAdapterCatalog create() {
        return new AIcAdapterCatalog(
                List.of(
                        new AIcBuiltinSubscriptionAdapter("maven-repository"),
                        new AIcBuiltinSubscriptionAdapter("python-repository"),
                        new AIcBuiltinSubscriptionAdapter("mps-repository")),
                List.of(
                        new AIcGitBranchPublicationAdapter(),
                        new AIcHttpDirectoryPublicationAdapter(),
                        new AIcLocalCopyPublicationAdapter(),
                        new AIcMavenLocalRepositoryPublicationAdapter(),
                        new AIcMavenRepositoryPublicationAdapter(),
                        new AIcPythonRepositoryPublicationAdapter(),
                        new AIcS3ObjectStoragePublicationAdapter()),
                List.of(new AIcBuildRecordPublicationFinalizationActionAdapter()),
                List.of(),
                List.of(),
                List.of(new AIcRemoveCorrespondingSnapshotsVersionScopePublicationFinalizationActionAdapter(),
                        new AIcRefreshDocsSiteVersionScopePublicationFinalizationActionAdapter()));
    }
}

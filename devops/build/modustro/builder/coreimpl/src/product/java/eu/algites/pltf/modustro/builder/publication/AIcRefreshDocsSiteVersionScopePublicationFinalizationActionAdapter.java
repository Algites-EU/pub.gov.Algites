package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationFinalizationActionResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Requests one repository documentation refresh after a complete Version Scope publication. */
public final class AIcRefreshDocsSiteVersionScopePublicationFinalizationActionAdapter
        implements AIiVersionScopePublicationFinalizationActionAdapter {
    public static final String ADAPTER_ID = "modustro-refresh-docs-site";

    @Override public String adapterId() { return ADAPTER_ID; }
    @Override public boolean isRetrySafe(AIcVersionScopePublicationFinalizationActionAttemptContext aContext) { return true; }

    @Override public AIcPublicationFinalizationActionResult execute(AIcVersionScopePublicationFinalizationActionAttemptContext aContext) {
        if (aContext.cancellationToken().isCancellationRequested()) throw new CancellationException("Documentation refresh request was cancelled.");
        aContext.progressReporter().completed("Repository documentation refresh requested.");
        return new AIcPublicationFinalizationActionResult(aContext.action().id(), true, true, false, 1,
                Duration.ZERO, null, Map.of("RepositoryPublicationRefreshRequests", List.of("MODUSTRO_DOCS_SITE")), null);
    }
}

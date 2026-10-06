package eu.algites.pltf.modustro.builder.model.publication;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Result of one publication-finalization action after scheduler retries. */
public record AIcPublicationFinalizationActionResult(
        String actionId,
        boolean started,
        boolean success,
        boolean ignoredFailure,
        int attempts,
        Duration duration,
        URI outputUri,
        Map<String, Object> metadata,
        Throwable failure) {
    public AIcPublicationFinalizationActionResult {
        metadata = AIcPublicationValues.freeze(metadata);
    }
}

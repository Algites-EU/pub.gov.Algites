package eu.algites.pltf.modustro.builder.model.publication;

import java.time.Duration;

/** Final observable result of one endpoint schedule. */
public record AIcPublishingEndpointResult(
        String endpointId,
        boolean started,
        boolean success,
        boolean ignoredFailure,
        int attempts,
        Duration duration,
        Throwable failure) {
}

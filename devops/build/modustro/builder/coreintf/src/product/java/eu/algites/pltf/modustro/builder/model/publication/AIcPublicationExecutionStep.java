package eu.algites.pltf.modustro.builder.model.publication;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** Immutable observable execution step retained in a post-publication lineage. */
public record AIcPublicationExecutionStep(
        String id,
        String kind,
        URI inputUri,
        URI outputUri,
        Map<String, Object> configuration,
        Map<String, Object> resultMetadata) {
    public AIcPublicationExecutionStep {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        configuration = Map.copyOf(configuration == null ? Map.of() : configuration);
        resultMetadata = Map.copyOf(resultMetadata == null ? Map.of() : resultMetadata);
    }
}

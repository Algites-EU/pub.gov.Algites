package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Gradle-independent payload to publish to one or more endpoints. */
public record AIcPublicationPayload(
        AInPublicationOutputKind outputKind,
        AInPublicationStability stability,
        String artifactIdentity,
        String version,
        List<AIcPublicationPayloadFile> files,
        Map<String, String> coordinates) {

    /** Normalizes payload collections and validates required identity. */
    public AIcPublicationPayload {
        Objects.requireNonNull(outputKind, "outputKind");
        Objects.requireNonNull(stability, "stability");
        Objects.requireNonNull(artifactIdentity, "artifactIdentity");
        files = List.copyOf(files == null ? List.of() : files);
        coordinates = Map.copyOf(coordinates == null ? Map.of() : coordinates);
    }
}

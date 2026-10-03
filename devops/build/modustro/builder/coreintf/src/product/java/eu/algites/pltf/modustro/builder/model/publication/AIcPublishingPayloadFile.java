package eu.algites.pltf.modustro.builder.model.publication;

import java.nio.file.Path;
import java.util.Objects;

/** One concrete file carried by a publishing payload. */
public record AIcPublishingPayloadFile(Path path, String logicalName) {
    /** Validates payload file identity. */
    public AIcPublishingPayloadFile {
        Objects.requireNonNull(path, "path");
        logicalName = logicalName == null || logicalName.isBlank() ? path.getFileName().toString() : logicalName;
    }
}

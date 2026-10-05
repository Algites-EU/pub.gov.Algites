package eu.algites.pltf.modustro.builder.model.publication;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/** One content item carried by a publication payload. The canonical content locator is a URI. */
public record AIcPublicationPayloadFile(URI contentUri, String logicalName) {
    /** Validates content identity. */
    public AIcPublicationPayloadFile {
        Objects.requireNonNull(contentUri, "contentUri");
        if (!contentUri.isAbsolute()) {
            throw new IllegalArgumentException("Publication content URI must be absolute.");
        }
        if (logicalName == null || logicalName.isBlank()) {
            String locPath = contentUri.getPath();
            int locSlash = locPath == null ? -1 : locPath.lastIndexOf('/');
            logicalName = locPath == null || locPath.isBlank() ? "content" : locPath.substring(locSlash + 1);
        }
    }

    /** Convenience constructor for materialized local content. */
    public AIcPublicationPayloadFile(Path aPath, String aLogicalName) {
        this(Objects.requireNonNull(aPath, "path").toAbsolutePath().normalize().toUri(), aLogicalName);
    }

    /** Returns the local path when the URI is file-backed. */
    public Path path() {
        if (!"file".equalsIgnoreCase(contentUri.getScheme())) {
            throw new IllegalStateException("Publication content is not locally file-backed: " + contentUri);
        }
        return Path.of(contentUri);
    }
}
